package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ToolInstallationIdentity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

internal enum class TaskRepeatKind {
    ONCE,
    DAILY,
    WEEKLY,
    INTERVAL_HOURS,
}

internal data class TaskRepeatRule(
    val kind: TaskRepeatKind,
    val intervalHours: Int? = null,
)

internal enum class TaskEndKind {
    NEVER,
    DATE,
    COUNT,
}

internal data class TaskEndCondition(
    val kind: TaskEndKind,
    val endDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
)

internal data class TaskDraft(
    val title: String,
    val prompt: String,
    val startLocalDateTime: LocalDateTime,
    val timeZoneId: String,
    val repeatRule: TaskRepeatRule,
    val endCondition: TaskEndCondition,
)

internal enum class TaskCreationSource {
    APP,
    AGENT,
}

internal data class TaskRecord(
    val id: String,
    val revision: Long,
    val title: String,
    val prompt: String,
    val startLocalDateTime: LocalDateTime,
    val timeZoneId: String,
    val repeatRule: TaskRepeatRule,
    val endCondition: TaskEndCondition,
    val enabled: Boolean,
    val eligibleFrom: Instant,
    val creationSource: TaskCreationSource,
    val createdAt: Instant,
    val updatedAt: Instant,
)

internal enum class TaskRunOrigin {
    SCHEDULED,
    MANUAL,
}

internal enum class TaskRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    MISSED,
    STALE,
}

internal enum class TaskRunFailureKind {
    HOST_REJECTED,
    AGENT_FAILED,
    USER_CANCELLED,
    TASK_CHANGED,
    TASK_PAUSED,
    PLUGIN_REPLACED,
    UNKNOWN,
}

internal data class TaskRunRecord(
    val taskId: String,
    val runId: String,
    val occurrenceId: String?,
    val scheduleRevision: Long,
    val plannedAt: Instant?,
    val triggeredAt: Instant,
    val origin: TaskRunOrigin,
    val installationIdentity: ToolInstallationIdentity,
    val requestId: String?,
    val status: TaskRunStatus,
    val updatedAt: Instant,
    val failureKind: TaskRunFailureKind? = null,
)

internal enum class MutationResultKind {
    CREATED,
    UPDATED,
    PAUSED,
    RESUMED,
    DELETED,
    RUN_QUEUED,
}

internal data class MutationReceipt(
    val requestId: String,
    val fingerprint: String,
    val resultKind: MutationResultKind,
    val taskId: String,
    val revision: Long? = null,
    val runId: String? = null,
)

internal data class TaskStoreState(
    val tasks: Map<String, TaskRecord> = emptyMap(),
    val runs: Map<String, TaskRunRecord> = emptyMap(),
    val mutationReceipts: Map<String, MutationReceipt> = emptyMap(),
)

internal data class PlannedOccurrence(
    val plannedAt: Instant,
    val occurrenceId: String,
    val runId: String,
)

internal data class TaskRunRequest(
    val taskId: String,
    val runId: String,
    val occurrenceId: String?,
    val scheduleRevision: Long,
    val plannedAt: Instant?,
    val triggeredAt: Instant,
    val title: String,
    val prompt: String,
    val timeZoneId: String,
    val origin: TaskRunOrigin,
    val installationIdentity: ToolInstallationIdentity,
)
