package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.elymbot.android.feature.plugin.domain.PluginGovernanceFailureProjection
import com.elymbot.android.feature.plugin.domain.PluginGovernanceReadModel
import com.elymbot.android.feature.plugin.domain.PluginGovernanceRecoveryStatus
import com.elymbot.android.model.plugin.PluginDiagnosticsSummary
import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginFailureState
import com.elymbot.android.model.plugin.PluginGovernanceSnapshot
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginLifecycleDiagnostic
import com.elymbot.android.model.plugin.PluginRepositorySource
import com.elymbot.android.model.plugin.PluginRuntimeLogRecord
import com.elymbot.android.model.plugin.PluginSuspensionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class PluginGovernanceRepository(
    private val repositoryStatePort: PluginRepositoryStatePort,
    private val runtimeSnapshotProvider: () -> PluginV2ActiveRuntimeSnapshot,
    private val failureStateStore: PluginFailureStateStore,
    private val diagnosticsSnapshotProvider: (() -> List<PluginLifecycleDiagnostic>)? = null,
    private val logBus: PluginRuntimeLogBus,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val installRecordsFlow: Flow<List<PluginInstallRecord>> = repositoryStatePort.records

    constructor(
        installRecordsFlow: Flow<List<PluginInstallRecord>> = flowOf(emptyList()),
        findInstallRecord: (String) -> PluginInstallRecord?,
        runtimeSnapshotProvider: () -> PluginV2ActiveRuntimeSnapshot,
        failureStateStore: PluginFailureStateStore,
        diagnosticsSnapshotProvider: (() -> List<PluginLifecycleDiagnostic>)? = null,
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        repositoryStatePort = ClosurePluginRepositoryStatePort(
            installRecordsFlow = installRecordsFlow,
            findInstallRecord = findInstallRecord,
        ),
        runtimeSnapshotProvider = runtimeSnapshotProvider,
        failureStateStore = failureStateStore,
        diagnosticsSnapshotProvider = diagnosticsSnapshotProvider,
        logBus = logBus,
        clock = clock,
    )

    fun get(
        pluginId: String,
        logLimit: Int = 20,
    ): PluginGovernanceReadModel? {
        val installRecord = repositoryStatePort.findByPluginId(pluginId) ?: return null
        return buildReadModel(
            installRecord = installRecord,
            logLimit = logLimit,
            publishProjectionLogs = true,
        )
    }

    fun getSilently(
        pluginId: String,
        logLimit: Int = 20,
    ): PluginGovernanceReadModel? {
        val installRecord = repositoryStatePort.findByPluginId(pluginId) ?: return null
        return buildReadModel(
            installRecord = installRecord,
            logLimit = logLimit,
            publishProjectionLogs = false,
        )
    }

    fun observeReadModels(
        logLimit: Int = 20,
    ): Flow<Map<String, PluginGovernanceReadModel>> {
        return combine(installRecordsFlow, logBus.records) { recordsSnapshot, _ ->
            recordsSnapshot.mapNotNull { record ->
                buildReadModel(
                    installRecord = record,
                    logLimit = logLimit,
                    publishProjectionLogs = false,
                )?.let { record.pluginId to it }
            }.toMap()
        }
    }

    private fun buildReadModel(
        installRecord: PluginInstallRecord,
        logLimit: Int,
        publishProjectionLogs: Boolean,
    ): PluginGovernanceReadModel? {
        val pluginId = installRecord.pluginId
        val runtimeSnapshot = runtimeSnapshotProvider()
        val lifecycleDiagnostics = resolveLifecycleDiagnostics(
            pluginId = pluginId,
            logLimit = logLimit,
        )
        val failureSnapshot = failureStateStore.get(pluginId)
        val recentLogs = logBus.snapshot(
            limit = logLimit,
            pluginId = pluginId,
        )
        val observabilitySummary = logBus.summary(
            pluginId = pluginId,
            limit = logLimit,
        )
        val snapshot = PluginGovernanceSnapshotMapper.map(
            installRecord = installRecord,
            runtimeSnapshot = runtimeSnapshot,
            failureSnapshot = failureSnapshot,
            lifecycleDiagnostics = lifecycleDiagnostics,
            clock = clock,
            logBus = logBus,
            publishProjectionLogs = publishProjectionLogs,
        )
        return PluginGovernanceReadModel(
            snapshot = snapshot,
            observabilitySummary = observabilitySummary,
            recentLogs = recentLogs,
            diagnosticsSummary = snapshot.diagnosticsSummary,
            lifecycleDiagnostics = lifecycleDiagnostics,
            failureProjection = projectFailureProjection(
                pluginId = pluginId,
                snapshot = snapshot,
                failureSnapshot = failureSnapshot,
                recentLogs = recentLogs,
            ),
        )
    }

    private fun resolveLifecycleDiagnostics(
        pluginId: String,
        logLimit: Int,
    ): List<PluginLifecycleDiagnostic> {
        diagnosticsSnapshotProvider?.invoke()
            ?.filter { diagnostic -> diagnostic.pluginId == pluginId }
            ?.let { return it }
        return logBus.snapshot(
            limit = logLimit,
            pluginId = pluginId,
            code = "plugin_error_hook_failed",
        ).map { record ->
            PluginLifecycleDiagnostic(
                pluginId = record.pluginId,
                hook = record.metadata["hook"].orEmpty(),
                code = record.code,
                message = record.message,
                tracebackText = record.metadata["tracebackText"].orEmpty(),
                occurredAtEpochMillis = record.occurredAtEpochMillis,
            )
        }
    }
}

