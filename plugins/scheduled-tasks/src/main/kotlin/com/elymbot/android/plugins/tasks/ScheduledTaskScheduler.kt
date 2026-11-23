package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ScheduledTaskRunFailureKind as ApiRunFailureKind
import com.elymbot.core.contract.ScheduledTaskRunReceipt as ApiRunReceipt
import com.elymbot.core.contract.ScheduledTaskRunRecord as ApiRunRecord
import com.elymbot.core.contract.ScheduledTaskRunStatus as ApiRunStatus
import java.time.Duration
import java.time.Instant

/** The App owns OS alarms and Agent execution; this class only records and returns work. */
internal class ScheduledTaskScheduler(
    private val repository: ScheduledTaskRepository,
    private val installationIdentity: ToolInstallationIdentity,
) {
    fun nextWakeAt(now: Instant, activeIdentity: ToolInstallationIdentity): Instant? {
        if (activeIdentity != installationIdentity) return null
        val state = repository.snapshot()
        if (state.runs.values.any { it.status == TaskRunStatus.QUEUED &&
                it.installationIdentity == activeIdentity
            }
        ) {
            return now
        }

        return state.tasks.values.asSequence()
            .filter(TaskRecord::enabled)
            .mapNotNull { task ->
                val count = scheduledExecutionCount(state, task.id, activeIdentity)
                if (task.endCondition.kind == TaskEndKind.COUNT &&
                    count >= requireNotNull(task.endCondition.occurrenceCount)
                ) {
                    return@mapNotNull null
                }
                val unprocessedDue = ScheduleCalculator.latestOccurrenceAtOrBefore(task, now)
                    ?.takeIf { occurrence ->
                        state.runs.values.none { run -> run.occurrenceId == occurrence.occurrenceId }
                    }
                if (unprocessedDue != null) {
                    now
                } else {
                    ScheduleCalculator.nextOccurrenceAfter(task, now, count)?.plannedAt
                }
            }
            .minOrNull()
    }

    fun collectPendingRuns(
        now: Instant,
        activeIdentity: ToolInstallationIdentity,
    ): List<TaskRunRequest> {
        if (activeIdentity != installationIdentity) return emptyList()

        return repository.transact { currentState ->
            val tasks = currentState.tasks
            val runs = currentState.runs.toMutableMap()

            runs.values.toList().forEach { run ->
                if (run.status !in setOf(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING)) return@forEach
                when {
                    run.installationIdentity != activeIdentity -> {
                        runs[run.runId] = run.copy(
                            status = TaskRunStatus.STALE,
                            updatedAt = now,
                            failureKind = TaskRunFailureKind.PLUGIN_REPLACED,
                        )
                    }
                    else -> {
                        val task = tasks[run.taskId]
                        val isScheduleStale = task == null ||
                            run.scheduleRevision != task.revision ||
                            run.origin == TaskRunOrigin.SCHEDULED && !task.enabled
                        if (isScheduleStale) {
                            runs[run.runId] = run.copy(
                                status = TaskRunStatus.CANCELLED,
                                updatedAt = now,
                                failureKind = if (task == null) {
                                    TaskRunFailureKind.TASK_CHANGED
                                } else if (!task.enabled) {
                                    TaskRunFailureKind.TASK_PAUSED
                                } else {
                                    TaskRunFailureKind.TASK_CHANGED
                                },
                            )
                        }
                    }
                }
            }

            tasks.values.filter(TaskRecord::enabled).forEach { task ->
                val currentRuns = runs.values.filter { it.taskId == task.id }
                var executionCount = scheduledExecutionCount(currentRuns)
                var cursor = currentRuns.asSequence()
                    .filter { it.origin == TaskRunOrigin.SCHEDULED && it.scheduleRevision == task.revision }
                    .mapNotNull(TaskRunRecord::plannedAt)
                    .maxOrNull()
                    ?: task.eligibleFrom.minusNanos(1)

                val knownOccurrenceIds = currentRuns.asSequence()
                    .filter { it.scheduleRevision == task.revision }
                    .mapNotNull(TaskRunRecord::occurrenceId)
                    .toMutableSet()
                val mostRecentDue = ScheduleCalculator.latestOccurrenceAtOrBefore(task, now)
                while (true) {
                    val candidate = ScheduleCalculator.nextOccurrenceAfter(task, cursor, executionCount)
                        ?: break
                    if (candidate.plannedAt > now) break
                    if (candidate.occurrenceId !in knownOccurrenceIds) {
                        val latestWithinRecoveryWindow = candidate.occurrenceId == mostRecentDue?.occurrenceId &&
                            Duration.between(candidate.plannedAt, now) <= RECOVERY_WINDOW
                        val status = if (latestWithinRecoveryWindow) {
                            TaskRunStatus.QUEUED
                        } else {
                            TaskRunStatus.MISSED
                        }
                        runs[candidate.runId] = TaskRunRecord(
                            taskId = task.id,
                            runId = candidate.runId,
                            occurrenceId = candidate.occurrenceId,
                            scheduleRevision = task.revision,
                            plannedAt = candidate.plannedAt,
                            triggeredAt = now,
                            origin = TaskRunOrigin.SCHEDULED,
                            installationIdentity = activeIdentity,
                            requestId = null,
                            status = status,
                            updatedAt = now,
                        )
                        knownOccurrenceIds += candidate.occurrenceId
                        if (status == TaskRunStatus.QUEUED) executionCount++
                    }
                    cursor = candidate.plannedAt
                    if (task.repeatRule.kind == TaskRepeatKind.ONCE) break
                }
            }

            val updated = TaskStoreState(
                tasks = tasks,
                runs = runs,
                mutationReceipts = currentState.mutationReceipts,
            )
            val activeQueued = runs.values.asSequence()
                .filter { it.status == TaskRunStatus.QUEUED && it.installationIdentity == activeIdentity }
                .filter { run ->
                    val task = tasks[run.taskId]
                    task != null && run.scheduleRevision == task.revision &&
                        (run.origin == TaskRunOrigin.MANUAL || task.enabled)
                }
                .mapNotNull { run ->
                    val task = tasks[run.taskId] ?: return@mapNotNull null
                    TaskRunRequest(
                        taskId = task.id,
                        runId = run.runId,
                        occurrenceId = run.occurrenceId,
                        scheduleRevision = run.scheduleRevision,
                        plannedAt = run.plannedAt,
                        triggeredAt = run.triggeredAt,
                        title = task.title,
                        prompt = task.prompt,
                        timeZoneId = task.timeZoneId,
                        origin = run.origin,
                        installationIdentity = run.installationIdentity,
                    )
                }
                .sortedWith(compareBy(TaskRunRequest::triggeredAt, TaskRunRequest::runId))
                .toList()
            updated to activeQueued
        }
    }

    fun recordRunReceipt(
        activeIdentity: ToolInstallationIdentity,
        receipt: ApiRunReceipt,
    ): ApiRunRecord? {
        if (activeIdentity != installationIdentity || receipt.status == ApiRunStatus.MISSED ||
            receipt.status == ApiRunStatus.STALE
        ) {
            return null
        }

        return repository.transact { state ->
            val run = state.runs[receipt.runId] ?: return@transact state to null
            if (run.taskId != receipt.taskId || run.occurrenceId != receipt.occurrenceId) {
                return@transact state to null
            }
            if (run.installationIdentity != activeIdentity) return@transact state to null
            val task = state.tasks[run.taskId]
            val status = receipt.status.toDomain()
            val nextRun = when (status) {
                TaskRunStatus.QUEUED -> run
                TaskRunStatus.RUNNING -> {
                    if (run.status != TaskRunStatus.QUEUED) return@transact state to null
                    if (task == null || task.revision != run.scheduleRevision ||
                        run.origin == TaskRunOrigin.SCHEDULED && !task.enabled
                    ) {
                        run.copy(
                            status = TaskRunStatus.CANCELLED,
                            updatedAt = receipt.updatedAt,
                            failureKind = if (task == null || task.revision != run.scheduleRevision) {
                                TaskRunFailureKind.TASK_CHANGED
                            } else {
                                TaskRunFailureKind.TASK_PAUSED
                            },
                        )
                    } else {
                        run.copy(status = TaskRunStatus.RUNNING, updatedAt = receipt.updatedAt, failureKind = null)
                    }
                }
                TaskRunStatus.SUCCEEDED,
                TaskRunStatus.FAILED,
                TaskRunStatus.CANCELLED -> {
                    if (run.status !in setOf(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING)) {
                        return@transact state to run.toApi()
                    }
                    run.copy(
                        status = status,
                        updatedAt = receipt.updatedAt,
                        failureKind = receipt.failureKind?.toDomain(),
                    )
                }
                TaskRunStatus.MISSED,
                TaskRunStatus.STALE -> return@transact state to null
            }
            val nextState = state.copy(runs = state.runs + (run.runId to nextRun))
            nextState to nextRun.toApi()
        }
    }

    private fun scheduledExecutionCount(
        state: TaskStoreState,
        taskId: String,
        activeIdentity: ToolInstallationIdentity,
    ): Int = state.runs.values.count { run ->
        run.taskId == taskId && run.origin == TaskRunOrigin.SCHEDULED &&
            run.status != TaskRunStatus.MISSED && run.status != TaskRunStatus.STALE &&
            (run.status != TaskRunStatus.QUEUED || run.installationIdentity == activeIdentity)
    }

    private fun scheduledExecutionCount(runs: Collection<TaskRunRecord>): Int =
        runs.count { run -> run.origin == TaskRunOrigin.SCHEDULED && run.status != TaskRunStatus.MISSED &&
            run.status != TaskRunStatus.STALE
        }

    private fun ApiRunStatus.toDomain(): TaskRunStatus = TaskRunStatus.valueOf(name)

    private fun ApiRunFailureKind.toDomain(): TaskRunFailureKind = TaskRunFailureKind.valueOf(name)

    private fun TaskRunRecord.toApi(): ApiRunRecord = ApiRunRecord(
        taskId = taskId,
        runId = runId,
        occurrenceId = occurrenceId,
        scheduleRevision = scheduleRevision,
        plannedAt = plannedAt,
        triggeredAt = triggeredAt,
        origin = com.elymbot.core.contract.ScheduledTaskRunOrigin.valueOf(origin.name),
        installationIdentity = installationIdentity,
        status = ApiRunStatus.valueOf(status.name),
        updatedAt = updatedAt,
        failureKind = failureKind?.let { ApiRunFailureKind.valueOf(it.name) },
    )

    private companion object {
        val RECOVERY_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
