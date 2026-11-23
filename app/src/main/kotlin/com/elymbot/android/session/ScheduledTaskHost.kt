package com.elymbot.android.session

import androidx.room.withTransaction
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.runtime.pluginpackage.PluginInstanceAvailability
import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.core.contract.ScheduledTaskAccessContext
import com.elymbot.core.contract.ScheduledTaskAccessOrigin
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskRunFailureKind
import com.elymbot.core.contract.ScheduledTaskRunReceipt
import com.elymbot.core.contract.ScheduledTaskRunRequest
import com.elymbot.core.contract.ScheduledTaskRunStatus
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ScheduledTasksPluginHandle
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolScope
import java.time.Instant
import kotlinx.coroutines.CancellationException

internal const val SCHEDULED_TASKS_PLUGIN_ID = "scheduled-tasks"
internal const val SCHEDULED_RUN_STATUS_QUEUED = "QUEUED"
internal const val SCHEDULED_RUN_STATUS_CLAIMING = "CLAIMING"
internal const val SCHEDULED_RUN_STATUS_CLAIMED = "CLAIMED"
internal const val SCHEDULED_RUN_STATUS_RUNNING = "RUNNING"
internal const val SCHEDULED_RUN_STATUS_SUCCEEDED = "SUCCEEDED"
internal const val SCHEDULED_RUN_STATUS_FAILED = "FAILED"
internal const val SCHEDULED_RUN_STATUS_CANCELLED = "CANCELLED"
internal const val SCHEDULED_RUN_STATUS_STALE = "STALE"

internal sealed interface ScheduledRunClaimResult {
    data object Claimed : ScheduledRunClaimResult

    data object Deferred : ScheduledRunClaimResult

    data object Stale : ScheduledRunClaimResult

    data object Lost : ScheduledRunClaimResult
}

internal sealed interface ScheduledRunCollection {
    data class Ready(val requests: List<ScheduledTaskRunRequest>) : ScheduledRunCollection

    data object Deferred : ScheduledRunCollection
}

