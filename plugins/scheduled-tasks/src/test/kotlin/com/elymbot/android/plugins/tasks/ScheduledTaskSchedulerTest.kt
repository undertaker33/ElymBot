package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ScheduledTaskRunReceipt
import com.elymbot.core.contract.ScheduledTaskRunStatus
import com.elymbot.core.contract.ToolInstallationIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ScheduledTaskSchedulerTest {
    private val installation = ToolInstallationIdentity("scheduled-tasks", "instance-a")

    @Test
    fun recoveryQueuesOnlyLatestOccurrenceWithinFifteenMinutesAndIsIdempotent() {
        val repository = InMemoryScheduledTaskRepository()
        repository.transact { state -> state.copy(tasks = mapOf("task-1" to task())) to Unit }
        val scheduler = ScheduledTaskScheduler(repository, installation)
        val now = Instant.parse("2026-11-23T10:10:00Z")

        val first = scheduler.collectPendingRuns(now, installation)
        val second = scheduler.collectPendingRuns(now, installation)
        val runs = repository.snapshot().runs.values

        assertEquals(1, first.size)
        assertEquals(first.single().runId, second.single().runId)
        assertEquals(3, runs.size)
        assertEquals(1, runs.count { it.status == TaskRunStatus.QUEUED })
        assertEquals(2, runs.count { it.status == TaskRunStatus.MISSED })
        assertEquals(now, scheduler.nextWakeAt(now, installation))
    }

    @Test
    fun anOccurrenceExactlyFifteenMinutesLateCanRecoverButOneSecondLaterIsMissed() {
        val onBoundary = schedulerAt("2026-11-23T10:15:00Z")
        val overBoundary = schedulerAt("2026-11-23T10:15:01Z")

        assertEquals(1, onBoundary.second.size)
        assertEquals(
            TaskRunStatus.QUEUED,
            onBoundary.first.snapshot().runs.values.single { it.plannedAt == Instant.parse("2026-11-23T10:00:00Z") }.status,
        )
        assertTrue(overBoundary.second.isEmpty())
        assertEquals(
            TaskRunStatus.MISSED,
            overBoundary.first.snapshot().runs.values.single { it.plannedAt == Instant.parse("2026-11-23T10:00:00Z") }.status,
        )
    }

    @Test
    fun installationReplacementDoesNotRestoreOldQueuedRuns() {
        val repository = InMemoryScheduledTaskRepository()
        repository.transact { state -> state.copy(tasks = mapOf("task-1" to task())) to Unit }
        val oldScheduler = ScheduledTaskScheduler(repository, installation)
        val oldQueued = oldScheduler.collectPendingRuns(Instant.parse("2026-11-23T10:05:00Z"), installation)
            .single()
        val newInstallation = ToolInstallationIdentity("scheduled-tasks", "instance-b")
        val newScheduler = ScheduledTaskScheduler(repository, newInstallation)

        val resumed = newScheduler.collectPendingRuns(Instant.parse("2026-11-23T10:06:00Z"), newInstallation)

        assertEquals(oldQueued.runId, repository.snapshot().runs.getValue(oldQueued.runId).runId)
        assertEquals(TaskRunStatus.STALE, repository.snapshot().runs.getValue(oldQueued.runId).status)
        assertTrue(resumed.isEmpty())
    }

    @Test
    fun runningReceiptClaimsQueuedWorkOnlyOnceAndRechecksScheduleRevision() {
        val repository = InMemoryScheduledTaskRepository()
        val task = task().copy(
            startLocalDateTime = LocalDateTime.parse("2026-11-23T10:00"),
            repeatRule = TaskRepeatRule(TaskRepeatKind.ONCE),
        )
        val now = Instant.parse("2026-11-23T10:10:00Z")
        repository.transact { state -> state.copy(tasks = mapOf(task.id to task)) to Unit }
        val scheduler = ScheduledTaskScheduler(repository, installation)
        val request = scheduler.collectPendingRuns(now, installation).single()
        val receipt = ScheduledTaskRunReceipt(
            taskId = request.taskId,
            runId = request.runId,
            occurrenceId = request.occurrenceId,
            status = ScheduledTaskRunStatus.RUNNING,
            updatedAt = now,
        )

        val claimed = scheduler.recordRunReceipt(installation, receipt)
        val duplicate = scheduler.recordRunReceipt(installation, receipt)

        assertEquals(ScheduledTaskRunStatus.RUNNING, claimed?.status)
        assertNull(duplicate)

        repository.transact { state ->
            val current = state.tasks.getValue(task.id)
            state.copy(tasks = state.tasks + (task.id to current.copy(revision = current.revision + 1))) to Unit
        }
        val staleRequest = request.copy(runId = "manual-stale", occurrenceId = "stale", scheduleRevision = task.revision)
        repository.transact { state ->
            val stale = TaskRunRecord(
                taskId = staleRequest.taskId,
                runId = staleRequest.runId,
                occurrenceId = staleRequest.occurrenceId,
                scheduleRevision = staleRequest.scheduleRevision,
                plannedAt = staleRequest.plannedAt,
                triggeredAt = now,
                origin = TaskRunOrigin.SCHEDULED,
                installationIdentity = installation,
                requestId = null,
                status = TaskRunStatus.QUEUED,
                updatedAt = now,
            )
            state.copy(runs = state.runs + (stale.runId to stale)) to Unit
        }
        val staleReceipt = receipt.copy(taskId = staleRequest.taskId, runId = staleRequest.runId, occurrenceId = staleRequest.occurrenceId)

        val rejectedStart = scheduler.recordRunReceipt(installation, staleReceipt)

        assertNotNull(rejectedStart)
        assertEquals(ScheduledTaskRunStatus.CANCELLED, rejectedStart?.status)
    }

    private fun schedulerAt(nowText: String): Pair<InMemoryScheduledTaskRepository, List<TaskRunRequest>> {
        val repository = InMemoryScheduledTaskRepository()
        repository.transact { state -> state.copy(tasks = mapOf("task-1" to task())) to Unit }
        val scheduler = ScheduledTaskScheduler(repository, installation)
        val pending = scheduler.collectPendingRuns(Instant.parse(nowText), installation)
        return repository to pending
    }

    private fun task() = TaskRecord(
        id = "task-1",
        revision = 1,
        title = "Hourly review",
        prompt = "Review recent work",
        startLocalDateTime = LocalDateTime.parse("2026-11-23T08:00"),
        timeZoneId = "UTC",
        repeatRule = TaskRepeatRule(TaskRepeatKind.INTERVAL_HOURS, intervalHours = 1),
        endCondition = TaskEndCondition(TaskEndKind.NEVER),
        enabled = true,
        eligibleFrom = Instant.parse("2026-11-23T07:00:00Z"),
        creationSource = TaskCreationSource.APP,
        createdAt = Instant.parse("2026-11-23T07:00:00Z"),
        updatedAt = Instant.parse("2026-11-23T07:00:00Z"),
    )
}
