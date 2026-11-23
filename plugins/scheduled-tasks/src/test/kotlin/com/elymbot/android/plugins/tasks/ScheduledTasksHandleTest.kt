package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.AgentMessage
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ScheduledTaskAccessContext
import com.elymbot.core.contract.ScheduledTaskAccessOrigin
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskDraft
import com.elymbot.core.contract.ScheduledTaskEndCondition
import com.elymbot.core.contract.ScheduledTaskRejection
import com.elymbot.core.contract.ScheduledTaskRepeatRule
import com.elymbot.core.contract.ScheduledTaskRunStatus
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolResultStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduledTasksHandleTest {
    private val identity = ToolInstallationIdentity("scheduled-tasks", "active-instance")
    private val now = Instant.parse("2026-11-23T10:00:00Z")

    @Test
    fun agentWritesWaitForMatchingAppMainConfirmationAndUseSharedRepository() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val handle = handle(repository)
        val initial = draft("Review the day")

        val pending = handle.requestAction(
            agentAccess(),
            requestId = "execution-1:call-1",
            action = ScheduledTaskAction.Create(initial),
        ) as ScheduledTaskActionResult.ConfirmationRequired

        assertTrue(repository.snapshot().tasks.isEmpty())
        val wrongUser = handle.confirmAction(
            access = uiAccess(userId = "other-user"),
            pending = pending,
            approved = true,
        )
        assertEquals(ScheduledTaskRejection.UNAUTHORIZED, (wrongUser as ScheduledTaskActionResult.Rejected).reason)
        assertTrue(repository.snapshot().tasks.isEmpty())

        val applied = handle.confirmAction(uiAccess(), pending, approved = true)
        val created = (applied as ScheduledTaskActionResult.Saved).task
        assertEquals("Review", created.title)
        assertEquals(1, repository.snapshot().tasks.size)
        assertEquals(TaskCreationSource.AGENT, repository.snapshot().tasks.getValue(created.id).creationSource)

        val repeated = handle.confirmAction(uiAccess(), pending, approved = true)
        assertEquals(ScheduledTaskRejection.CONFIRMATION_EXPIRED, (repeated as ScheduledTaskActionResult.Rejected).reason)
        assertEquals(1, repository.snapshot().tasks.size)
    }

    @Test
    fun approvedModelCreateReplayReturnsPersistedMutationResult() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val handle = handle(repository)
        val access = agentAccess(executionId = "execution-create")
        val requestId = "execution-create:call-1"
        val action = ScheduledTaskAction.Create(draft("Daily briefing"))
        val pending = handle.requestAction(access, requestId, action)
            as ScheduledTaskActionResult.ConfirmationRequired

        val first = handle.confirmAction(uiAccess(), pending, approved = true)
            as ScheduledTaskActionResult.Saved
        val replay = handle.requestAction(access, requestId, action)

        assertTrue(replay is ScheduledTaskActionResult.Saved)
        assertEquals(first.task.id, (replay as ScheduledTaskActionResult.Saved).task.id)
        assertEquals(1, repository.snapshot().tasks.size)
        val conflict = handle.requestAction(
            access,
            requestId,
            ScheduledTaskAction.Create(draft("Different request body")),
        ) as ScheduledTaskActionResult.Rejected
        assertEquals(ScheduledTaskRejection.IDEMPOTENCY_CONFLICT, conflict.reason)
    }

    @Test
    fun approvedModelRunNowReplayReturnsTheSameQueuedRun() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val handle = handle(repository)
        val task = (handle.requestAction(
            uiAccess(),
            requestId = "create-run-now-target",
            action = ScheduledTaskAction.Create(draft("Check the latest notes")),
        ) as ScheduledTaskActionResult.Saved).task
        val access = agentAccess(executionId = "execution-run-now")
        val requestId = "execution-run-now:call-1"
        val action = ScheduledTaskAction.RunNow(task.id, task.revision)
        val pending = handle.requestAction(access, requestId, action)
            as ScheduledTaskActionResult.ConfirmationRequired

        val first = handle.confirmAction(uiAccess(), pending, approved = true)
            as ScheduledTaskActionResult.RunQueued
        val replay = handle.requestAction(access, requestId, action)
            as ScheduledTaskActionResult.RunQueued

        assertEquals(first.run.runId, replay.run.runId)
        assertEquals(1, repository.snapshot().runs.size)
    }

    @Test
    fun refusalAndExpiredConfirmationNeverWrite() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val clock = MutableClock(now)
        val handle = handle(repository, clock)
        val pending = handle.requestAction(
            agentAccess(),
            requestId = "execution-2:call-1",
            action = ScheduledTaskAction.Create(draft("Do not save")),
        ) as ScheduledTaskActionResult.ConfirmationRequired

        val declined = handle.confirmAction(uiAccess(), pending, approved = false)
        assertEquals(ScheduledTaskRejection.CONFIRMATION_DECLINED, (declined as ScheduledTaskActionResult.Rejected).reason)
        assertTrue(repository.snapshot().tasks.isEmpty())

        val another = handle.requestAction(
            agentAccess(executionId = "execution-3"),
            requestId = "execution-3:call-1",
            action = ScheduledTaskAction.Create(draft("Expire")),
        ) as ScheduledTaskActionResult.ConfirmationRequired
        clock.current = another.expiresAt
        val expired = handle.confirmAction(uiAccess(), another, approved = true)

        assertEquals(ScheduledTaskRejection.CONFIRMATION_EXPIRED, (expired as ScheduledTaskActionResult.Rejected).reason)
        assertTrue(repository.snapshot().tasks.isEmpty())
    }

    @Test
    fun staleRevisionCannotApplyAndPausedTaskStillSupportsImmediateRun() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val handle = handle(repository)
        val created = handle.requestAction(
            uiAccess(),
            requestId = "create-1",
            action = ScheduledTaskAction.Create(draft("Prepare a note")),
        ) as ScheduledTaskActionResult.Saved
        val task = created.task

        val paused = handle.requestAction(
            uiAccess(),
            requestId = "pause-1",
            action = ScheduledTaskAction.Pause(task.id, task.revision),
        ) as ScheduledTaskActionResult.Saved
        assertFalse(paused.task.enabled)

        val staleUpdate = handle.requestAction(
            uiAccess(),
            requestId = "stale-update",
            action = ScheduledTaskAction.Update(task.id, task.revision, draft("Stale edit")),
        ) as ScheduledTaskActionResult.Rejected
        assertEquals(ScheduledTaskRejection.REVISION_CONFLICT, staleUpdate.reason)

        val run = handle.requestAction(
            uiAccess(),
            requestId = "manual-run-1",
            action = ScheduledTaskAction.RunNow(paused.task.id, paused.task.revision),
        ) as ScheduledTaskActionResult.RunQueued
        assertNull(run.run.plannedAt)
        assertNull(run.run.occurrenceId)
        assertEquals(paused.task.startLocalDateTime, repository.snapshot().tasks.getValue(task.id).startLocalDateTime)

        val scheduler = ScheduledTaskScheduler(repository, identity)
        val pendingRuns = scheduler.collectPendingRuns(now, identity)
        assertEquals(1, pendingRuns.size)
        assertEquals(run.run.runId, pendingRuns.single().runId)
        val claimed = scheduler.recordRunReceipt(
            identity,
            com.elymbot.core.contract.ScheduledTaskRunReceipt(
                taskId = run.run.taskId,
                runId = run.run.runId,
                occurrenceId = null,
                status = ScheduledTaskRunStatus.RUNNING,
                updatedAt = now,
            ),
        )
        assertEquals(ScheduledTaskRunStatus.RUNNING, claimed?.status)
    }

    @Test
    fun sharedMainSessionFromQQCannotQueryTasksEvenWithMainScope() = runBlocking {
        val handle = handle(InMemoryScheduledTaskRepository())
        val source = handle.toolSources.single()
        val result = source.execute(
            call = ModelToolCall("call-qq", source.definition.name, "{\"action\":\"list\"}"),
            context = toolContext(channel = "qq", actor = "qq-user"),
            onProgress = {},
        )

        assertEquals(ToolResultStatus.REJECTED, result.status)
    }

    @Test
    fun appMainToolCanListAndModelCreateOnlyReturnsPendingConfirmation() = runBlocking {
        val repository = InMemoryScheduledTaskRepository()
        val handle = handle(repository)
        val source = handle.toolSources.single()
        val list = source.execute(
            call = ModelToolCall("call-list", source.definition.name, "{\"action\":\"list\"}"),
            context = toolContext(channel = "app", actor = "local-user"),
            onProgress = {},
        )
        val createJson = """
            {
              "action":"create",
              "title":"Review",
              "prompt":"Review the day",
              "startLocalDateTime":"2026-11-23T11:00",
              "timeZoneId":"UTC",
              "repeatRule":{"kind":"ONCE"},
              "endCondition":{"kind":"NEVER"}
            }
        """.trimIndent()
        val create = source.execute(
            call = ModelToolCall("call-create", source.definition.name, createJson),
            context = toolContext(channel = "app", actor = "local-user"),
            onProgress = {},
        )

        assertEquals(ToolResultStatus.SUCCESS, list.status)
        assertEquals(ToolResultStatus.SUCCESS, create.status)
        assertTrue(create.content.contains("PENDING_CONFIRMATION"))
        assertTrue(repository.snapshot().tasks.isEmpty())
    }

    @Test
    fun unauthorizedAppRouteAndInactiveInstallationAreRejected() = runBlocking {
        val handle = handle(InMemoryScheduledTaskRepository())
        val nonMain = handle.snapshot(
            access = uiAccess().copy(sessionScope = ToolScope.PERSONA_SESSION),
        )
        val wrongInstallation = handle.snapshot(
            access = uiAccess().copy(installationIdentity = ToolInstallationIdentity("scheduled-tasks", "old")),
        )

        assertEquals(ScheduledTaskRejection.UNAUTHORIZED, (nonMain as ScheduledTaskSnapshotResult.Rejected).reason)
        assertEquals(ScheduledTaskRejection.INACTIVE_INSTALLATION, (wrongInstallation as ScheduledTaskSnapshotResult.Rejected).reason)
    }

    private fun handle(
        repository: ScheduledTaskRepository,
        clock: Clock = Clock.fixed(now, ZoneId.of("UTC")),
    ) = ScheduledTasksPluginHandleImpl(repository, identity, clock)

    private fun draft(prompt: String) = ScheduledTaskDraft(
        title = "Review",
        prompt = prompt,
        startLocalDateTime = LocalDateTime.parse("2026-11-23T11:00"),
        timeZoneId = "UTC",
        repeatRule = ScheduledTaskRepeatRule.Once,
        endCondition = ScheduledTaskEndCondition.Never,
    )

    private fun uiAccess(userId: String = "local-user") = ScheduledTaskAccessContext(
        origin = ScheduledTaskAccessOrigin.APP_UI_MAIN,
        sessionId = "primary-session",
        userId = userId,
        sessionScope = ToolScope.MAIN_SESSION,
        installationIdentity = identity,
    )

    private fun agentAccess(executionId: String = "execution-1") = ScheduledTaskAccessContext(
        origin = ScheduledTaskAccessOrigin.APP_AGENT_MAIN,
        sessionId = "primary-session",
        userId = "local-user",
        sessionScope = ToolScope.MAIN_SESSION,
        installationIdentity = identity,
        executionId = executionId,
    )

    private suspend fun ScheduledTasksPluginHandleImpl.confirmAction(
        access: ScheduledTaskAccessContext,
        pending: ScheduledTaskActionResult.ConfirmationRequired,
        approved: Boolean,
    ) = confirmAction(access, pending.confirmationId, pending.initiatingExecutionId, approved)

    private fun toolContext(channel: String, actor: String): ToolExecutionContext = ToolExecutionContext(
        sessionId = "primary-session",
        turnSequence = 1,
        attemptId = "attempt-1",
        historyUpperBoundExclusive = 2,
        scope = ToolScope.MAIN_SESSION,
        executionId = "execution-1",
        installationIdentity = identity,
        inputMessage = AgentMessage(
            origin = MessageRoute(channelId = channel, conversationId = "conversation", actorId = actor),
            segments = emptyList(),
        ),
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
