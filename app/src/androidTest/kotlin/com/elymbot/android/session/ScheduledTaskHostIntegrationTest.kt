package com.elymbot.android.session

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.ModelAssignmentEntity
import com.elymbot.android.data.ModelServiceEntity
import com.elymbot.android.data.PersonaEntity
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.runtime.pluginpackage.PluginPackageExpectation
import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.android.ui.ModelRole as UiModelRole
import com.elymbot.core.command.DefaultSlashCommandParser
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentRuntime
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.ReplyMessage
import com.elymbot.core.contract.ReplySubmission
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.ScheduledTaskAccessContext
import com.elymbot.core.contract.ScheduledTaskAccessOrigin
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskDraft
import com.elymbot.core.contract.ScheduledTaskEndCondition
import com.elymbot.core.contract.ScheduledTaskRepeatRule
import com.elymbot.core.contract.ScheduledTaskRunStatus
import com.elymbot.core.contract.ScheduledTaskRejection
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ScheduledTaskUiStateResult
import com.elymbot.core.contract.ScheduledTasksPluginHandle
import com.elymbot.core.contract.ScheduledTasksUiHandle
import com.elymbot.core.contract.ToolScope
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledTaskHostIntegrationTest {
    @Test
    fun signedUiCapabilityLoadsAndPreservesGuideChoice() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("t02ScheduleUiProbe") == "true")
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedFiles = File(baseContext.cacheDir, "t02-scheduled-ui-${UUID.randomUUID()}")
        check(isolatedFiles.mkdirs())
        val runtime = PluginRuntimeManager(IsolatedPluginContext(baseContext, isolatedFiles))
        val fixture = File(baseContext.cacheDir, "t02-scheduled-ui-probe")
        val pluginId = "scheduled-tasks"
        val publisherKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(File(fixture, "final-probe-publisher-public.der").readBytes()),
        )
        val expectation = PluginPackageExpectation(
            publisherId = "synthetic.elymbot.tests",
            pluginId = pluginId,
            version = "0.0.2-ui-probe",
            hostApi = "host-api-test-1",
            supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            trustedPublisherKey = publisherKey,
        )

        try {
            runtime.restore { null }
            val installed = runtime.install(
                File(fixture, "scheduled-tasks-ui-probe-final.elymbot-plugin"),
                expectation,
            )
            val access = ScheduledTaskAccessContext(
                origin = ScheduledTaskAccessOrigin.APP_UI_MAIN,
                sessionId = "probe-main",
                userId = "probe-owner",
                sessionScope = ToolScope.MAIN_SESSION,
                installationIdentity = installed.installationIdentity,
            )
            withTimeout(5_000) {
                runtime.withActivePluginHandle(pluginId, access.installationIdentity, ScheduledTasksUiHandle::class.java) {
                    val initial = it.observeMainUiState(access).first() as ScheduledTaskUiStateResult.Visible
                    assertFalse(initial.state.guideSkipped)
                    val skipped = it.skipFirstTaskGuide(access) as ScheduledTaskUiStateResult.Visible
                    assertTrue(skipped.state.guideSkipped)
                } ?: error("Signed DEX did not expose the optional UI capability")

                runtime.disable(pluginId)
                val reenabled = runtime.enable(pluginId, expectation)
                val reactivatedAccess = access.copy(installationIdentity = reenabled.installationIdentity)
                runtime.withActivePluginHandle(pluginId, reenabled.installationIdentity, ScheduledTasksUiHandle::class.java) {
                    val current = it.observeMainUiState(reactivatedAccess).first() as ScheduledTaskUiStateResult.Visible
                    assertTrue(current.state.guideSkipped)
                } ?: error("Reactivated DEX did not expose the UI capability")
            }
        } finally {
            runCatching { runtime.uninstall(pluginId, deleteData = true) }
            isolatedFiles.deleteRecursively()
        }
    }

    @Test
    fun workerWakeWaitsForRepositoryRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assetRoot = File(context.cacheDir, "t02-readiness-media-${UUID.randomUUID()}")
        val assets = PrivateAssetStore(context, root = assetRoot)
        val recoveryStarted = CompletableDeferred<Unit>()
        val releaseRecovery = CompletableDeferred<Unit>()
        val repository = ElymBotDataRepository(
            context = context,
            database = database,
            assets = assets,
            beforeInitialize = {
                recoveryStarted.complete(Unit)
                releaseRecovery.await()
            },
        )
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val service = ElymBotSessionService(
            context = context,
            repository = repository,
            connectionProvider = RoomModelServiceConnectionProvider(repository),
            modelGateway = UnusedModelGateway,
            agentRuntime = AgentRuntime { flow { error("No turn can start before repository readiness.") } },
            commandParser = DefaultSlashCommandParser(),
            scope = serviceScope,
        )
        val wake = async { service.processScheduledTaskWake() }

        try {
            withTimeout(5_000) { recoveryStarted.await() }
            delay(50)
            assertFalse("Worker wake escaped the repository readiness barrier", wake.isCompleted)
            assertTrue("A turn started before recovery completed", database.turnDao().running().isEmpty())

            releaseRecovery.complete(Unit)
            assertFalse(withTimeout(5_000) { wake.await() })
            assertTrue(database.turnDao().running().isEmpty())
        } finally {
            releaseRecovery.complete(Unit)
            serviceScope.cancel()
            database.close()
            assetRoot.deleteRecursively()
        }
    }

    @Test
    fun runNowPersistsOnceAndWaitsForTheBusyMainSession() = runBlocking {
        assumeTrue(
            "Run only when the root has injected the signed W05 probe fixture.",
            InstrumentationRegistry.getArguments().getString("t02ScheduleProbe") == "true",
        )
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedFiles = File(baseContext.cacheDir, "t02-scheduled-host-${UUID.randomUUID()}")
        check(isolatedFiles.mkdirs())
        val context = IsolatedPluginContext(baseContext, isolatedFiles)
        val artifactDirectory = File(baseContext.cacheDir, "t02-scheduled-probe")
        val packageFile = File(artifactDirectory, "scheduled-tasks-probe.elymbot-plugin")
        val publisherKeyFile = File(artifactDirectory, "probe-publisher-public.der")
        val database = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assets = PrivateAssetStore(context, root = File(isolatedFiles, "media"))
        val runtimeManager = PluginRuntimeManager(context)
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val releaseFirstTurn = CompletableDeferred<Unit>()
        val firstTurnEntered = CompletableDeferred<AgentRequest>()
        val scheduledTurnEntered = CompletableDeferred<AgentRequest>()
        val agentRequests = CopyOnWriteArrayList<AgentRequest>()
        var service: ElymBotSessionService? = null

        try {
            assertTrue("Probe package is missing", packageFile.isFile)
            assertTrue("Publisher public key is missing", publisherKeyFile.isFile)
            runtimeManager.restore { null }
            val publisherKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publisherKeyFile.readBytes()))
            val expectation = PluginPackageExpectation(
                publisherId = "synthetic.elymbot.tests",
                pluginId = "scheduled-tasks",
                version = "0.0.1-probe",
                hostApi = "host-api-test-1",
                supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
                trustedPublisherKey = publisherKey,
            )
            runtimeManager.install(packageFile, expectation)

            val repository = ElymBotDataRepository(
                context = context,
                database = database,
                assets = assets,
                beforeInitialize = { runtimeManager.restore { null } },
            )
            val personaId = "scheduled-probe-persona-${UUID.randomUUID()}"
            database.personaDao().insert(
                PersonaEntity(
                    id = personaId,
                    name = "Scheduled probe persona",
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
                ),
            )
            repository.initialize()
            val main = requireNotNull(database.conversationDao().getMain())
            database.modelConfigurationDao().upsertService(
                ModelServiceEntity(
                    id = "scheduled-probe-model",
                    provider = "integration-test",
                    name = "Scheduled probe model",
                    baseUrl = "https://unused.invalid/v1",
                    modelId = "probe-model",
                    catalogValue = "[\"probe-model\"]",
                ),
            )
            database.modelConfigurationDao().upsertAssignment(
                ModelAssignmentEntity(UiModelRole.Text.name, "scheduled-probe-model", "probe-model"),
            )

            val pluginScheduler = RecordingScheduler()
            val host = ScheduledTaskHost(repository, runtimeManager, pluginScheduler)
            val modelGateway = UnusedModelGateway
            val connectionProvider = RoomModelServiceConnectionProvider(repository)
            val runtime = AgentRuntime { request ->
                flow {
                    agentRequests += request
                    val origin = request.inputMessage?.origin
                    if (origin?.channelId == "app") firstTurnEntered.complete(request)
                    if (origin?.channelId == "scheduled-task") scheduledTurnEntered.complete(request)
                    if (origin?.channelId == "app") releaseFirstTurn.await()

                    val accepted = requireNotNull(request.replySubmitter).submit(
                        ReplySubmission(
                            context = ReplySubmissionContext(
                                sessionId = request.sessionId,
                                executionId = request.executionId,
                                attemptId = request.attemptId,
                                turnSequence = request.turnSequence,
                                toolStepOrdinal = 0,
                                toolCallOrdinal = 0,
                                toolCallId = "scheduled-probe-${request.executionId}",
                            ),
                            messages = listOf(ReplyMessage("finished-${request.turnSequence}", 0)),
                        ),
                    )
                    check(accepted is ReplySubmissionResult.Accepted)
                    emit(AgentEvent.Finished(request.executionId, request.attemptId, AgentTerminalStatus.COMPLETED))
                }
            }
            service = ElymBotSessionService(
                context = context,
                repository = repository,
                connectionProvider = connectionProvider,
                modelGateway = modelGateway,
                agentRuntime = runtime,
                commandParser = DefaultSlashCommandParser(),
                scope = serviceScope,
                scheduledTaskHost = host,
                toolRegistrations = runtimeManager::activeRegistrations,
                toolAccessPolicy = AppToolAccessPolicy(runtimeManager),
            )
            val dedicatedSessionId = service.newSession(personaId)
            assertEquals(
                ScheduledTaskSnapshotResult.Rejected(ScheduledTaskRejection.UNAUTHORIZED),
                service.scheduledTasksSnapshot(dedicatedSessionId),
            )
            withTimeout(5_000) { pluginScheduler.startupKeepRecorded.await() }
            assertFalse("Startup must not replace existing WorkManager requests", ScheduledTaskScheduleMode.REPLACE in pluginScheduler.modes)
            val firstKeep = pluginScheduler.modes.indexOf(ScheduledTaskScheduleMode.KEEP)
            assertNull("An empty task store should keep WorkManager without enqueuing a replacement", pluginScheduler.scheduledAt[firstKeep])
            val visible = service.scheduledTasksSnapshot(main.id) as ScheduledTaskSnapshotResult.Visible
            assertTrue(visible.snapshot.tasks.isEmpty())

            val start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
            val created = service.requestScheduledTaskAction(
                sessionId = main.id,
                requestId = "create-probe-task-${UUID.randomUUID()}",
                action = ScheduledTaskAction.Create(
                    ScheduledTaskDraft(
                        title = "Queue integration probe",
                        prompt = "Run from the durable App queue",
                        startLocalDateTime = start,
                        timeZoneId = ZoneId.systemDefault().id,
                        repeatRule = ScheduledTaskRepeatRule.Once,
                        endCondition = ScheduledTaskEndCondition.Never,
                    ),
                ),
            ) as ScheduledTaskActionResult.Saved
            service.send(main.id, "Hold the main session", null)
            withTimeout(10_000) { firstTurnEntered.await() }

            val requestId = "run-probe-${UUID.randomUUID()}"
            val runAction = ScheduledTaskAction.RunNow(created.task.id, created.task.revision)
            val firstQueued = service.requestScheduledTaskAction(main.id, requestId, runAction)
                as ScheduledTaskActionResult.RunQueued
            val replayed = service.requestScheduledTaskAction(main.id, requestId, runAction)
                as ScheduledTaskActionResult.RunQueued
            assertEquals(firstQueued.run.runId, replayed.run.runId)

            val inbox = database.scheduledTaskRunInboxDao().get(firstQueued.run.runId)
            assertNotNull(inbox)
            assertEquals("QUEUED", inbox?.status)
            assertEquals(1, database.scheduledTaskRunInboxDao().queued().size)
            assertEquals("RUNNING", database.turnDao().latest(main.id)?.status)
            assertEquals(1, agentRequests.size)
            assertTrue(service.processScheduledTaskWake())
            assertEquals(ScheduledTaskScheduleMode.APPEND, pluginScheduler.modes.last())
            assertEquals(1, database.scheduledTaskRunInboxDao().queued().size)

            releaseFirstTurn.complete(Unit)
            val scheduledRequest = withTimeout(10_000) { scheduledTurnEntered.await() }
            assertEquals(main.id, scheduledRequest.sessionId)
            assertEquals("scheduled-task", scheduledRequest.inputMessage?.origin?.channelId)
            assertEquals(firstQueued.run.runId, scheduledRequest.inputMessage?.origin?.conversationId)
            assertEquals("scheduled-task", scheduledRequest.inputMessage?.origin?.actorId)

            withTimeout(10_000) {
                while (database.turnDao().latest(main.id)?.sequence != 2L ||
                    database.turnDao().latest(main.id)?.status == "RUNNING"
                ) delay(20)
            }
            val scheduledTurn = database.turnDao().get(main.id, 2L)
            assertEquals("COMPLETED", scheduledTurn?.status)
            val scheduledMessages = database.messageDao().forTurn(main.id, 2L)
            assertEquals(1, scheduledMessages.count { it.message.role == "USER" && it.message.text == created.task.prompt })
            assertEquals(1, scheduledMessages.count { it.message.role == "ASSISTANT" && it.message.text == "finished-2" })
            val completedRuns = (service.scheduledTasksSnapshot(main.id, created.task.id) as ScheduledTaskSnapshotResult.Visible)
                .snapshot.runs
            assertEquals(ScheduledTaskRunStatus.SUCCEEDED, completedRuns.single { it.runId == firstQueued.run.runId }.status)
            assertFalse(database.scheduledTaskRunInboxDao().get(firstQueued.run.runId)?.receiptPending ?: true)
            assertEquals(2, agentRequests.size)

            val originalIdentity = requireNotNull(runtimeManager.statusSnapshot().singleOrNull()).installationIdentity
            val beforeDisable = pluginScheduler.modes.size
            runtimeManager.disable("scheduled-tasks")
            service.refreshScheduledTasksAfterPluginRuntimeChange("scheduled-tasks")
            assertTrue(
                "Disabling the task plugin cancels its registered OS wake",
                (beforeDisable until pluginScheduler.modes.size).any { index ->
                    pluginScheduler.modes[index] == ScheduledTaskScheduleMode.REPLACE &&
                        pluginScheduler.scheduledAt[index] == null
                },
            )

            val enabled = runtimeManager.enable("scheduled-tasks", expectation)
            assertTrue("Reactivation must create a fresh installation identity", originalIdentity != enabled.installationIdentity)
            val beforeEnable = pluginScheduler.modes.size
            service.refreshScheduledTasksAfterPluginRuntimeChange("scheduled-tasks")
            assertTrue(
                "Reactivation re-registers the saved next occurrence",
                (beforeEnable until pluginScheduler.modes.size).any { index ->
                    pluginScheduler.modes[index] == ScheduledTaskScheduleMode.REPLACE &&
                        pluginScheduler.scheduledAt[index] != null
                },
            )

            val enabledIdentity = enabled.installationIdentity
            val pendingAfterEnable = runtimeManager.withActivePluginHandle(
                pluginId = "scheduled-tasks",
                installationIdentity = enabledIdentity,
                handleType = ScheduledTasksPluginHandle::class.java,
            ) { handle ->
                handle.requestAction(
                    access = ScheduledTaskAccessContext(
                        origin = ScheduledTaskAccessOrigin.APP_UI_MAIN,
                        sessionId = main.id,
                        userId = "local-user",
                        sessionScope = ToolScope.MAIN_SESSION,
                        installationIdentity = enabledIdentity,
                    ),
                    requestId = "pending-after-enable-${UUID.randomUUID()}",
                    action = ScheduledTaskAction.RunNow(created.task.id, created.task.revision),
                )
            } as? ScheduledTaskActionResult.RunQueued ?: error("Enabled task plugin did not queue the probe run")
            service.refreshScheduledTasksAfterPluginRuntimeChange("scheduled-tasks")
            withTimeout(10_000) {
                while (database.turnDao().latest(main.id)?.sequence != 3L ||
                    database.turnDao().latest(main.id)?.status == "RUNNING"
                ) delay(20)
            }
            assertEquals(3, agentRequests.size)
            assertEquals(
                "SUCCEEDED",
                database.scheduledTaskRunInboxDao().get(pendingAfterEnable.run.runId)?.status,
            )
        } finally {
            releaseFirstTurn.complete(Unit)
            serviceScope.cancel()
            runCatching { runtimeManager.uninstall("scheduled-tasks", deleteData = true) }
            database.close()
            isolatedFiles.deleteRecursively()
        }
    }

    private class IsolatedPluginContext(base: Context, private val isolatedFiles: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = isolatedFiles
    }

    private class RecordingScheduler : ScheduledTaskWakeScheduler {
        val scheduledAt = CopyOnWriteArrayList<Instant?>()
        val modes = CopyOnWriteArrayList<ScheduledTaskScheduleMode>()
        val startupKeepRecorded = CompletableDeferred<Unit>()

        override fun schedule(nextWakeAt: Instant?, mode: ScheduledTaskScheduleMode) {
            scheduledAt += nextWakeAt
            modes += mode
            if (mode == ScheduledTaskScheduleMode.KEEP) startupKeepRecorded.complete(Unit)
        }
    }

    private object UnusedModelGateway : ModelGateway {
        override suspend fun listModels(serviceId: String): List<String> = listOf("probe-model")

        override suspend fun probe(model: ModelReference) = ModelCapabilityReport(
            model = model,
            availability = CapabilityStatus.SUPPORTED,
            vision = CapabilityStatus.UNSUPPORTED,
            nativeStreaming = CapabilityStatus.UNSUPPORTED,
            structuredToolCalls = CapabilityStatus.SUPPORTED,
        )

        override fun stream(request: ModelRequest): Flow<ModelStreamEvent> =
            error("The integration runtime does not call the model gateway.")
    }
}
