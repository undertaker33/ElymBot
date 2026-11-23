package com.elymbot.core.contract

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * Typed capability implemented by the active scheduled-tasks plugin. The host owns
 * the system scheduler, primary-session authorization, confirmation UI, and Agent queue.
 */
interface ScheduledTasksPluginHandle : PluginHandle {
    suspend fun snapshot(
        access: ScheduledTaskAccessContext,
        taskId: String? = null,
    ): ScheduledTaskSnapshotResult

    suspend fun requestAction(
        access: ScheduledTaskAccessContext,
        requestId: String,
        action: ScheduledTaskAction,
    ): ScheduledTaskActionResult

    suspend fun confirmAction(
        accessNow: ScheduledTaskAccessContext,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
    ): ScheduledTaskActionResult

    /** Next system wake-up time, or null when no enabled task has a future occurrence. */
    suspend fun nextWakeAt(
        now: Instant,
        activeIdentity: ToolInstallationIdentity,
    ): Instant?

    /**
     * Atomically records due/missed occurrences and returns every durable queued run
     * for this active installation. Repeated calls intentionally return unacknowledged
     * queued runs again; the host queue deduplicates by task/run/occurrence identity.
     */
    suspend fun collectPendingRuns(
        now: Instant,
        activeIdentity: ToolInstallationIdentity,
    ): List<ScheduledTaskRunRequest>

    /**
     * Rechecks installation and task state before accepting RUNNING. A returned record
     * with status RUNNING means this call atomically claimed QUEUED work and the host may
     * start the Agent turn. Repeated RUNNING receipts return null so they cannot start a
     * second turn. A stale task may return its CANCELLED record; inactive identities and
     * unknown or already-claimed runs return null.
     */
    suspend fun recordRunReceipt(
        activeIdentity: ToolInstallationIdentity,
        receipt: ScheduledTaskRunReceipt,
    ): ScheduledTaskRunRecord?
}

/**
 * App-only live view and first-use state for the active scheduled-tasks plugin.
 * Kept separate from [ScheduledTasksPluginHandle] so older plugin fixtures remain
 * binary compatible while the host adopts this observer.
 */
interface ScheduledTasksUiHandle : PluginHandle {
    /**
     * Emits the current task view and pending confirmations for a host-attested
     * App main-session caller. Closing or invalidating this handle emits
     * [ScheduledTaskRejection.INACTIVE_INSTALLATION] and completes the flow.
     */
    fun observeMainUiState(
        access: ScheduledTaskAccessContext,
        taskId: String? = null,
    ): Flow<ScheduledTaskUiStateResult>

    /** Persists that the user explicitly skipped the first-task guide. */
    suspend fun skipFirstTaskGuide(
        access: ScheduledTaskAccessContext,
    ): ScheduledTaskUiStateResult
}

sealed interface ScheduledTaskUiStateResult {
    data class Visible(val state: ScheduledTaskUiState) : ScheduledTaskUiStateResult

    data class Rejected(val reason: ScheduledTaskRejection) : ScheduledTaskUiStateResult
}

data class ScheduledTaskUiState(
    val snapshot: ScheduledTaskSnapshot,
    val pendingConfirmations: List<ScheduledTaskActionResult.ConfirmationRequired>,
    val firstTaskCreated: Boolean,
    val guideSkipped: Boolean,
    /** Monotonic only within this active handle; transient confirmation emissions may repeat it. */
    val dataRevision: Long,
)

/** Host-attested request identity. Model arguments never provide these fields. */
data class ScheduledTaskAccessContext(
    val origin: ScheduledTaskAccessOrigin,
    val sessionId: String,
    val userId: String,
    val sessionScope: ToolScope,
    val installationIdentity: ToolInstallationIdentity,
    val executionId: String? = null,
)

enum class ScheduledTaskAccessOrigin {
    APP_UI_MAIN,
    APP_AGENT_MAIN,
    OTHER,
}

data class ScheduledTaskDraft(
    val title: String,
    val prompt: String,
    val startLocalDateTime: LocalDateTime,
    val timeZoneId: String,
    val repeatRule: ScheduledTaskRepeatRule,
    val endCondition: ScheduledTaskEndCondition,
)

sealed interface ScheduledTaskRepeatRule {
    data object Once : ScheduledTaskRepeatRule

    data object Daily : ScheduledTaskRepeatRule

    data object Weekly : ScheduledTaskRepeatRule

    data class IntervalHours(val hours: Int) : ScheduledTaskRepeatRule
}

sealed interface ScheduledTaskEndCondition {
    data object Never : ScheduledTaskEndCondition

    /** The task remains eligible through this local calendar date in its saved zone. */
    data class OnDate(val date: LocalDate) : ScheduledTaskEndCondition

    /** Counts scheduled runs accepted into the host queue, including failed attempts. */
    data class AfterOccurrences(val count: Int) : ScheduledTaskEndCondition
}

