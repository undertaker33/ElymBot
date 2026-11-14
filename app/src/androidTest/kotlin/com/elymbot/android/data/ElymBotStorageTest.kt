package com.elymbot.android.data

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.elymbot.android.R
import com.elymbot.android.session.CommandTextProvider
import com.elymbot.android.session.ElymBotSessionService
import com.elymbot.android.ui.ChatCheckpoint
import com.elymbot.android.ui.ChatCommand
import com.elymbot.android.ui.ModelChoice
import com.elymbot.android.ui.ModelRole as UiModelRole
import com.elymbot.android.ui.ModelServiceDraft
import com.elymbot.android.ui.PersonaCard
import com.elymbot.android.ui.UiIssue
import com.elymbot.android.ui.UiServiceException
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentFailure
import com.elymbot.core.contract.AgentFailureKind
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentRuntime
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.CommandAction
import com.elymbot.core.contract.CommandNames
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.HistoryCursor
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryRole
import com.elymbot.core.contract.HistoryTurnRange
import com.elymbot.core.contract.HistorySummaryCandidate
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelProbeTarget
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.SummaryCommitResult
import com.elymbot.core.model.OpenAiCompatibleModelGateway
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElymBotStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyDatabaseCreatesMainWithFirstPersonaAndSurvivesRestart() = runBlocking {
        val databaseName = "elymbot-bootstrap-${UUID.randomUUID()}.db"
        val fixture = Fixture(context, databaseName)
        var restartedDatabase: ElymBotDatabase? = null
        try {
            fixture.repository.initialize()
            fixture.repository.initialize()
            assertEquals(0, fixture.database.personaDao().count())
            assertNull(fixture.database.conversationDao().getMain())

            val service = fixture.service()
            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_first_main BEFORE INSERT ON conversations " +
                    "WHEN NEW.mainKey = 'main' BEGIN SELECT RAISE(ABORT, 'injected first-main failure'); END",
            )
            val failedBootstrap = runCatching {
                service.savePersona(PersonaCard(name = "First persona", prompt = "first"))
            }.exceptionOrNull()
            assertNotNull(failedBootstrap)
            assertEquals(0, fixture.database.personaDao().count())
            assertNull(fixture.database.conversationDao().getMain())
            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_first_main")

            val firstId = service.savePersona(PersonaCard(name = "First persona", prompt = "first"))
            val main = fixture.database.conversationDao().getMain()!!
            val emptyCheckpoint = fixture.database.checkpointDao().getForSession(main.id).single()
            assertEquals(firstId, main.personaId)
            assertEquals("main", main.id)
            assertEquals(1, fixture.database.conversationDao().countMain())
            assertEquals("empty:main", emptyCheckpoint.id)
            assertEquals(1, emptyCheckpoint.displayNumber)
            assertEquals(0L, emptyCheckpoint.boundaryTurnSequence)

            val secondId = service.savePersona(PersonaCard(name = "Second persona", prompt = "second"))
            fixture.closeDatabase()
            val reopened = Room.databaseBuilder(context, ElymBotDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            restartedDatabase = reopened
            val restarted = ElymBotDataRepository(context, reopened, fixture.assets)
            restarted.initialize()

            assertEquals(setOf(firstId, secondId), reopened.personaDao().getAll().map { it.id }.toSet())
            assertEquals(firstId, reopened.conversationDao().getMain()?.personaId)
            assertEquals(main, reopened.conversationDao().getMain())
            assertEquals(emptyCheckpoint, reopened.checkpointDao().getForSession("main").single())
        } finally {
            restartedDatabase?.close()
            fixture.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun newSessionsKeepTheirPersonaOwnerAndAreListedOnlyForThatPersona() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val service = fixture.service()
            val firstPersonaId = fixture.personaId(1)
            val secondPersonaId = fixture.personaId(2)
            val firstId = service.newSession(firstPersonaId)
            val secondId = service.newSession(secondPersonaId)
            fixture.saveServiceAndSelectModel(service)
            service.send(firstId, "first message", null)
            service.send(secondId, "second message", null)
            fixture.awaitStatus(firstId, "COMPLETED")
            fixture.awaitStatus(secondId, "COMPLETED")

            val first = fixture.database.conversationDao().get(firstId)!!
            val second = fixture.database.conversationDao().get(secondId)!!
            assertEquals("DEDICATED", first.kind)
            assertEquals(firstPersonaId, first.personaId)
            assertEquals(secondPersonaId, second.personaId)
            assertEquals(0L, fixture.database.checkpointDao().getForSession(firstId).single().boundaryTurnSequence)
            assertEquals(setOf(firstId), service.sessions(firstPersonaId).first().map { it.id }.toSet())
            assertEquals(setOf(secondId), service.sessions(secondPersonaId).first().map { it.id }.toSet())
            assertEquals("main", fixture.database.conversationDao().getMain()?.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deletingPersonaRemovesEmptyDraftsButNeverOrphansAFirstTurn() = runBlocking {
        val fixture = Fixture(context)
        val sendBarrier = CompletableDeferred<Unit>()
        try {
            fixture.initializeTestState()
            val service = fixture.service()
            val draft = service.newSession(fixture.personaId(0))

            service.deletePersona(fixture.personaId(0))

            assertNull(fixture.database.personaDao().get(fixture.personaId(0)))
            assertNull(fixture.database.conversationDao().get(draft))
            assertTrue(fixture.database.checkpointDao().getForSession(draft).isEmpty())

            val firstTurn = service.newSession(fixture.personaId(1))
            service.saveModelService(
                ModelServiceDraft(
                    provider = "compatible",
                    name = "Test service",
                    baseUrl = "https://model.example/v1",
                    apiKey = "test-key",
                    modelId = "model-a",
                ),
            )
            val modelService = fixture.database.modelConfigurationDao().getServices().single()
            service.setModel(UiModelRole.Text, ModelChoice(modelService.id, "model-a"))
            val racingService = fixture.service(
                runtime = AgentRuntime { request ->
                    flow {
                        sendBarrier.await()
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "reply"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            racingService.setModel(UiModelRole.Text, ModelChoice(modelService.id, "model-a"))
            val ready = CompletableDeferred<Unit>()
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                ready.await()
                runCatching { racingService.send(firstTurn, "first message", null) }.exceptionOrNull()
            }
            val delete = async(start = CoroutineStart.UNDISPATCHED) {
                ready.await()
                runCatching { racingService.deletePersona(fixture.personaId(1)) }.exceptionOrNull()
            }
            ready.complete(Unit)
            val sendFailure = send.await()
            val deleteFailure = delete.await()
            val personaStillExists = fixture.database.personaDao().get(fixture.personaId(1)) != null
            val storedTurn = fixture.database.turnDao().latest(firstTurn)
            if (personaStillExists) {
                assertNotNull(fixture.database.conversationDao().get(firstTurn))
                assertNotNull(storedTurn)
                assertTrue(deleteFailure is UiServiceException)
                assertEquals(UiIssue.OwnedSessions, (deleteFailure as UiServiceException).issue)
                sendBarrier.complete(Unit)
                fixture.awaitStatus(firstTurn, "COMPLETED")
            } else {
                assertNull(fixture.database.conversationDao().get(firstTurn))
                assertNull(storedTurn)
                assertTrue(sendFailure is UiServiceException)
                assertEquals(UiIssue.InvalidInput, (sendFailure as UiServiceException).issue)
            }
        } finally {
            sendBarrier.complete(Unit)
            fixture.close()
        }
    }

    @Test
    fun firstMessageTextTitleCollapsesWhitespaceAndUsesTwentySixCodePoints() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val service = fixture.service()
            val textSession = service.newSession(fixture.personaId(0))
            fixture.saveServiceAndSelectModel(service)
            val text = "  alpha   beta\n" + "😀".repeat(30)
            service.send(textSession, text, null)
            fixture.awaitStatus(textSession, "COMPLETED")

            val title = fixture.database.conversationDao().get(textSession)!!.title!!
            assertEquals(26, title.codePointCount(0, title.length))
            assertEquals("alpha beta ${"😀".repeat(15)}", title)
        } finally {
            fixture.close()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun imageOnlySessionUsesLocalizedTitleAndDeliversContentUriBytes() = runBlocking {
        val fixture = Fixture(context)
        val imagePayloadCount = AtomicInteger()
        val imageMediaType = AtomicReference<String?>()
        val imageByteCount = AtomicInteger()
        var imageUri: Uri? = null
        try {
            fixture.initializeTestState()
            val imageSession = fixture.service().newSession(fixture.personaId(0))
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    val images = request.currentInput.content.filterIsInstance<ModelContentPart.Image>()
                    if (images.isNotEmpty()) {
                        imagePayloadCount.addAndGet(images.size)
                        imageMediaType.set(images.single().mediaType)
                        imageByteCount.set(images.single().bytes.size)
                    }
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "image received"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)

            val uri = fixture.createImageContentUri()
            imageUri = uri
            assertTrue(uri.toString().startsWith("content://"))
            assertEquals("image/png", context.contentResolver.getType(uri))
            service.send(imageSession, " \n ", uri)
            fixture.awaitStatus(imageSession, "COMPLETED")

            assertEquals(context.getString(R.string.elym_session_image_title), fixture.database.conversationDao().get(imageSession)?.title)
            assertEquals(1, imagePayloadCount.get())
            assertEquals("image/png", imageMediaType.get())
            assertTrue(imageByteCount.get() > 0)
            val userMessage = fixture.database.messageDao().forTurn(imageSession, 1).first { it.message.role == "USER" }
            assertEquals("image/png", userMessage.attachments.single().mediaType)
        } finally {
            imageUri?.let { context.contentResolver.delete(it, null, null) }
            fixture.close()
        }
    }

    @Test
    fun renameSessionChangesOnlyTitleAndActivityColumns() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val service = fixture.service()
            val sessionId = service.newSession(fixture.personaId(0))
            val session = fixture.database.conversationDao().get(sessionId)!!
            fixture.database.conversationDao().update(session.copy(nextTurnSequence = 17, unread = true, lastActivityAt = 1))
            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_rename_state_write BEFORE UPDATE OF nextTurnSequence ON conversations " +
                    "WHEN OLD.id = '$sessionId' BEGIN SELECT RAISE(ABORT, 'rename wrote unrelated state'); END",
            )

            service.renameSession(sessionId, "  renamed  ")

            val renamed = fixture.database.conversationDao().get(sessionId)!!
            assertEquals("renamed", renamed.title)
            assertEquals(17L, renamed.nextTurnSequence)
            assertTrue(renamed.unread)
            assertTrue(renamed.lastActivityAt > 1)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restoreDeletesTailSummariesAttachmentsAndLaterSameBoundaryCheckpointAtomically() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val coverPath = "media/turn-two.png"
            val coverFile = fixture.assets.file(coverPath).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            (1L..4L).forEach { sequence ->
                fixture.insertTurn(main.id, sequence, "user-$sequence", "assistant-$sequence", if (sequence == 2L) coverPath else null)
            }
            val checkpointDao = fixture.database.checkpointDao()
            val checkpoint2 = CheckpointEntity("cp-2", main.id, 2, 1, 2, 2)
            val laterSameBoundary = CheckpointEntity("cp-3", main.id, 3, 1, 3, 3)
            val checkpoint4 = CheckpointEntity("cp-4", main.id, 4, 3, 4, 4)
            checkpointDao.insert(checkpoint2)
            checkpointDao.insert(laterSameBoundary)
            checkpointDao.insert(checkpoint4)
            fixture.database.conversationDao().update(main.copy(currentCheckpointId = checkpoint4.id, nextTurnSequence = 5))
            fixture.database.historySummaryDao().insert(HistorySummaryEntity("sum-1", main.id, 1, 1, "keep", 1))
            fixture.database.historySummaryDao().insert(HistorySummaryEntity("sum-cross", main.id, 1, 2, "remove", 2))
            fixture.database.historySummaryDao().insert(HistorySummaryEntity("sum-late", main.id, 3, 4, "remove", 3))

            runCatching {
                fixture.database.withTransaction {
                    fixture.database.turnDao().deleteAfter(main.id, 1)
                    fixture.database.checkpointDao().deleteAfter(main.id, 2)
                    error("force transaction rollback")
                }
            }
            assertEquals(4L, fixture.database.turnDao().latest(main.id)?.sequence)
            assertEquals(4, checkpointDao.getForSession(main.id).size)

            val service = fixture.service(
                parser = SlashCommandParser { input, _ ->
                    if (input == "/checkpoint 2") CommandParseResult.Action(CommandAction.Checkpoint(2))
                    else CommandParseResult.NotCommand
                },
            )
            service.send(main.id, "/checkpoint 2", null)

            assertEquals(1L, fixture.database.turnDao().latest(main.id)?.sequence)
            assertEquals(listOf("empty:main", "cp-2"), checkpointDao.getForSession(main.id).map { it.id })
            assertEquals("cp-2", fixture.database.conversationDao().getMain()?.currentCheckpointId)
            assertEquals(listOf("sum-1"), fixture.database.historySummaryDao().getForSession(main.id).map { it.id })
            assertTrue(fixture.database.attachmentDao().referencedPaths().isEmpty())
            assertFalse(coverFile.exists())
            assertEquals(5L, fixture.database.conversationDao().getMain()?.nextTurnSequence)
            assertTrue(fixture.database.messageDao().forTurn(main.id, 5).isEmpty())
            val retainedHistory = RoomHistoryStore(fixture.database).read(
                HistoryReadRequest(
                    sessionId = main.id,
                    upperBoundExclusive = 5,
                    pageSize = 20,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("user-1", "assistant-1"), retainedHistory.records.map { it.text })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service()

            assertEquals(listOf(ChatCheckpoint(1, current = true, retainedMessageCount = 0)), service.checkpoints(main.id).first())
            val emptySave = runCatching { service.saveCheckpoint(main.id) }.exceptionOrNull() as UiServiceException
            assertEquals(UiIssue.NoCheckpointBoundary, emptySave.issue)

            fixture.insertTurn(main.id, 1, "user-1", "assistant-1")
            fixture.insertTurn(main.id, 2, "user-2", "assistant-2")
            fixture.database.messageDao().insertAll(
                listOf(
                    MessageEntity("feedback-${main.id}", main.id, 2, 2, "ASSISTANT", "tool display", "FEEDBACK", null, null),
                    MessageEntity("command-${main.id}", main.id, 2, 3, "USER", "/mark", "COMMAND", null, null),
                ),
            )

            service.saveCheckpoint(main.id)
            assertEquals(
                listOf(
                    ChatCheckpoint(1, current = false, retainedMessageCount = 0),
                    ChatCheckpoint(2, current = true, retainedMessageCount = 4),
                ),
                service.checkpoints(main.id).first(),
            )

            service.restoreCheckpoint(main.id, 1)
            assertEquals(listOf("empty:main"), fixture.database.checkpointDao().getForSession(main.id).map { it.id })
            assertEquals("empty:main", fixture.database.conversationDao().getMain()?.currentCheckpointId)
            assertNull(fixture.database.turnDao().latest(main.id))
            val restoredConversation = service.conversation(main.id).first()
            assertEquals(listOf("restored 1"), restoredConversation.messages.map { it.text })
            assertTrue(restoredConversation.messages.single().assistant)

            val restoreEmpty = runCatching { service.saveCheckpoint(main.id) }.exceptionOrNull() as UiServiceException
            assertEquals(UiIssue.NoCheckpointBoundary, restoreEmpty.issue)

            fixture.insertTurn(main.id, 3, "user-3", "assistant-3")
            service.saveCheckpoint(main.id)
            val recreated = service.checkpoints(main.id).first()
            assertEquals(listOf(1, 2), recreated.map { it.displayNumber })
            assertEquals(ChatCheckpoint(2, current = true, retainedMessageCount = 2), recreated.last())

            val completed = fixture.database.turnDao().get(main.id, 3)!!
            fixture.database.withTransaction {
                fixture.database.turnDao().insert(
                    completed.copy(
                        sequence = 4,
                        executionId = "running-${main.id}",
                        attemptId = "running-${main.id}",
                        status = "RUNNING",
                        startedAt = 4,
                        finishedAt = null,
                    ),
                )
                val current = fixture.database.conversationDao().get(main.id)!!
                fixture.database.conversationDao().update(current.copy(nextTurnSequence = 5))
            }

            val saveWhileGenerating = runCatching { service.saveCheckpoint(main.id) }.exceptionOrNull() as UiServiceException
            assertEquals(UiIssue.Busy, saveWhileGenerating.issue)
            val restoreWhileGenerating = runCatching { service.restoreCheckpoint(main.id, 1) }.exceptionOrNull() as UiServiceException
            assertEquals(UiIssue.Busy, restoreWhileGenerating.issue)
            assertEquals(recreated, service.checkpoints(main.id).first())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun historyPagesStayWithinSessionUpperBoundAndExcludeDisplayOnlyFeedback() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val dedicated = fixture.createSession(fixture.personaId(1))
            fixture.insertTurn(main.id, 1, "main one", "reply one")
            fixture.insertTurn(main.id, 2, "main two", "reply two")
            fixture.database.messageDao().insert(
                MessageEntity("feedback", main.id, 2, 2, "ASSISTANT", "tool display", "FEEDBACK", null, "Fixture persona 0"),
            )
            fixture.insertTurn(dedicated.id, 1, "private one", "private reply")
            val store = RoomHistoryStore(fixture.database)

            val page = store.read(
                HistoryReadRequest(
                    sessionId = main.id,
                    upperBoundExclusive = 2,
                    direction = HistoryDirection.NEWEST_TO_OLDEST,
                    pageSize = 10,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("main one", "reply one"), page.records.map { it.text })
            assertTrue(page.records.all { it.role == HistoryRole.USER || it.role == HistoryRole.ASSISTANT })
            val otherSession = store.read(
                HistoryReadRequest(
                    sessionId = dedicated.id,
                    upperBoundExclusive = 2,
                    pageSize = 10,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("private one", "private reply"), otherSession.records.map { it.text })
            assertFalse(page.records.any { it.text == "tool display" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun historyQueriesApplyRangeDirectionCursorAndPageLimit() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            fixture.insertTurn(main.id, 1, "u1", "a1")
            fixture.insertTurn(main.id, 3, "u3", "a3")
            fixture.insertTurn(main.id, 5, "u5", "a5")
            val store = RoomHistoryStore(fixture.database)

            val newestPage = store.read(
                HistoryReadRequest(
                    sessionId = main.id,
                    upperBoundExclusive = 6,
                    turnRange = HistoryTurnRange(2, 5),
                    direction = HistoryDirection.NEWEST_TO_OLDEST,
                    pageSize = 1,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("a5"), newestPage.records.map { it.text })
            assertEquals(HistoryCursor(5, 1), newestPage.continuation)
            assertTrue(newestPage.hasMore)

            val nextNewestPage = store.read(
                HistoryReadRequest(
                    sessionId = main.id,
                    upperBoundExclusive = 6,
                    turnRange = HistoryTurnRange(2, 5),
                    direction = HistoryDirection.NEWEST_TO_OLDEST,
                    cursor = newestPage.continuation,
                    pageSize = 1,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("u5"), nextNewestPage.records.map { it.text })
            assertTrue(nextNewestPage.hasMore)

            val oldestPage = store.read(
                HistoryReadRequest(
                    sessionId = main.id,
                    upperBoundExclusive = 5,
                    turnRange = HistoryTurnRange(1, 3),
                    direction = HistoryDirection.OLDEST_TO_NEWEST,
                    pageSize = 1,
                    tokenBudget = 100,
                ),
            )
            assertEquals(listOf("u1"), oldestPage.records.map { it.text })
            assertEquals(HistoryCursor(1, 0), oldestPage.continuation)
            assertTrue(oldestPage.hasMore)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetedAssetCleanupPreservesAnUncommittedImportAndAssetPathsRoundTrip() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val pendingPath = "media/pending/imported-image.png"
            val pendingFile = fixture.assets.file(pendingPath).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val orphanPath = "media/deleted-image.png"
            val orphanFile = fixture.assets.file(orphanPath).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4, 5, 6))
            }

            fixture.repository.cleanupUnreferencedAssets(listOf(orphanPath))

            assertTrue(pendingFile.exists())
            assertFalse(orphanFile.exists())
            val pendingUri = Uri.fromFile(pendingFile)
            assertEquals(pendingPath, fixture.assets.relativePath(pendingUri))
            assertEquals(pendingFile.canonicalFile, fixture.assets.file(pendingPath).canonicalFile)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun summaryCommitValidatesCoverageAndReplacesOnlyListedSummaries() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            fixture.insertTurn(main.id, 1, "u1", "a1")
            fixture.insertTurn(main.id, 2, "u2", "a2")
            val store = RoomHistoryStore(fixture.database)
            val first = store.commit(HistorySummaryCandidate(main.id, 1, 1, "first"))
            val firstSummary = (first as SummaryCommitResult.Committed).summary
            val merge = store.commit(
                HistorySummaryCandidate(main.id, 1, 2, "merged", setOf(firstSummary.id)),
            )
            assertTrue(merge is SummaryCommitResult.Committed)
            assertEquals(listOf("merged"), store.read(main.id, 3).map { it.text })
            val overlap = store.commit(HistorySummaryCandidate(main.id, 2, 2, "overlap"))
            assertEquals(SummaryCommitResult.RejectedOverlap, overlap)
            val stale = store.commit(HistorySummaryCandidate(main.id, 1, 3, "future"))
            assertEquals(SummaryCommitResult.RejectedStaleCoverage, stale)
            assertEquals(listOf("u1", "a1", "u2", "a2"), fixture.database.messageDao().history(main.id, 3).map { it.text })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun batchDeleteChecksOwnershipCascadesRowsAndAllowsPersonaDeletionAfterward() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val sessionA = fixture.createSession(fixture.personaId(1))
            val sessionB = fixture.createSession(fixture.personaId(1))
            val path = "media/deleted-image.png"
            val file = fixture.assets.file(path).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(9, 8, 7))
            }
            fixture.insertTurn(sessionA.id, 1, "a", "b", path)
            fixture.insertTurn(sessionB.id, 1, "c", "d")
            val service = fixture.service()

            service.deleteSessions(fixture.personaId(1), setOf(sessionA.id))

            assertNull(fixture.database.conversationDao().get(sessionA.id))
            assertEquals(1L, fixture.database.turnDao().latest(sessionB.id)?.sequence)
            assertFalse(file.exists())
            assertEquals(3, fixture.database.personaDao().count())
            val ownedSessions = runCatching { service.deletePersona(fixture.personaId(1)) }.exceptionOrNull()
            assertEquals(UiIssue.OwnedSessions, (ownedSessions as UiServiceException).issue)

            service.deleteSessions(fixture.personaId(1), setOf(sessionB.id))
            service.deletePersona(fixture.personaId(1))
            assertNull(fixture.database.personaDao().get(fixture.personaId(1)))
            assertEquals(2, fixture.database.personaDao().count())
            assertEquals(fixture.personaId(0), fixture.database.conversationDao().getMain()?.personaId)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun diskRestartPreservesSessionHistoryModelsCommandsAndPersonaImagesThenReusesCheckpointNumber() = runBlocking {
        val databaseName = "elymbot-storage-restart-${UUID.randomUUID()}.db"
        val fixture = Fixture(context, databaseName)
        var reopenedDatabase: ElymBotDatabase? = null
        try {
            fixture.initializeTestState()
            val service = fixture.service(
                parser = SlashCommandParser { input, _ ->
                    when (input) {
                        "/mark" -> CommandParseResult.Action(CommandAction.Mark)
                        "/checkpoint 1" -> CommandParseResult.Action(CommandAction.Checkpoint(1))
                        else -> CommandParseResult.NotCommand
                    }
                },
            )
            val main = fixture.database.conversationDao().getMain()!!
            val sessionA = service.newSession(fixture.personaId(1))
            val sessionB = service.newSession(fixture.personaId(2))
            fixture.insertTurn(main.id, 1, "main user 1", "main assistant 1")
            service.send(main.id, "/mark", null)
            fixture.insertTurn(main.id, 2, "main user 2", "main assistant 2")
            fixture.insertTurn(sessionA, 1, "session A user", "session A assistant")
            fixture.insertTurn(sessionB, 1, "session B user", "session B assistant")

            val coverPath = "media/persona-cover.png"
            val cover = fixture.assets.file(coverPath).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4, 5, 6, 7))
            }
            val personaId = service.savePersona(
                PersonaCard(
                    name = "持久人格",
                    description = "说明",
                    tags = listOf("学习"),
                    prompt = "prompt",
                    cover = Uri.fromFile(cover),
                    coverPosition = com.elymbot.android.ui.ImagePosition(0.22f, 0.77f),
                    avatarPosition = com.elymbot.android.ui.ImagePosition(0.31f, 0.64f),
                ),
            )
            val savedPersona = fixture.database.personaDao().get(personaId)!!
            assertEquals(0.22f, savedPersona.coverX)
            assertEquals(0.77f, savedPersona.coverY)
            assertEquals(0.31f, savedPersona.avatarX)
            assertEquals(0.64f, savedPersona.avatarY)
            assertEquals(coverPath, savedPersona.coverPath)

            val secret = "private-api-token-2026"
            service.saveModelService(
                ModelServiceDraft(
                    provider = "compatible",
                    name = "Test service",
                    baseUrl = "https://model.example/v1",
                    apiKey = secret,
                    modelId = "model-a",
                ),
            )
            val storedService = fixture.database.modelConfigurationDao().getServices().single()
            service.setModel(UiModelRole.Text, ModelChoice(storedService.id, "model-a"))
            service.setModel(UiModelRole.Vision, ModelChoice(storedService.id, "model-a"))
            val renamedCommands = ChatCommand.entries.associateWith { "test-${it.defaultName}" }
            service.saveCommandNames(renamedCommands)
            val credential = fixture.database.credentialDao().get(storedService.id)!!
            assertFalse(String(credential.encryptedKey, Charsets.UTF_8).contains(secret))
            assertEquals(secret, fixture.repository.credentials.resolve(storedService.id))

            fixture.closeDatabase()
            val reopened = Room.databaseBuilder(context, ElymBotDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            reopenedDatabase = reopened
            val restarted = ElymBotDataRepository(context, reopened, fixture.assets)
            restarted.initialize()

            val reopenedPersona = reopened.personaDao().get(personaId)!!
            assertEquals(savedPersona, reopenedPersona)
            assertEquals(0.22f, reopenedPersona.coverX)
            assertEquals(0.77f, reopenedPersona.coverY)
            assertEquals(0.31f, reopenedPersona.avatarX)
            assertEquals(0.64f, reopenedPersona.avatarY)
            assertEquals(secret, restarted.credentials.resolve(storedService.id))
            assertEquals(ModelAssignmentEntity(UiModelRole.Text.name, storedService.id, "model-a"), reopened.modelConfigurationDao().getAssignment(UiModelRole.Text.name))
            assertEquals(ModelAssignmentEntity(UiModelRole.Vision.name, storedService.id, "model-a"), reopened.modelConfigurationDao().getAssignment(UiModelRole.Vision.name))
            assertEquals(
                ChatCommand.entries.associate { it.name to "test-${it.defaultName}" },
                reopened.commandNameDao().getAll().associate { it.behaviorId to it.name },
            )
            assertEquals(
                listOf("main user 1", "main assistant 1", "main user 2", "main assistant 2"),
                reopened.messageDao().history(main.id, 3).map { it.text },
            )
            assertEquals(
                listOf("session A user", "session A assistant"),
                reopened.messageDao().history(sessionA, 2).map { it.text },
            )
            assertEquals(
                listOf("session B user", "session B assistant"),
                reopened.messageDao().history(sessionB, 2).map { it.text },
            )
            val checkpointBeforeRollback = reopened.checkpointDao().getForSession(main.id)
            assertEquals(listOf(1, 2), checkpointBeforeRollback.map { it.displayNumber })
            assertEquals("main", reopened.conversationDao().getMain()?.id)

            val restartedService = fixture.service(
                repository = restarted,
                parser = SlashCommandParser { input, _ ->
                    when (input) {
                        "/mark" -> CommandParseResult.Action(CommandAction.Mark)
                        "/checkpoint 1" -> CommandParseResult.Action(CommandAction.Checkpoint(1))
                        else -> CommandParseResult.NotCommand
                    }
                },
            )
            restartedService.send(main.id, "/checkpoint 1", null)
            assertTrue(reopened.messageDao().history(main.id, 4).isEmpty())
            assertEquals(listOf(1), reopened.checkpointDao().getForSession(main.id).map { it.displayNumber })
            assertEquals(
                listOf("session A user", "session A assistant"),
                reopened.messageDao().history(sessionA, 2).map { it.text },
            )
            assertEquals(
                listOf("session B user", "session B assistant"),
                reopened.messageDao().history(sessionB, 2).map { it.text },
            )

            restartedService.send(main.id, "after rollback", null)
            withTimeout(10_000) {
                while (reopened.turnDao().latest(main.id)?.status != "COMPLETED") delay(10)
            }
            restartedService.send(main.id, "/mark", null)
            val recreatedCheckpoints = reopened.checkpointDao().getForSession(main.id)
            assertEquals(listOf(1, 2), recreatedCheckpoints.map { it.displayNumber })
            assertEquals(listOf(0L, 3L), recreatedCheckpoints.map { it.boundaryTurnSequence })
            assertEquals(recreatedCheckpoints.last().id, reopened.conversationDao().getMain()?.currentCheckpointId)
        } finally {
            reopenedDatabase?.close()
            fixture.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun modelServiceUrlAndCredentialUpdatesStayAtomicAcrossSnapshotsAndDrafts() = runBlocking {
        val fixture = Fixture(context)
        val oldServer = ModelListServer()
        val newServer = ModelListServer()
        val draftServer = ModelListServer()
        val releaseRequest = CompletableDeferred<Unit>()
        try {
            fixture.initializeTestState()
            val provider = RoomModelServiceConnectionProvider(fixture.repository)
            val gateway = OpenAiCompatibleModelGateway(provider)
            val service = fixture.service(connectionProvider = provider, modelGateway = gateway)
            val serviceId = "test-connection"

            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_initial_credential BEFORE INSERT ON service_credentials " +
                    "WHEN NEW.serviceId = '$serviceId' BEGIN SELECT RAISE(ABORT, 'injected credential failure'); END",
            )
            val initialFailure = runCatching {
                service.saveModelService(
                    ModelServiceDraft(
                        id = serviceId,
                        provider = "compatible",
                        name = "Connection",
                        baseUrl = oldServer.baseUrl,
                        apiKey = "old-test-key",
                        modelId = "model-a",
                    ),
                )
            }.exceptionOrNull()
            assertNotNull(initialFailure)
            assertNull(fixture.database.modelConfigurationDao().getService(serviceId))
            assertNull(fixture.database.credentialDao().get(serviceId))
            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_initial_credential")

            service.saveModelService(
                ModelServiceDraft(
                    id = serviceId,
                    provider = "compatible",
                    name = "Connection",
                    baseUrl = oldServer.baseUrl,
                    apiKey = "old-test-key",
                    modelId = "model-a",
                ),
            )
            val requestCaptured = CompletableDeferred<Unit>()
            val oldSnapshotRead = async(Dispatchers.IO) {
                provider.withServiceSnapshot(serviceId) { snapshotId ->
                    requestCaptured.complete(Unit)
                    releaseRequest.await()
                    gateway.listModels(snapshotId)
                }
            }
            requestCaptured.await()

            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_replacement_credential BEFORE INSERT ON service_credentials " +
                    "WHEN NEW.serviceId = '$serviceId' BEGIN SELECT RAISE(ABORT, 'injected credential replacement failure'); END",
            )
            val updateFailure = runCatching {
                service.saveModelService(
                    ModelServiceDraft(
                        id = serviceId,
                        provider = "compatible",
                        name = "Connection",
                        baseUrl = newServer.baseUrl,
                        apiKey = "new-test-key",
                        modelId = "model-a",
                    ),
                )
            }.exceptionOrNull()
            assertNotNull(updateFailure)
            assertEquals(oldServer.baseUrl, fixture.database.modelConfigurationDao().getService(serviceId)?.baseUrl)
            assertEquals("old-test-key", fixture.repository.credentials.resolve(serviceId))
            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_replacement_credential")

            service.saveModelService(
                ModelServiceDraft(
                    id = serviceId,
                    provider = "compatible",
                    name = "Connection",
                    baseUrl = newServer.baseUrl,
                    apiKey = "new-test-key",
                    modelId = "model-a",
                ),
            )
            releaseRequest.complete(Unit)
            assertEquals(listOf("model-a"), oldSnapshotRead.await())
            assertEquals("Bearer old-test-key", oldServer.awaitAuthorization())

            service.fetchModels(
                ModelServiceDraft(
                    id = serviceId,
                    provider = "compatible",
                    name = "Connection",
                    baseUrl = newServer.baseUrl,
                    apiKey = "",
                    modelId = "model-a",
                ),
            )
            assertEquals("Bearer new-test-key", newServer.awaitAuthorization())

            service.saveModelService(
                ModelServiceDraft(
                    id = serviceId,
                    provider = "compatible",
                    name = "Connection",
                    baseUrl = draftServer.baseUrl,
                    apiKey = "",
                    modelId = "model-a",
                ),
            )
            service.fetchModels(
                ModelServiceDraft(
                    id = serviceId,
                    provider = "compatible",
                    name = "Connection",
                    baseUrl = draftServer.baseUrl,
                    apiKey = "",
                    modelId = "model-a",
                ),
            )
            assertNull(draftServer.awaitAuthorization())
            assertNull(fixture.repository.credentials.resolve(serviceId))
        } finally {
            releaseRequest.complete(Unit)
            oldServer.close()
            newServer.close()
            draftServer.close()
            fixture.close()
        }
    }

    @Test
    fun nextTurnUsesLatestSavedPersonaNameAndPrompt() = runBlocking {
        val fixture = Fixture(context)
        val requestPersonas = CopyOnWriteArrayList<Pair<String, String>>()
        try {
            fixture.initializeTestState()
            val personaId = fixture.personaId(1)
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    requestPersonas.add(request.persona.name to request.persona.prompt)
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "reply"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)
            val sessionId = service.newSession(personaId)

            service.send(sessionId, "first turn", null)
            fixture.awaitStatus(sessionId, "COMPLETED")
            val firstTurn = fixture.database.turnDao().latest(sessionId)!!
            assertEquals("Fixture persona 1", firstTurn.personaName)
            assertEquals("fixture-1", firstTurn.personaPrompt)

            val updatedId = service.savePersona(
                PersonaCard(
                    id = personaId,
                    name = "Updated fixture persona",
                    prompt = "updated fixture prompt",
                ),
            )
            assertEquals(personaId, updatedId)

            service.send(sessionId, "second turn", null)
            fixture.awaitStatus(sessionId, "COMPLETED")
            val secondTurn = fixture.database.turnDao().latest(sessionId)!!
            assertEquals(2L, secondTurn.sequence)
            assertEquals("Updated fixture persona", secondTurn.personaName)
            assertEquals("updated fixture prompt", secondTurn.personaPrompt)
            assertEquals(
                listOf(
                    "Fixture persona 1" to "fixture-1",
                    "Updated fixture persona" to "updated fixture prompt",
                ),
                requestPersonas.toList(),
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun retryReusesOneUserMessageAndCompletionUpdatesUnreadOnlyForHiddenConversation() = runBlocking {
        val fixture = Fixture(context)
        val calls = AtomicInteger()
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    flow {
                        if (calls.incrementAndGet() == 1) {
                            emit(
                                AgentEvent.Finished(
                                    request.executionId,
                                    request.attemptId,
                                    AgentTerminalStatus.FAILED,
                                    AgentFailure(AgentFailureKind.NETWORK, retryable = true),
                                ),
                            )
                        } else {
                            emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "finished"))
                            emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                        }
                    }
                },
            )
            val configId = fixture.saveServiceAndSelectModel(service)

            service.setViewedConversation(null)
            service.send(main.id, "question", null)
            fixture.awaitStatus(main.id, "FAILED")
            assertTrue(fixture.database.conversationDao().getMain()!!.unread)
            service.retry(main.id)
            fixture.awaitStatus(main.id, "COMPLETED")

            val turnMessages = fixture.database.messageDao().forTurn(main.id, 1)
            assertEquals(2, turnMessages.size)
            assertEquals(1, turnMessages.count { it.message.role == "USER" })
            assertEquals("finished", turnMessages.last().message.text)
            assertEquals(configId, fixture.database.turnDao().latest(main.id)?.primaryServiceId)
            assertTrue(fixture.database.conversationDao().getMain()!!.unread)
            service.setViewedConversation(main.id)
            assertFalse(fixture.database.conversationDao().getMain()!!.unread)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedFinishTransactionRecoversAttemptAndAllowsOneMessageRetry() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "partial"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)
            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_completed_turn BEFORE UPDATE OF status ON turns " +
                    "WHEN OLD.sessionId = '${main.id}' AND NEW.status = 'COMPLETED' " +
                    "BEGIN SELECT RAISE(ABORT, 'injected finish failure'); END",
            )

            service.send(main.id, "question", null)
            fixture.awaitStatus(main.id, "INTERRUPTED")

            val failedTurn = fixture.database.turnDao().latest(main.id)!!
            val failedMessages = fixture.database.messageDao().forTurn(main.id, failedTurn.sequence)
            assertEquals(AgentFailureKind.INTERNAL.name, failedTurn.failureKind)
            assertTrue(failedTurn.retryable)
            assertEquals(listOf("question", "partial"), failedMessages.map { it.message.text })
            assertEquals("INTERRUPTED", failedMessages.last().message.status)
            assertEquals(UiIssue.Failed, service.conversation(main.id).first { !it.generating }.issue)

            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_completed_turn")
            service.retry(main.id)
            fixture.awaitStatus(main.id, "COMPLETED")

            val completedMessages = fixture.database.messageDao().forTurn(main.id, failedTurn.sequence)
            assertEquals(1, completedMessages.count { it.message.role == "USER" })
            assertEquals(1, completedMessages.count { it.message.role == "ASSISTANT" && it.message.category == "HISTORY" })
            assertEquals("partial", completedMessages.last().message.text)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun commandReceiptsStayBetweenTheTurnsTheyFollowAndPrecede() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                parser = SlashCommandParser { text, _ ->
                    if (text == "/help") CommandParseResult.Action(CommandAction.Help)
                    else CommandParseResult.NotCommand
                },
                runtime = AgentRuntime { request ->
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "reply-${request.turnSequence}"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)

            service.send(main.id, "first question", null)
            fixture.awaitStatus(main.id, "COMPLETED")
            service.send(main.id, "/help", null)
            service.send(main.id, "second question", null)
            fixture.awaitStatus(main.id, "COMPLETED")

            val messages = service.conversation(main.id).first { it.messages.size == 6 }.messages
            assertEquals(
                listOf("first question", "reply-1", "/help", "help", "second question", "reply-2"),
                messages.map { it.text },
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun stopBeforeCoroutineBodyStartsUsesTheSameTerminalCleanup() = runBlocking {
        val fixture = Fixture(context)
        val pausedDispatcher = PausedDispatcher()
        val pausedScope = CoroutineScope(SupervisorJob() + pausedDispatcher)
        val runtimeCalls = AtomicInteger()
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    runtimeCalls.incrementAndGet()
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "unexpected"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
                scope = pausedScope,
            )
            fixture.saveServiceAndSelectModel(service)

            service.send(main.id, "question", null)
            assertTrue(pausedDispatcher.pendingCount > 0)
            val stop = async(start = CoroutineStart.UNDISPATCHED) { service.stop(main.id) }
            pausedDispatcher.runPending()
            stop.await()

            assertEquals(0, runtimeCalls.get())
            assertEquals("STOPPED", fixture.database.turnDao().latest(main.id)?.status)
            assertFalse(service.conversation(main.id).first().generating)
            assertEquals(listOf("question", ""), fixture.database.messageDao().forTurn(main.id, 1).map { it.message.text })
        } finally {
            pausedScope.cancel()
            fixture.close()
        }
    }

    @Test
    fun stopWaitsForExecutionPublishedInsideSessionStartupLock() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                runtime = AgentRuntime {
                    flow { awaitCancellation() }
                },
            )
            fixture.saveServiceAndSelectModel(service)

            val locksField = ElymBotSessionService::class.java.getDeclaredField("sessionLocks").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val locks = locksField.get(service) as ConcurrentHashMap<String, Mutex>
            val startupLock = locks.computeIfAbsent(main.id) { Mutex() }
            startupLock.lock()
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                service.send(main.id, "question", null)
            }
            val stop = async(start = CoroutineStart.UNDISPATCHED) { service.stop(main.id) }
            startupLock.unlock()

            send.await()
            stop.await()
            fixture.awaitStatus(main.id, "STOPPED")
            assertFalse(service.conversation(main.id).first().generating)
            assertFalse(service.conversation(main.id).first().toolProgressActive)
            assertEquals(1, fixture.database.messageDao().forTurn(main.id, 1).count { it.message.role == "USER" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun parallelSessionsKeepCancellationAndUnreadStateIsolated() = runBlocking {
        val fixture = Fixture(context)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        try {
            fixture.initializeTestState()
            val first = fixture.createSession(fixture.personaId(0))
            val second = fixture.createSession(fixture.personaId(1))
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    flow {
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "partial"))
                        emit(AgentEvent.ToolProgress(request.executionId, request.attemptId, active = true))
                        if (request.sessionId == first.id) firstStarted.complete(Unit)
                        if (request.sessionId == second.id) secondStarted.complete(Unit)
                        if (request.sessionId == first.id) awaitCancellation()
                        finishSecond.await()
                        emit(AgentEvent.ToolProgress(request.executionId, request.attemptId, active = false))
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, " reply"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)
            service.setViewedConversation(null)

            service.send(first.id, "first question", null)
            service.send(second.id, "second question", null)
            withTimeout(10_000) {
                firstStarted.await()
                secondStarted.await()
            }
            assertTrue(service.conversation(second.id).first { it.toolProgressActive }.toolProgressActive)
            assertEquals(1, fixture.database.messageDao().forTurn(second.id, 1).size)

            service.stop(first.id)
            fixture.awaitStatus(first.id, "STOPPED")
            assertEquals("RUNNING", fixture.database.turnDao().latest(second.id)?.status)
            assertFalse(service.conversation(first.id).first().generating)
            assertFalse(service.conversation(first.id).first().toolProgressActive)
            assertTrue(service.conversation(second.id).first().toolProgressActive)

            finishSecond.complete(Unit)
            fixture.awaitStatus(second.id, "COMPLETED")
            val firstTurn = fixture.database.turnDao().latest(first.id)!!
            val secondTurn = fixture.database.turnDao().latest(second.id)!!
            assertEquals("STOPPED", firstTurn.status)
            assertEquals("COMPLETED", secondTurn.status)
            assertEquals("partial", fixture.database.messageDao().forTurn(first.id, firstTurn.sequence).last().message.text)
            assertEquals("partial reply", fixture.database.messageDao().forTurn(second.id, secondTurn.sequence).last().message.text)
            assertFalse(service.conversation(second.id).first().toolProgressActive)
            assertTrue(fixture.database.conversationDao().get(first.id)!!.unread)
            assertTrue(fixture.database.conversationDao().get(second.id)!!.unread)
        } finally {
            finishSecond.complete(Unit)
            fixture.close()
        }
    }

    @Test
    fun staleAttemptEventsAreIgnoredBeforeUpdatingTheConversation() = runBlocking {
        val fixture = Fixture(context)
        try {
            fixture.initializeTestState()
            val main = fixture.database.conversationDao().getMain()!!
            val service = fixture.service(
                runtime = AgentRuntime { request ->
                    flow {
                        val oldAttempt = "${request.attemptId}-old"
                        emit(AgentEvent.TextDelta(request.executionId, oldAttempt, "stale output"))
                        emit(AgentEvent.Finished(request.executionId, oldAttempt, AgentTerminalStatus.FAILED))
                        emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "current output"))
                        emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)

            service.send(main.id, "question", null)
            fixture.awaitStatus(main.id, "COMPLETED")

            val messages = fixture.database.messageDao().forTurn(main.id, 1).map { it.message.text }
            assertEquals(listOf("question", "current output"), messages)
            assertFalse(messages.any { it.contains("stale") })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rejectedModelResponsesWithUnauthorizedStatusBecomeAuthenticationIssues() = runBlocking {
        val fixture = Fixture(context)
        val calls = AtomicInteger()
        val statuses = listOf(401, 403)
        try {
            fixture.initializeTestState()
            val service = fixture.service(
                runtime = AgentRuntime { _ ->
                    flow {
                        val status = statuses[calls.getAndIncrement()]
                        throw ModelRequestException.ApiRejected("unauthorized", "Request rejected", status)
                    }
                },
            )
            fixture.saveServiceAndSelectModel(service)
            val mainSession = fixture.database.conversationDao().getMain()!!
            val dedicatedSession = fixture.createSession(fixture.personaId(0))

            listOf(mainSession.id, dedicatedSession.id).forEachIndexed { index, sessionId ->
                service.send(sessionId, "question $index", null)
                fixture.awaitStatus(sessionId, "FAILED")
                assertEquals(UiIssue.Authentication, service.conversation(sessionId).first().issue)
                assertEquals(
                    AgentFailureKind.AUTHENTICATION.name,
                    fixture.database.turnDao().latest(sessionId)?.failureKind,
                )
            }
        } finally {
            fixture.close()
        }
    }

    private class Fixture(context: Context, private val persistentDatabaseName: String? = null) {
        private val appContext = context.applicationContext
        val database = if (persistentDatabaseName == null) {
            Room.inMemoryDatabaseBuilder(appContext, ElymBotDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        } else {
            Room.databaseBuilder(appContext, ElymBotDatabase::class.java, persistentDatabaseName)
                .allowMainThreadQueries()
                .build()
        }
        private val mediaRoot = File(appContext.cacheDir, "elymbot-storage-test-${UUID.randomUUID()}")
        val assets = PrivateAssetStore(appContext, root = mediaRoot)
        val repository = ElymBotDataRepository(appContext, database, assets)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var databaseClosed = false

        fun personaId(index: Int) = "fixture-persona-$index"

        suspend fun initializeTestState() {
            repository.initialize()
            database.withTransaction {
                val personas = database.personaDao()
                if (personas.count() == 0) {
                    personas.insertAll((0 until TEST_PERSONA_COUNT).map(::testPersona))
                }
                val available = personas.getAll()
                val main = database.conversationDao().getMain()
                if (main == null) {
                    val activePersona = available.first()
                    val timestamp = System.currentTimeMillis()
                    val checkpointId = "empty:main"
                    database.conversationDao().insert(
                        ConversationEntity(
                            id = "main",
                            kind = "MAIN",
                            personaId = activePersona.id,
                            title = null,
                            mainKey = "main",
                            currentCheckpointId = checkpointId,
                            nextTurnSequence = 1,
                            unread = false,
                            createdAt = timestamp,
                            lastActivityAt = timestamp,
                        ),
                    )
                    database.checkpointDao().insert(
                        CheckpointEntity(checkpointId, "main", 1, 0, 1, timestamp),
                    )
                }
            }
        }

        private fun testPersona(index: Int) = PersonaEntity(
            id = personaId(index),
            name = "Fixture persona $index",
            description = "Test fixture persona",
            tagsValue = "test",
            prompt = "fixture-$index",
            favorite = false,
            coverPath = null,
            atlasTile = 0,
            coverX = 0.5f,
            coverY = 0.5f,
            avatarX = 0.5f,
            avatarY = 0.5f,
            sortOrder = index,
        )

        fun service(
            repository: ElymBotDataRepository = this.repository,
            parser: SlashCommandParser = SlashCommandParser { _, _ -> CommandParseResult.NotCommand },
            runtime: AgentRuntime = AgentRuntime { request ->
                flow {
                    emit(AgentEvent.TextDelta(request.executionId, request.attemptId, "reply"))
                    emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                }
            },
            connectionProvider: RoomModelServiceConnectionProvider = RoomModelServiceConnectionProvider(repository),
            modelGateway: ModelGateway = FakeModelGateway,
            scope: CoroutineScope = this.scope,
        ) = ElymBotSessionService(
            context = appContext,
            repository = repository,
            connectionProvider = connectionProvider,
            modelGateway = modelGateway,
            agentRuntime = runtime,
            commandParser = parser,
            commandText = FakeCommandText,
            scope = scope,
        )

        suspend fun createSession(personaId: String): ConversationEntity {
            val id = UUID.randomUUID().toString()
            val checkpointId = "empty:$id"
            val now = System.currentTimeMillis()
            val session = ConversationEntity(id, "DEDICATED", personaId, null, null, checkpointId, 1, false, now, now)
            database.withTransaction {
                database.conversationDao().insert(session)
                database.checkpointDao().insert(CheckpointEntity(checkpointId, id, 1, 0, 1, now))
            }
            return session
        }

        suspend fun insertTurn(
            sessionId: String,
            sequence: Long,
            userText: String,
            assistantText: String,
            attachmentPath: String? = null,
        ) {
            database.withTransaction {
                val session = database.conversationDao().get(sessionId)!!
                val persona = database.personaDao().get(session.personaId)!!
                val userId = "user-$sessionId-$sequence"
                database.turnDao().insert(
                    TurnEntity(
                        sessionId = sessionId,
                        sequence = sequence,
                        executionId = "execution-$sessionId-$sequence",
                        attemptId = "attempt-$sessionId-$sequence",
                        status = "COMPLETED",
                        startedAt = sequence,
                        finishedAt = sequence,
                        personaId = persona.id,
                        personaName = persona.name,
                        personaPrompt = persona.prompt,
                        primaryServiceId = null,
                        primaryModelId = null,
                        visionServiceId = null,
                        visionModelId = null,
                        failureKind = null,
                        retryable = false,
                    ),
                )
                database.messageDao().insertAll(
                    listOf(
                        MessageEntity(userId, sessionId, sequence, 0, "USER", userText, "HISTORY", null, null),
                        MessageEntity("assistant-$sessionId-$sequence", sessionId, sequence, 1, "ASSISTANT", assistantText, "HISTORY", null, persona.name),
                    ),
                )
                if (attachmentPath != null) {
                    database.attachmentDao().insert(
                        AttachmentEntity("asset-$sessionId-$sequence", userId, attachmentPath, "image/png", 4),
                    )
                }
                database.conversationDao().update(session.copy(nextTurnSequence = maxOf(session.nextTurnSequence, sequence + 1)))
            }
        }

        suspend fun awaitStatus(sessionId: String, status: String) = withTimeout(10_000) {
            while (database.turnDao().latest(sessionId)?.status != status) delay(10)
        }

        suspend fun saveServiceAndSelectModel(service: ElymBotSessionService): String {
            service.saveModelService(
                ModelServiceDraft(
                    provider = "compatible",
                    name = "Test service",
                    baseUrl = "https://model.example/v1",
                    apiKey = "test-key",
                    modelId = "model-a",
                ),
            )
            val id = database.modelConfigurationDao().getServices().single().id
            service.setModel(UiModelRole.Text, ModelChoice(id, "model-a"))
            return id
        }

        fun createImageContentUri(): Uri {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            val pngBytes = try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "elymbot-title-${UUID.randomUUID()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/ElymBotTest",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create test image")
            try {
                val output = appContext.contentResolver.openOutputStream(uri, "w")
                    ?: error("Unable to write test image")
                output.use { it.write(pngBytes) }
                appContext.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (failure: Throwable) {
                appContext.contentResolver.delete(uri, null, null)
                throw failure
            }
            return uri
        }

        fun close() {
            scope.cancel()
            closeDatabase()
            mediaRoot.deleteRecursively()
        }

        fun closeDatabase() {
            if (!databaseClosed) {
                database.close()
                databaseClosed = true
            }
        }

        private companion object {
            const val TEST_PERSONA_COUNT = 3
        }
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        private val pending = java.util.ArrayDeque<Runnable>()

        val pendingCount: Int
            get() = synchronized(pending) { pending.size }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(pending) { pending.addLast(block) }
        }

        fun runPending() {
            while (true) {
                val next = synchronized(pending) { if (pending.isEmpty()) null else pending.removeFirst() } ?: return
                next.run()
            }
        }
    }

    private class ModelListServer : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val authorization = CompletableDeferred<String?>()
        val baseUrl = "http://localhost:${server.localPort}/v1"
        private val worker = Thread(::serve).apply {
            name = "elymbot-storage-model-list"
            isDaemon = true
            start()
        }

        suspend fun awaitAuthorization(): String? = withTimeout(10_000) { authorization.await() }

        private fun serve() {
            try {
                server.accept().use { socket ->
                    val reader = InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1).buffered()
                    reader.readLine() ?: error("Missing model-list request line")
                    var auth: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0 && line.substring(0, separator).equals("Authorization", ignoreCase = true)) {
                            auth = line.substring(separator + 1).trim()
                        }
                    }
                    val responseBody = "{\"data\":[{\"id\":\"model-a\"}]}".toByteArray(Charsets.UTF_8)
                    val output = socket.getOutputStream()
                    output.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${responseBody.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1),
                    )
                    output.write(responseBody)
                    output.flush()
                    authorization.complete(auth)
                }
            } catch (failure: Throwable) {
                if (!authorization.isCompleted) authorization.completeExceptionally(failure)
            }
        }

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }

    private object FakeModelGateway : ModelGateway {
        override suspend fun listModels(serviceId: String): List<String> = listOf("model-a")

        override suspend fun probe(model: ModelReference) = ModelCapabilityReport(
            model = model,
            availability = CapabilityStatus.SUPPORTED,
            vision = CapabilityStatus.UNKNOWN,
            nativeStreaming = CapabilityStatus.SUPPORTED,
            structuredToolCalls = CapabilityStatus.UNKNOWN,
        )

        override fun stream(request: ModelRequest): Flow<ModelStreamEvent> = flow {
            emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
        }
    }

    private object FakeCommandText : CommandTextProvider {
        override fun help(mainSession: Boolean, names: CommandNames) = "help"
        override fun currentMarker() = " current"
        override fun personaEntry(number: Int, name: String, current: Boolean) = "$number · $name"
        override fun personaSwitched(name: String) = "persona $name"
        override fun checkpointEntry(number: Int, current: Boolean) = "$number"
        override fun checkpointMarked(number: Int) = "marked $number"
        override fun checkpointRestored(number: Int) = "restored $number"
        override fun noCheckpoints() = "empty"
        override fun noMarkBoundary() = "no boundary"
        override fun invalidCommand(helpName: String) = "invalid $helpName"
    }
}