private class ClosurePluginRepositoryStatePort(
    installRecordsFlow: Flow<List<PluginInstallRecord>>,
    private val findInstallRecord: (String) -> PluginInstallRecord?,
) : PluginRepositoryStatePort {
    override val records: StateFlow<List<PluginInstallRecord>> =
        installRecordsFlow as? StateFlow<List<PluginInstallRecord>> ?: MutableStateFlow(emptyList())
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = MutableStateFlow(emptyList())
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = MutableStateFlow(emptyList())

    override fun findByPluginId(pluginId: String): PluginInstallRecord? = findInstallRecord(pluginId)

    override fun updateFailureState(
        pluginId: String,
        failureState: PluginFailureState,
    ): PluginInstallRecord {
        error("ClosurePluginRepositoryStatePort is read-only.")
    }

    override fun clearFailureState(pluginId: String): PluginInstallRecord {
        error("ClosurePluginRepositoryStatePort is read-only.")
    }
}

@Suppress("UNUSED_PARAMETER")
private fun projectFailureProjection(
    pluginId: String,
    snapshot: PluginGovernanceSnapshot,
    failureSnapshot: PluginFailureSnapshot?,
    recentLogs: List<PluginRuntimeLogRecord>,
): PluginGovernanceFailureProjection {
    val transitionRecord = recentLogs.firstOrNull { record ->
        record.code == "failure_guard_recovery_failed" ||
            record.code == "failure_guard_recovered" ||
            record.code == "failure_guard_resumed" ||
            record.code == "plugin_recovery_completed" ||
            record.code == "plugin_recovery_failed" ||
            record.code == "plugin_suspension_state_changed"
    }
    val activeFailureCount = snapshot.autoRecoveryState.consecutiveFailureCount
    val autoRecoveryStatus = snapshot.autoRecoveryState.status
    val hasActiveFailure = snapshot.suspensionState == PluginSuspensionState.SUSPENDED ||
        autoRecoveryStatus == "SUSPENDED" ||
        (autoRecoveryStatus == "TRACKING_FAILURES" && activeFailureCount > 0)
    val status = when {
        hasActiveFailure &&
            (
                transitionRecord?.code == "failure_guard_recovery_failed" ||
                    transitionRecord?.code == "plugin_recovery_failed"
                ) -> PluginGovernanceRecoveryStatus.RECOVERY_FAILED
        snapshot.suspensionState == PluginSuspensionState.SUSPENDED ||
            autoRecoveryStatus == "SUSPENDED" -> PluginGovernanceRecoveryStatus.SUSPENDED
        transitionRecord?.code == "failure_guard_recovered" ||
            transitionRecord?.code == "failure_guard_resumed" ||
            transitionRecord?.code == "plugin_recovery_completed" -> PluginGovernanceRecoveryStatus.RECOVERED
        transitionRecord?.code == "plugin_suspension_state_changed" &&
            transitionRecord.metadata["currentState"] == "NOT_SUSPENDED" -> PluginGovernanceRecoveryStatus.RECOVERED
        autoRecoveryStatus == "TRACKING_FAILURES" && activeFailureCount > 0 -> {
            PluginGovernanceRecoveryStatus.TRACKING_FAILURES
        }
        else -> PluginGovernanceRecoveryStatus.IDLE
    }
    return PluginGovernanceFailureProjection(
        pluginId = pluginId,
        status = status,
        lastTransitionCode = transitionRecord?.code.orEmpty(),
        lastTransitionAtEpochMillis = transitionRecord?.occurredAtEpochMillis,
        consecutiveFailureCount = snapshot.autoRecoveryState.consecutiveFailureCount,
        suspendedUntilEpochMillis = snapshot.autoRecoveryState.suspendedUntilEpochMillis,
        lastErrorSummary = snapshot.lastFailure?.summary.orEmpty(),
    )
}