enum class ScheduledTaskCreationSource {
    APP,
    AGENT,
}

data class ScheduledTask(
    val id: String,
    val revision: Long,
    val title: String,
    val prompt: String,
    val startLocalDateTime: LocalDateTime,
    val timeZoneId: String,
    val repeatRule: ScheduledTaskRepeatRule,
    val endCondition: ScheduledTaskEndCondition,
    val enabled: Boolean,
    val nextRunAt: Instant?,
    val creationSource: ScheduledTaskCreationSource,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ScheduledTaskSnapshot(
    val capturedAt: Instant,
    val tasks: List<ScheduledTask>,
    val runs: List<ScheduledTaskRunRecord>,
)

sealed interface ScheduledTaskSnapshotResult {
    data class Visible(val snapshot: ScheduledTaskSnapshot) : ScheduledTaskSnapshotResult

    data class Rejected(val reason: ScheduledTaskRejection) : ScheduledTaskSnapshotResult
}

sealed interface ScheduledTaskAction {
    data class Create(val draft: ScheduledTaskDraft) : ScheduledTaskAction

    data class Update(
        val taskId: String,
        val expectedRevision: Long,
        val draft: ScheduledTaskDraft,
    ) : ScheduledTaskAction

    data class Pause(val taskId: String, val expectedRevision: Long) : ScheduledTaskAction

    data class Resume(val taskId: String, val expectedRevision: Long) : ScheduledTaskAction

    data class Delete(val taskId: String, val expectedRevision: Long) : ScheduledTaskAction

    /** Manual run has a separate run record and never changes the saved schedule. */
    data class RunNow(val taskId: String, val expectedRevision: Long) : ScheduledTaskAction
}

sealed interface ScheduledTaskActionResult {
    data class Saved(val task: ScheduledTask) : ScheduledTaskActionResult

    data class Deleted(val taskId: String) : ScheduledTaskActionResult

    data class RunQueued(val run: ScheduledTaskRunRequest) : ScheduledTaskActionResult

    data class ConfirmationRequired(
        val confirmationId: String,
        val requestId: String,
        val initiatingExecutionId: String,
        /** The exact uncommitted operation the App must display before confirmation. */
        val requestedAction: ScheduledTaskAction,
        val summary: ScheduledTaskConfirmationSummary,
        val expiresAt: Instant,
    ) : ScheduledTaskActionResult

    data class Rejected(val reason: ScheduledTaskRejection) : ScheduledTaskActionResult
}

data class ScheduledTaskConfirmationSummary(
    val action: ScheduledTaskActionKind,
    val title: String,
)

enum class ScheduledTaskActionKind {
    CREATE,
    UPDATE,
    PAUSE,
    RESUME,
    DELETE,
    RUN_NOW,
}

enum class ScheduledTaskRejection {
    UNAUTHORIZED,
    INACTIVE_INSTALLATION,
    TASK_NOT_FOUND,
    REVISION_CONFLICT,
    INVALID_INPUT,
    TASK_NOT_ENABLED,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_DECLINED,
    IDEMPOTENCY_CONFLICT,
    STORE_UNAVAILABLE,
}

data class ScheduledTaskRunRequest(
    val taskId: String,
    val runId: String,
    /** Null for a user-requested immediate run. */
    val occurrenceId: String?,
    val scheduleRevision: Long,
    /** Null for a user-requested immediate run. */
    val plannedAt: Instant?,
    val triggeredAt: Instant,
    val title: String,
    val prompt: String,
    val timeZoneId: String,
    val origin: ScheduledTaskRunOrigin,
    val installationIdentity: ToolInstallationIdentity,
)

enum class ScheduledTaskRunOrigin {
    SCHEDULED,
    MANUAL,
}

enum class ScheduledTaskRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    MISSED,
    STALE,
}

enum class ScheduledTaskRunFailureKind {
    HOST_REJECTED,
    AGENT_FAILED,
    USER_CANCELLED,
    TASK_CHANGED,
    TASK_PAUSED,
    PLUGIN_REPLACED,
    UNKNOWN,
}

data class ScheduledTaskRunReceipt(
    val taskId: String,
    val runId: String,
    val occurrenceId: String?,
    val status: ScheduledTaskRunStatus,
    val updatedAt: Instant,
    val failureKind: ScheduledTaskRunFailureKind? = null,
)

data class ScheduledTaskRunRecord(
    val taskId: String,
    val runId: String,
    val occurrenceId: String?,
    val scheduleRevision: Long,
    val plannedAt: Instant?,
    val triggeredAt: Instant,
    val origin: ScheduledTaskRunOrigin,
    val installationIdentity: ToolInstallationIdentity,
    val status: ScheduledTaskRunStatus,
    val updatedAt: Instant,
    val failureKind: ScheduledTaskRunFailureKind? = null,
)
