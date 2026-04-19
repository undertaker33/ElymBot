package com.astrbot.android.data

import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.ConversationAggregateWriteModel
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationRepositoryTest {
    private lateinit var snapshot: List<ConversationSession>
    private lateinit var originalConversationDao: ConversationAggregateDao

    @Before
    fun captureSnapshot() {
        snapshot = ConversationRepository.snapshotSessions()
        originalConversationDao = conversationAggregateDao()
    }

    @After
    fun restoreSnapshot() {
        setConversationDao(originalConversationDao)
        ConversationRepository.restoreSessions(snapshot)
    }

    @Test
    fun isolated_group_sessions_from_different_users_are_not_treated_as_duplicates() {
        val existing = testSession(
            id = "qq-qq-main-group-20001-user-10001",
            personaId = "persona-a",
        )
        val incoming = testSession(
            id = "qq-qq-main-group-20001-user-10002",
            personaId = "persona-b",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))
        val result = ConversationRepository.importSessions(
            importedSessions = listOf(incoming),
            overwriteDuplicates = true,
        )
        val sessions = ConversationRepository.snapshotSessions()

        assertTrue(preview.duplicateSessions.isEmpty())
        assertEquals(1, preview.newSessions.size)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.overwrittenCount)
        assertEquals(
            setOf("persona-a", "persona-b"),
            sessions
                .filter { it.id.startsWith("qq-qq-main-group-20001-user-") }
                .map { it.personaId }
                .toSet(),
        )
    }

    @Test
    fun shared_group_sessions_still_deduplicate_to_a_single_persona_slot() {
        val existing = testSession(
            id = "qq-qq-main-group-20001",
            personaId = "persona-a",
        )
        val incoming = testSession(
            id = "qq-qq-main-group-20001",
            personaId = "persona-b",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))
        val result = ConversationRepository.importSessions(
            importedSessions = listOf(incoming),
            overwriteDuplicates = false,
        )
        val sessions = ConversationRepository.snapshotSessions()

        assertEquals(1, preview.duplicateSessions.size)
        assertTrue(preview.newSessions.isEmpty())
        assertEquals(0, result.importedCount)
        assertEquals(0, result.overwrittenCount)
        assertEquals(1, result.skippedCount)
        assertEquals(
            listOf("persona-a"),
            sessions
                .filter { it.id == "qq-qq-main-group-20001" }
                .map { it.personaId },
        )
    }

    @Test
    fun explicit_session_identity_drives_qq_dedup_even_when_ids_change() {
        val existing = testSession(
            id = "local-a",
            personaId = "persona-a",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
        )
        val incoming = testSession(
            id = "local-b",
            personaId = "persona-b",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))

        assertEquals(1, preview.duplicateSessions.size)
        assertTrue(preview.newSessions.isEmpty())
    }

    @Test
    fun pinned_sessions_are_sorted_ahead_of_recent_unpinned_sessions() {
        val unpinnedRecent = testSession(
            id = "recent",
            personaId = "",
            pinned = false,
            latestTimestamp = 300L,
        )
        val pinnedOlder = testSession(
            id = "pinned",
            personaId = "",
            pinned = true,
            latestTimestamp = 100L,
        )

        ConversationRepository.restoreSessions(listOf(unpinnedRecent, pinnedOlder))

        val sessions = ConversationRepository.snapshotSessions()

        assertEquals(listOf("pinned", "recent"), sessions.map { it.id })
    }

    @Test
    fun sync_system_session_title_does_not_override_a_user_renamed_private_session() {
        val sessionId = "qq-qq-main-private-10001"
        ConversationRepository.restoreSessions(
            listOf(
                testSession(
                    id = sessionId,
                    personaId = "",
                    title = "QQP 小明",
                ),
            ),
        )

        ConversationRepository.renameSession(sessionId, "和小明的私聊")
        ConversationRepository.syncSystemSessionTitle(sessionId, "QQP 新昵称")

        val updated = ConversationRepository.session(sessionId)
        assertEquals("和小明的私聊", updated.title)
        assertTrue(updated.titleCustomized)
    }

    /**
     * Task10 Phase3 – Task D: restoreSessionsDurable must update the in-memory snapshot
     * synchronously (no Room DB available in unit tests, but the in-memory state is still set).
     */
    @Test
    fun restoreSessionsDurable_updates_in_memory_sessions() = runBlocking {
        val session = testSession(id = "session-durable", personaId = "persona-durable")

        ConversationRepository.restoreSessionsDurable(listOf(session))

        val sessions = ConversationRepository.snapshotSessions()
        assertEquals(1, sessions.size)
        assertEquals("session-durable", sessions.single().id)
    }

    /**
     * Task10 Phase3 – Task D: restoreSessionsDurable applies the same dedup logic as
     * restoreSessions (distinct IDs only).
     */
    @Test
    fun restoreSessionsDurable_deduplicates_sessions_by_id() = runBlocking {
        val dup = testSession(id = "dup", personaId = "p1")

        ConversationRepository.restoreSessionsDurable(listOf(dup, dup))

        val sessions = ConversationRepository.snapshotSessions()
        assertEquals(1, sessions.count { it.id == "dup" })
    }

    /**
     * Task10 Phase3 – Task D (import path): importSessionsDurable merges sessions and updates
     * in-memory state durably, applying the same dedup-key logic as importSessions.
     */
    @Test
    fun importSessionsDurable_merges_new_sessions_into_existing() = runBlocking {
        val existing = testSession(id = "existing-session", personaId = "p1")
        val incoming = testSession(id = "new-session", personaId = "p2")
        ConversationRepository.restoreSessions(listOf(existing))

        val result = ConversationRepository.importSessionsDurable(
            importedSessions = listOf(incoming),
            overwriteDuplicates = false,
        )

        val sessions = ConversationRepository.snapshotSessions()
        assertEquals(1, result.importedCount)
        assertEquals(0, result.overwrittenCount)
        assertTrue(sessions.any { it.id == "existing-session" })
        assertTrue(sessions.any { it.id == "new-session" })
    }

    /**
     * Task10 Phase3 – Task D (import path): importSessionsDurable deduplicates incoming sessions
     * by ID before merging, matching the behavior of importSessions.
     */
    @Test
    fun importSessionsDurable_deduplicates_incoming_sessions_by_id() = runBlocking {
        val dup = testSession(id = "dup-import", personaId = "p3")

        val result = ConversationRepository.importSessionsDurable(
            importedSessions = listOf(dup, dup),
            overwriteDuplicates = false,
        )

        val sessions = ConversationRepository.snapshotSessions()
        assertEquals(1, sessions.count { it.id == "dup-import" })
        assertEquals(1, result.importedCount)
    }

    @Test
    fun restoreSessionsDurable_rethrows_and_rolls_back_when_persistence_fails() = runBlocking {
        val before = listOf(testSession(id = "existing-before-restore", personaId = "p0"))
        ConversationRepository.restoreSessions(before)
        setConversationDao(ThrowingConversationAggregateDao())

        var failed = false
        try {
            ConversationRepository.restoreSessionsDurable(
                listOf(testSession(id = "incoming-after-failure", personaId = "p1")),
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue("restoreSessionsDurable must propagate Room persistence failures", failed)
        assertEquals(
            "restoreSessionsDurable must roll back in-memory sessions on persistence failure",
            before,
            ConversationRepository.snapshotSessions(),
        )
    }

    @Test
    fun importSessionsDurable_rethrows_and_rolls_back_when_persistence_fails() = runBlocking {
        val before = listOf(testSession(id = "existing-before-import", personaId = "p0"))
        ConversationRepository.restoreSessions(before)
        setConversationDao(ThrowingConversationAggregateDao())

        var failed = false
        try {
            ConversationRepository.importSessionsDurable(
                importedSessions = listOf(testSession(id = "incoming-import", personaId = "p1")),
                overwriteDuplicates = false,
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue("importSessionsDurable must propagate Room persistence failures", failed)
        assertEquals(
            "importSessionsDurable must roll back in-memory sessions on persistence failure",
            before,
            ConversationRepository.snapshotSessions(),
        )
    }

    private fun conversationAggregateDao(): ConversationAggregateDao {
        val field = ConversationRepository::class.java.getDeclaredField("conversationAggregateDao")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(ConversationRepository) as ConversationAggregateDao
    }

    private fun setConversationDao(dao: ConversationAggregateDao) {
        val field = ConversationRepository::class.java.getDeclaredField("conversationAggregateDao")
        field.isAccessible = true
        field.set(ConversationRepository, dao)
    }

    private fun testSession(
        id: String,
        personaId: String,
        title: String = id,
        pinned: Boolean = false,
        latestTimestamp: Long = 0L,
        platformId: String = "app",
        messageType: MessageType = MessageType.OtherMessage,
        originSessionId: String = id,
    ): ConversationSession {
        return ConversationSession(
            id = id,
            title = title,
            botId = "qq-main",
            personaId = personaId,
            providerId = "",
            platformId = platformId,
            messageType = messageType,
            originSessionId = originSessionId,
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = pinned,
            titleCustomized = false,
            messages = if (latestTimestamp > 0L) {
                listOf(
                    com.astrbot.android.model.chat.ConversationMessage(
                        id = "$id-message",
                        role = "assistant",
                        content = "seed",
                        timestamp = latestTimestamp,
                    ),
                )
            } else {
                emptyList()
            },
        )
    }
}

private class ThrowingConversationAggregateDao : ConversationAggregateDao() {
    override fun observeConversationAggregates() = flowOf(emptyList<ConversationAggregate>())

    override suspend fun listConversationAggregates(): List<ConversationAggregate> = emptyList()

    override suspend fun upsertSessions(entities: List<ConversationEntity>) = Unit

    override suspend fun upsertMessages(entities: List<ConversationMessageEntity>) = Unit

    override suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>) = Unit

    override suspend fun deleteMissingSessions(ids: List<String>) = Unit

    override suspend fun clearSessions() = Unit

    override suspend fun deleteMessagesForSessions(sessionIds: List<String>) = Unit

    override suspend fun count(): Int = 0

    override suspend fun replaceAll(writeModels: List<ConversationAggregateWriteModel>) {
        throw IllegalStateException("forced durable conversation persistence failure")
    }
}