/** App-owned authorization, system scheduling, and receipt bridge for the task plugin. */
internal class ScheduledTaskHost(
    private val repository: ElymBotDataRepository,
    private val pluginRuntimeManager: PluginRuntimeManager,
    private val systemScheduler: ScheduledTaskWakeScheduler,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun onProcessReady(): ScheduledRunCollection {
        return collectPendingRuns(mode = ScheduledTaskScheduleMode.KEEP)
    }

    suspend fun collectPendingRuns(fromWorker: Boolean = false): ScheduledRunCollection =
        collectPendingRuns(
            mode = if (fromWorker) ScheduledTaskScheduleMode.APPEND else ScheduledTaskScheduleMode.KEEP,
        )

    private suspend fun collectPendingRuns(mode: ScheduledTaskScheduleMode): ScheduledRunCollection {
        val active = activeIdentityOrNull()
        if (active == null) {
            val unavailable = scheduledPluginUnavailable()
            if (unavailable) return ScheduledRunCollection.Deferred
            systemScheduler.schedule(null, mode)
            return ScheduledRunCollection.Ready(emptyList())
        }

        val result = pluginRuntimeManager.withActivePluginHandle(
            pluginId = SCHEDULED_TASKS_PLUGIN_ID,
            installationIdentity = active,
            handleType = ScheduledTasksPluginHandle::class.java,
        ) { handle ->
            val collectedAt = now()
            val requests = handle.collectPendingRuns(collectedAt, active)
            val nextWakeAt = handle.nextWakeAt(now(), active)
            systemScheduler.schedule(nextWakeAt, mode)
            requests
        } ?: return ScheduledRunCollection.Deferred
        return ScheduledRunCollection.Ready(result)
    }

    suspend fun refreshSchedule() {
        val active = activeIdentityOrNull()
        if (active == null) {
            if (!scheduledPluginUnavailable()) systemScheduler.schedule(null, ScheduledTaskScheduleMode.REPLACE)
            return
        }
        pluginRuntimeManager.withActivePluginHandle(
            pluginId = SCHEDULED_TASKS_PLUGIN_ID,
            installationIdentity = active,
            handleType = ScheduledTasksPluginHandle::class.java,
        ) { handle ->
            systemScheduler.schedule(handle.nextWakeAt(now(), active), ScheduledTaskScheduleMode.REPLACE)
        }
    }

    suspend fun snapshotMain(sessionId: String, taskId: String? = null): ScheduledTaskSnapshotResult {
        val active = activeIdentityOrNull()
            ?: return ScheduledTaskSnapshotResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
            )
        val access = mainUiAccess(identity = active, requestedSessionId = sessionId)
            ?: return ScheduledTaskSnapshotResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.UNAUTHORIZED,
            )
        return withActiveHandle(active) { handle -> handle.snapshot(access, taskId) }
            ?: ScheduledTaskSnapshotResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
            )
    }

    suspend fun requestMainAction(
        sessionId: String,
        requestId: String,
        action: ScheduledTaskAction,
    ): ScheduledTaskActionResult {
        if (requestId.isBlank()) {
            return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INVALID_INPUT,
            )
        }
        val active = activeIdentityOrNull()
            ?: return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
            )
        val access = mainUiAccess(identity = active, requestedSessionId = sessionId)
            ?: return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.UNAUTHORIZED,
            )
        val result = withActiveHandle(active) { handle -> handle.requestAction(access, requestId, action) }
            ?: return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
            )
        refreshSchedule()
        return result
    }

    suspend fun confirmMainAction(
        sessionId: String,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
    ): ScheduledTaskActionResult {
        if (confirmationId.isBlank() || initiatingExecutionId.isBlank()) {
            return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INVALID_INPUT,
            )
        }
        val active = activeIdentityOrNull()
            ?: return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
            )
        val accessNow = mainUiAccess(identity = active, requestedSessionId = sessionId)
            ?: return ScheduledTaskActionResult.Rejected(
                com.elymbot.core.contract.ScheduledTaskRejection.UNAUTHORIZED,
            )
        val result = withActiveHandle(active) { handle ->
            handle.confirmAction(accessNow, confirmationId, initiatingExecutionId, approved)
        } ?: return ScheduledTaskActionResult.Rejected(
            com.elymbot.core.contract.ScheduledTaskRejection.INACTIVE_INSTALLATION,
        )
        refreshSchedule()
        return result
    }

    suspend fun claimRun(request: ScheduledTaskRunRequest): ScheduledRunClaimResult {
        when (pluginRuntimeManager.instanceAvailability(request.installationIdentity)) {
            PluginInstanceAvailability.RESTORING,
            PluginInstanceAvailability.UNAVAILABLE -> return ScheduledRunClaimResult.Deferred
            PluginInstanceAvailability.INVALIDATED -> return ScheduledRunClaimResult.Stale
            PluginInstanceAvailability.ACTIVE -> Unit
        }
        val result = pluginRuntimeManager.withActivePluginHandle(
            pluginId = request.installationIdentity.pluginId,
            installationIdentity = request.installationIdentity,
            handleType = ScheduledTasksPluginHandle::class.java,
        ) { handle ->
            handle.recordRunReceipt(
                request.installationIdentity,
                request.toReceipt(ScheduledTaskRunStatus.RUNNING, null),
            )
        } ?: return ScheduledRunClaimResult.Deferred
        return when (result.status) {
            ScheduledTaskRunStatus.RUNNING -> ScheduledRunClaimResult.Claimed
            ScheduledTaskRunStatus.CANCELLED,
            ScheduledTaskRunStatus.MISSED,
            ScheduledTaskRunStatus.STALE -> ScheduledRunClaimResult.Stale
            else -> ScheduledRunClaimResult.Lost
        }
    }

    suspend fun resumeClaimedRun(request: ScheduledTaskRunRequest): ScheduledRunClaimResult =
        when (pluginRuntimeManager.instanceAvailability(request.installationIdentity)) {
            PluginInstanceAvailability.ACTIVE -> ScheduledRunClaimResult.Claimed
            PluginInstanceAvailability.RESTORING,
            PluginInstanceAvailability.UNAVAILABLE -> ScheduledRunClaimResult.Deferred
            PluginInstanceAvailability.INVALIDATED -> ScheduledRunClaimResult.Stale
        }

    suspend fun recordRunReceipt(
        request: ScheduledTaskRunRequest,
        status: ScheduledTaskRunStatus,
        failureKind: ScheduledTaskRunFailureKind? = null,
    ): Boolean {
        if (pluginRuntimeManager.instanceAvailability(request.installationIdentity) != PluginInstanceAvailability.ACTIVE) {
            return false
        }
        return withActiveHandle(request.installationIdentity) { handle ->
            handle.recordRunReceipt(
                request.installationIdentity,
                request.toReceipt(status, failureKind),
            )
            true
        } ?: false
    }

    private suspend fun mainUiAccess(
        identity: ToolInstallationIdentity,
        requestedSessionId: String,
    ): ScheduledTaskAccessContext? {
        repository.initialize()
        val main = repository.database.conversationDao().getMain() ?: return null
        val requested = repository.database.conversationDao().get(requestedSessionId) ?: return null
        if (repository.database.conversationDao().countMain() != 1 || requested.id != main.id ||
            main.id != MAIN_SESSION_ID || main.kind != SESSION_KIND_MAIN || main.mainKey != MAIN_KEY
        ) return null
        return ScheduledTaskAccessContext(
            origin = ScheduledTaskAccessOrigin.APP_UI_MAIN,
            sessionId = main.id,
            userId = APP_USER_ID,
            sessionScope = ToolScope.MAIN_SESSION,
            installationIdentity = identity,
        )
    }

    private suspend fun activeIdentityOrNull(): ToolInstallationIdentity? {
        if (!pluginRuntimeManager.isRestorationComplete) return null
        return pluginRuntimeManager.statusSnapshot()
            .firstOrNull { it.pluginId == SCHEDULED_TASKS_PLUGIN_ID && it.enabled &&
                it.availability == PluginInstanceAvailability.ACTIVE
            }
            ?.installationIdentity
    }

    private suspend fun scheduledPluginUnavailable(): Boolean {
        if (!pluginRuntimeManager.isRestorationComplete) return true
        return pluginRuntimeManager.statusSnapshot().any { status ->
            status.pluginId == SCHEDULED_TASKS_PLUGIN_ID && status.enabled &&
                status.availability in setOf(PluginInstanceAvailability.RESTORING, PluginInstanceAvailability.UNAVAILABLE)
        }
    }

    private suspend fun <T> withActiveHandle(
        identity: ToolInstallationIdentity,
        action: suspend (ScheduledTasksPluginHandle) -> T,
    ): T? = pluginRuntimeManager.withActivePluginHandle(
        pluginId = identity.pluginId,
        installationIdentity = identity,
        handleType = ScheduledTasksPluginHandle::class.java,
        action = action,
    )

    private fun ScheduledTaskRunRequest.toReceipt(
        status: ScheduledTaskRunStatus,
        failureKind: ScheduledTaskRunFailureKind?,
    ) = ScheduledTaskRunReceipt(
        taskId = taskId,
        runId = runId,
        occurrenceId = occurrenceId,
        status = status,
        updatedAt = now(),
        failureKind = failureKind,
    )

    private companion object {
        const val APP_USER_ID = "local-user"
        const val MAIN_SESSION_ID = "main"
        const val SESSION_KIND_MAIN = "MAIN"
        const val MAIN_KEY = "main"
    }
}
