package com.elymbot.android.session

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.CheckpointEntity
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.MessageEntity
import com.elymbot.android.data.PersonaEntity
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.ReplyDeliveryStatus
import com.elymbot.android.data.ReplyDeliveryTargetKind
import com.elymbot.android.data.RoomHistoryStore
import com.elymbot.android.data.TurnEntity
import com.elymbot.android.ui.ChatDeliveryStatus
import com.elymbot.android.ui.UiIssue
import com.elymbot.android.ui.UiServiceException
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentRuntime
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryMessageStatus
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryRole
import com.elymbot.core.contract.HistorySummaryCandidate
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ReplyMessage
import com.elymbot.core.contract.ReplySubmission
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.SummaryCommitResult
import com.elymbot.core.contract.ToolInstallationIdentity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReplySubmitterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun submissionsAreAtomicIdempotentAndHistoryWaitsForTheGate() = runBlocking {
        val database = newDatabase()
        try {
            val turn = seedRunningTurn(database, sequence = 1)
            val submitter = RoomReplySubmitter(database, externalTargets("origin", "mirror"))
            val submission = submission(turn, callOrdinal = 0, messages = listOf("first", "second"))

            val accepted = submitter.submit(submission) as ReplySubmissionResult.Accepted
            assertEquals(2, accepted.messageIds.size)
            val secondAcceptance = submitter.submit(submission) as ReplySubmissionResult.Accepted
            assertEquals(accepted.messageIds, secondAcceptance.messageIds)
            assertEquals(
                accepted.messageIds,
                database.replySubmissionDao().forCall(
                    turn.sessionId,
                    turn.executionId,
                    turn.attemptId,
                    turn.sequence,
                    0,
                    0,
                ).map { it.messageId },
            )
            assertEquals(3, database.messageDao().forTurn(turn.sessionId, turn.sequence).size)
            val deliveries = accepted.messageIds.flatMap { messageId -> database.replyDeliveryDao().forMessage(messageId) }
            assertEquals(4, deliveries.size)
            assertTrue(deliveries.all { it.status == ReplyDeliveryStatus.PENDING.name })
            assertEquals(2, deliveries.count { it.historyVisibilityGate })

            val sameIdentityDifferentPayload = submitter.submit(
                submission.copy(messages = listOf(ReplyMessage("different", 0), ReplyMessage("second", 0))),
            )
            assertEquals(
                ReplySubmissionResult.Rejected(com.elymbot.core.contract.ReplyRejectionCode.INVALID_REQUEST),
                sameIdentityDifferentPayload,
            )

            val history = RoomHistoryStore(database)
            val beforeDelivery = history.read(historyRequest(turn.sessionId))
            assertEquals(listOf(HistoryRole.USER), beforeDelivery.records.map { it.role })
            assertTrue(database.replySubmissionDao().hasIneligibleHistoryMessages(turn.sessionId, 1, 1))

            val firstMessageDeliveries = database.replyDeliveryDao().forMessage(accepted.messageIds.first())
            val gate = firstMessageDeliveries.single { it.targetKey == "origin" }
            val mirror = firstMessageDeliveries.single { it.targetKey == "mirror" }
            resolve(database, gate.id, ReplyDeliveryStatus.FAILED)
            resolve(database, mirror.id, ReplyDeliveryStatus.DELIVERED)
            assertEquals(1, database.replyDeliveryDao().retryConfirmedFailure(accepted.messageIds.first(), gate.id, 50L))
            assertEquals(0, database.replyDeliveryDao().retryConfirmedFailure(accepted.messageIds.first(), mirror.id, 50L))
            assertTrue(database.replySubmissionDao().hasIneligibleHistoryMessages(turn.sessionId, 1, 1))

            resolve(database, gate.id, ReplyDeliveryStatus.DELIVERED)
            val secondGate = database.replyDeliveryDao().forMessage(accepted.messageIds.last())
                .single { it.targetKey == "origin" }
            resolve(database, secondGate.id, ReplyDeliveryStatus.DELIVERED)
            database.turnDao().update(turn.copy(status = "FAILED", finishedAt = 100L, failureKind = "MODEL"))
            val afterDelivery = history.read(historyRequest(turn.sessionId))
            val assistantRows = afterDelivery.records.filter { it.role == HistoryRole.ASSISTANT }
            assertEquals(2, assistantRows.size)
            assertTrue(assistantRows.all { it.status == HistoryMessageStatus.COMPLETE })
            assertFalse(afterDelivery.records.first { it.role == HistoryRole.USER }.successfulSilence)

            database.turnDao().update(turn.copy(status = "RUNNING", finishedAt = null, failureKind = null))
            val pendingCall = submitter.submit(submission(turn, callOrdinal = 1, messages = listOf("canceled reply")))
                as ReplySubmissionResult.Accepted
            assertTrue(database.replySubmissionDao().hasIneligibleHistoryMessages(turn.sessionId, 1, 1))
            database.replyDeliveryDao().cancelPendingForAttempt(turn.sessionId, turn.executionId, turn.attemptId, 70L)
            assertFalse(database.replySubmissionDao().hasIneligibleHistoryMessages(turn.sessionId, 1, 1))
            database.turnDao().update(turn.copy(status = "COMPLETED", finishedAt = 100L, failureKind = null))
            val summary = RoomHistoryStore(database).commit(
                HistorySummaryCandidate(turn.sessionId, 1, 1, "delivered reply summary"),
            )
            assertTrue(summary is SummaryCommitResult.Committed)
            assertEquals(
                mapOf(
                    "origin" to ReplyDeliveryStatus.CANCELLED.name,
                    "mirror" to ReplyDeliveryStatus.CANCELLED.name,
                ),
                database.replyDeliveryDao().forMessage(pendingCall.messageIds.single())
                    .associate { it.targetKey to it.status },
            )

            val silentTurn = insertRunningTurn(database, turn.sessionId, sequence = 2)
            database.turnDao().update(
                silentTurn.copy(status = "COMPLETED", finishedAt = 101L, successfulSilence = true),
            )
            val silentHistory = history.read(historyRequest(turn.sessionId))
            val silentUser = silentHistory.records.single { it.turnSequence == 2L }
            assertEquals(HistoryRole.USER, silentUser.role)
            assertTrue(silentUser.successfulSilence)
            assertFalse(silentHistory.records.any { it.turnSequence == 2L && it.role == HistoryRole.ASSISTANT })
        } finally {
            database.close()
        }
    }

    @Test
    fun reopenedRepositoryRestoresValidPendingAndLeavesFinishedDeliveriesOutOfTheQueue() = runBlocking {
        val databaseName = "reply-restart-${UUID.randomUUID()}.db"
        var database: ElymBotDatabase? = null
        try {
            val original = Room.databaseBuilder(context, ElymBotDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            database = original
            val turn = seedRunningTurn(original, sequence = 1)
            val accepted = RoomReplySubmitter(original, externalTargets("pending", "in-flight")).submit(
                submission(turn, 0, listOf("survive process death")),
            ) as ReplySubmissionResult.Accepted
            val deliveries = original.replyDeliveryDao().forMessage(accepted.messageIds.single())
            val inFlight = deliveries.single { it.targetKey == "in-flight" }
            assertEquals(1, original.replyDeliveryDao().markSending(inFlight.id, 80L))

            val parallelSession = requireNotNull(original.conversationDao().get(turn.sessionId)).copy(
                id = "parallel-${UUID.randomUUID()}",
                currentCheckpointId = "parallel-checkpoint",
            )
            original.conversationDao().insert(parallelSession)
            val finished = insertRunningTurn(original, parallelSession.id, sequence = 1)
            val finishedReply = RoomReplySubmitter(original, externalTargets("waiting", "already-sent")).submit(
                submission(finished, 0, listOf("finish before the process reopens")),
            ) as ReplySubmissionResult.Accepted
            val finishedDeliveries = original.replyDeliveryDao().forMessage(finishedReply.messageIds.single())
            val waiting = finishedDeliveries.single { it.targetKey == "waiting" }
            val alreadySent = finishedDeliveries.single { it.targetKey == "already-sent" }
            resolve(original, alreadySent.id, ReplyDeliveryStatus.DELIVERED)
            original.turnDao().update(finished.copy(status = "COMPLETED", finishedAt = System.currentTimeMillis()))

            original.close()
            database = null

            val reopened = Room.databaseBuilder(context, ElymBotDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            database = reopened
            repository(reopened).initialize()

            assertEquals("INTERRUPTED", reopened.turnDao().get(turn.sessionId, turn.sequence)?.status)
            val recovered = reopened.replyDeliveryDao().forMessage(accepted.messageIds.single())
            assertEquals(
                mapOf(
                    "pending" to ReplyDeliveryStatus.CANCELLED.name,
                    "in-flight" to ReplyDeliveryStatus.UNKNOWN.name,
                ),
                recovered.associate { it.targetKey to it.status },
            )
            assertEquals(listOf(waiting.id), reopened.replyDeliveryDao().pendingPlatformSnapshot().map { it.delivery.id })
            assertEquals(
                ReplyDeliveryStatus.DELIVERED.name,
                reopened.replyDeliveryDao().forMessage(finishedReply.messageIds.single())
                    .single { it.id == alreadySent.id }.status,
            )
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun failedPersistenceDoesNotLeavePartialReplyMessages() = runBlocking {
        val database = newDatabase()
        try {
            val turn = seedRunningTurn(database, sequence = 1)
            database.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER fail_second_reply_target BEFORE INSERT ON reply_deliveries
                    WHEN (SELECT COUNT(*) FROM reply_submissions WHERE toolCallId = 'call-0') > 0
                    BEGIN SELECT RAISE(ABORT, 'injected target failure'); END""".trimIndent(),
            )
            val result = RoomReplySubmitter(database, externalTargets("origin")).submit(
                submission(turn, callOrdinal = 0, messages = listOf("first", "second")),
            )

            assertEquals(ReplySubmissionResult.Failed(com.elymbot.core.contract.ReplyFailureCode.PERSISTENCE), result)
            assertEquals(1, database.messageDao().forTurn(turn.sessionId, turn.sequence).size)
            assertTrue(database.replySubmissionDao().forCall(
                turn.sessionId,
                turn.executionId,
                turn.attemptId,
                turn.sequence,
                0,
                0,
            ).isEmpty())
            assertTrue(database.replyDeliveryDao().observePending().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun appTranscriptReplyRemainsHiddenUntilDueReleaseAndThenBlocksRetry() = runBlocking {
        val database = newDatabase()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val turn = seedRunningTurn(database, sequence = 1)
            val submitter = RoomReplySubmitter(database, appTranscriptTarget())
            val submission = submission(turn, 0, listOf("delayed App reply")).copy(
                messages = listOf(ReplyMessage("delayed App reply", suggestedDelayMillis = 30_000L)),
            )
            val accepted = submitter.submit(submission) as ReplySubmissionResult.Accepted
            val delivery = database.replyDeliveryDao().forMessage(accepted.messageIds.single()).single()
            assertEquals(ReplyDeliveryStatus.PENDING.name, delivery.status)
            database.turnDao().update(
                turn.copy(status = "FAILED", finishedAt = 100L, failureKind = "MODEL", retryable = true),
            )

            val history = RoomHistoryStore(database)
            assertEquals(listOf(HistoryRole.USER), history.read(historyRequest(turn.sessionId)).records.map { it.role })
            assertEquals(1, database.messageDao().observeForSession(turn.sessionId).first().size)
            val repository = repository(database)
            val service = service(repository, scope)
            val beforeRelease = service.conversation(turn.sessionId).first()
            assertFalse(beforeRelease.messages.any { it.text == "delayed App reply" })
            assertEquals(ReplyDeliveryStatus.PENDING.name, beforeRelease.pendingDeliveries.single().status.name.uppercase())
            assertTrue(beforeRelease.canRetry)

            val duplicate = submitter.submit(submission) as ReplySubmissionResult.Accepted
            assertEquals(accepted.messageIds, duplicate.messageIds)
            assertEquals(2, database.messageDao().forTurn(turn.sessionId, turn.sequence).size)

            assertEquals(1, database.replyDeliveryDao().releaseDueAppTranscript(delivery.id, System.currentTimeMillis() + 60_000L))
            val afterRelease = history.read(historyRequest(turn.sessionId))
            assertEquals(listOf(HistoryRole.USER, HistoryRole.ASSISTANT), afterRelease.records.map { it.role })
            assertEquals(HistoryMessageStatus.COMPLETE, afterRelease.records.last().status)
            assertTrue(database.replyDeliveryDao().hasVisibleOrPossibleEffectInTurn(turn.sessionId, turn.sequence))
            val visible = service.conversation(turn.sessionId).first()
            assertTrue(visible.messages.any { it.text == "delayed App reply" })
            assertFalse(visible.canRetry)
            val retryFailure = runCatching { service.retry(turn.sessionId) }.exceptionOrNull()
            assertTrue(retryFailure is UiServiceException)
            assertEquals(UiIssue.InvalidInput, (retryFailure as UiServiceException).issue)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun stopWithoutActiveExecutionCancelsPendingAppReleaseAndKeepsItOutOfHistory() = runBlocking {
        val database = newDatabase()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val turn = seedRunningTurn(database, sequence = 1)
            val submission = submission(turn, 0, listOf("must not be released")).copy(
                messages = listOf(ReplyMessage("must not be released", suggestedDelayMillis = 30_000L)),
            )
            val accepted = RoomReplySubmitter(database, appTranscriptTarget()).submit(submission)
                as ReplySubmissionResult.Accepted
            database.turnDao().update(
                turn.copy(status = "COMPLETED", finishedAt = 100L, retryable = true),
            )
            val service = service(repository(database), scope)

            service.stop(turn.sessionId)

            assertEquals(1, database.replyDeliveryDao().forMessage(accepted.messageIds.single())
                .count { it.status == ReplyDeliveryStatus.CANCELLED.name })
            assertEquals(listOf(HistoryRole.USER), RoomHistoryStore(database).read(historyRequest(turn.sessionId)).records.map { it.role })
            val conversation = service.conversation(turn.sessionId).first()
            assertFalse(conversation.messages.any { it.text == "must not be released" })
            assertEquals(ChatDeliveryStatus.Cancelled, conversation.pendingDeliveries.single().status)
            assertTrue(conversation.canRetry)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun checkpointRollbackRetiresExecutionAndDetachesOnlyExternalEffects() = runBlocking {
        val database = newDatabase()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(database)
            repository.database.personaDao().insert(testPersona())
            repository.initialize()
            val session = database.conversationDao().getMain()!!
            insertCompletedTurn(database, session, sequence = 1)
            val service = service(repository, scope)
            service.saveCheckpoint(session.id)
            val checkpoint = service.checkpoints(session.id).first().single { it.displayNumber == 2 }

            val removedTurn = insertRunningTurn(database, session.id, sequence = 2)
            val submission = submission(removedTurn, callOrdinal = 0, messages = listOf("private reply"))
            val submitter = RoomReplySubmitter(database, externalTargets("delivered", "unknown", "pending", "failed"))
            val accepted = submitter.submit(submission) as ReplySubmissionResult.Accepted
            val targets = database.replyDeliveryDao().forMessage(accepted.messageIds.single())
            resolve(database, targets.single { it.targetKey == "delivered" }.id, ReplyDeliveryStatus.DELIVERED)
            val unknownId = targets.single { it.targetKey == "unknown" }.id
            assertEquals(1, database.replyDeliveryDao().markSending(unknownId, 60L))
            database.replyDeliveryDao().recoverSendingAsUnknown(61L)
            resolve(database, targets.single { it.targetKey == "failed" }.id, ReplyDeliveryStatus.FAILED)
            database.turnDao().update(
                removedTurn.copy(status = "COMPLETED", finishedAt = 100L, failureKind = null),
            )

            service.restoreCheckpoint(session.id, checkpoint.displayNumber, checkpoint.id)

            assertTrue(database.messageDao().forTurn(session.id, 2).isEmpty())
            assertTrue(database.replyDeliveryDao().forMessage(accepted.messageIds.single()).isEmpty())
            assertTrue(database.retiredReplyDao().isRetired(session.id, removedTurn.executionId, removedTurn.attemptId))
            assertEquals(
                ReplySubmissionResult.Rejected(com.elymbot.core.contract.ReplyRejectionCode.STALE_ATTEMPT),
                submitter.submit(submission),
            )
            val receipts = database.retiredReplyDao().observeReceiptsForSession(session.id).first()
            assertEquals(setOf("delivered", "unknown"), receipts.map { it.targetKey }.toSet())
            assertEquals(
                setOf(ReplyDeliveryStatus.DELIVERED.name, ReplyDeliveryStatus.UNKNOWN.name),
                receipts.map { it.status }.toSet(),
            )
            assertTrue(service.conversation(session.id).first().detachedDeliveryReceipts.all { it.messageId == accepted.messageIds.single() })
            assertFalse(receipts.any { it.targetKey == "pending" || it.targetKey == "failed" })
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun v2MigrationAddsSilenceAndOutboxTablesWithoutChangingTurns() {
        val databaseName = "reply-v2-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """CREATE TABLE turns (
                                    sessionId TEXT NOT NULL, sequence INTEGER NOT NULL, executionId TEXT NOT NULL,
                                    attemptId TEXT NOT NULL, status TEXT NOT NULL, startedAt INTEGER NOT NULL,
                                    finishedAt INTEGER, personaId TEXT NOT NULL, personaName TEXT NOT NULL,
                                    personaPrompt TEXT NOT NULL, primaryServiceId TEXT, primaryModelId TEXT,
                                    visionServiceId TEXT, visionModelId TEXT, failureKind TEXT,
                                    retryable INTEGER NOT NULL, PRIMARY KEY(sessionId, sequence))""".trimIndent(),
                            )
                            db.execSQL(
                                """INSERT INTO turns VALUES (
                                    'legacy', 4, 'legacy-execution', 'legacy-attempt', 'COMPLETED', 10,
                                    20, 'persona', 'Persona', '', NULL, NULL, NULL, NULL, NULL, 0)""".trimIndent(),
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ElymBotDatabase.MIGRATION_2_3.migrate(db)
            db.query("SELECT successfulSilence FROM turns WHERE sessionId = 'legacy' AND sequence = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            assertTrue(tables.containsAll(setOf(
                "reply_submissions",
                "reply_deliveries",
                "retired_reply_executions",
                "retired_reply_receipts",
            )))
            assertNotNull(tables.firstOrNull { it == "turns" })
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private suspend fun seedRunningTurn(database: ElymBotDatabase, sequence: Long): TurnEntity {
        val persona = testPersona()
        val now = System.currentTimeMillis()
        database.personaDao().insert(persona)
        val session = ConversationEntity(
            id = "session-${UUID.randomUUID()}",
            kind = "DEDICATED",
            personaId = persona.id,
            title = "Test",
            mainKey = null,
            currentCheckpointId = "checkpoint",
            nextTurnSequence = sequence + 1,
            unread = false,
            createdAt = now,
            lastActivityAt = now,
        )
        database.conversationDao().insert(session)
        database.checkpointDao().insert(CheckpointEntity("checkpoint", session.id, 1, 0, 1, now))
        return insertRunningTurn(database, session.id, sequence)
    }

    private suspend fun insertRunningTurn(database: ElymBotDatabase, sessionId: String, sequence: Long): TurnEntity {
        val persona = database.personaDao().getAll().first()
        val turn = TurnEntity(
            sessionId = sessionId,
            sequence = sequence,
            executionId = "execution-$sequence-${UUID.randomUUID()}",
            attemptId = "attempt-$sequence-${UUID.randomUUID()}",
            status = "RUNNING",
            startedAt = System.currentTimeMillis(),
            finishedAt = null,
            personaId = persona.id,
            personaName = persona.name,
            personaPrompt = persona.prompt,
            primaryServiceId = null,
            primaryModelId = null,
            visionServiceId = null,
            visionModelId = null,
            failureKind = null,
            retryable = false,
        )
        database.turnDao().insert(turn)
        database.messageDao().insert(
            MessageEntity(
                id = "user-${UUID.randomUUID()}",
                sessionId = sessionId,
                turnSequence = sequence,
                messageIndex = 0,
                role = "USER",
                text = "input $sequence",
                category = "HISTORY",
                status = null,
                author = null,
            ),
        )
        return turn
    }

    private suspend fun insertCompletedTurn(database: ElymBotDatabase, session: ConversationEntity, sequence: Long) {
        val running = insertRunningTurn(database, session.id, sequence)
        val persona = database.personaDao().get(running.personaId)!!
        database.messageDao().insert(
            MessageEntity(
                id = "assistant-${UUID.randomUUID()}",
                sessionId = session.id,
                turnSequence = sequence,
                messageIndex = 1,
                role = "ASSISTANT",
                text = "retained response",
                category = "HISTORY",
                status = null,
                author = persona.name,
            ),
        )
        database.turnDao().update(running.copy(status = "COMPLETED", finishedAt = System.currentTimeMillis()))
        database.conversationDao().update(session.copy(nextTurnSequence = sequence + 1))
    }

    private fun submission(turn: TurnEntity, callOrdinal: Int, messages: List<String>) = ReplySubmission(
        context = ReplySubmissionContext(
            sessionId = turn.sessionId,
            executionId = turn.executionId,
            attemptId = turn.attemptId,
            turnSequence = turn.sequence,
            toolStepOrdinal = 0,
            toolCallOrdinal = callOrdinal,
            toolCallId = "call-$callOrdinal",
        ),
        messages = messages.map { ReplyMessage(it, suggestedDelayMillis = 0L) },
    )

    private fun externalTargets(vararg targetKeys: String) = ReplyTargetSnapshotProvider { _ ->
        val targets = targetKeys.map { key ->
            ReplyDeliveryTarget(
                targetKey = key,
                kind = ReplyDeliveryTargetKind.PLATFORM,
                route = MessageRoute("test", "conversation-$key", accountId = "account"),
                installationIdentity = ToolInstallationIdentity("plugin-$key", "instance-$key"),
            )
        }
        ReplyTargetSnapshot(targets, historyVisibilityTargetKey = targetKeys.first())
    }

    private fun appTranscriptTarget() = ReplyTargetSnapshotProvider { _ ->
        ReplyTargetSnapshot(
            targets = listOf(
                ReplyDeliveryTarget(APP_TRANSCRIPT_TARGET_KEY, ReplyDeliveryTargetKind.APP_TRANSCRIPT),
            ),
            historyVisibilityTargetKey = APP_TRANSCRIPT_TARGET_KEY,
        )
    }

    private suspend fun resolve(database: ElymBotDatabase, id: String, status: ReplyDeliveryStatus) {
        assertEquals(1, database.replyDeliveryDao().markSending(id, System.currentTimeMillis()))
        assertEquals(
            1,
            database.replyDeliveryDao().resolveAttempt(
                id = id,
                status = status.name,
                remoteMessageId = "remote-$id".takeIf { status == ReplyDeliveryStatus.DELIVERED },
                failureCode = "TEST_FAILURE".takeIf { status == ReplyDeliveryStatus.FAILED },
                now = System.currentTimeMillis(),
            ),
        )
    }

    private fun historyRequest(sessionId: String) = HistoryReadRequest(
        sessionId = sessionId,
        upperBoundExclusive = Long.MAX_VALUE,
        direction = HistoryDirection.OLDEST_TO_NEWEST,
        pageSize = 20,
        tokenBudget = 2_000,
    )

    private fun testPersona() = PersonaEntity(
        id = "persona-${UUID.randomUUID()}",
        name = "Reply test persona",
        description = "",
        tagsValue = "",
        prompt = "",
        favorite = false,
        coverPath = null,
        atlasTile = 0,
        coverX = 0.5f,
        coverY = 0.5f,
        avatarX = 0.5f,
        avatarY = 0.5f,
        sortOrder = 0,
    )

    private fun repository(database: ElymBotDatabase) = ElymBotDataRepository(
        context = context,
        database = database,
        assets = PrivateAssetStore(context, root = File(context.cacheDir, "reply-test-${UUID.randomUUID()}")),
    )

    private fun service(repository: ElymBotDataRepository, scope: CoroutineScope) = ElymBotSessionService(
        context = context,
        repository = repository,
        connectionProvider = com.elymbot.android.data.RoomModelServiceConnectionProvider(repository),
        modelGateway = TestModelGateway,
        agentRuntime = AgentRuntime { request ->
            kotlinx.coroutines.flow.flowOf(
                AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED),
            )
        },
        commandParser = SlashCommandParser { _, _ -> CommandParseResult.NotCommand },
        scope = scope,
    )

    private object TestModelGateway : ModelGateway {
        override suspend fun listModels(serviceId: String): List<String> = emptyList()

        override suspend fun probe(model: ModelReference) = ModelCapabilityReport(
            model = model,
            availability = CapabilityStatus.UNKNOWN,
            vision = CapabilityStatus.UNKNOWN,
            nativeStreaming = CapabilityStatus.UNKNOWN,
            structuredToolCalls = CapabilityStatus.UNKNOWN,
        )

        override fun stream(request: ModelRequest) = emptyFlow<ModelStreamEvent>()
    }
}
