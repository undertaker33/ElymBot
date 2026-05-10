package com.astrbot.android.feature.plugin.domain

import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginDiagnosticsSummary
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContract
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PluginObservabilitySummary(
    val totalCount: Int = 0,
    val lastCode: String = "",
    val lastLevel: PluginRuntimeLogLevel? = null,
    val lastOccurredAtEpochMillis: Long? = null,
    val failureCount: Int = 0,
    val recoveryCount: Int = 0,
    val latestRequestId: String? = null,
    val latestToolCallId: String? = null,
    val structuredCodes: Set<String> = emptySet(),
)

enum class PluginGovernanceRecoveryStatus {
    IDLE,
    TRACKING_FAILURES,
    SUSPENDED,
    RECOVERED,
    RECOVERY_FAILED,
}

data class PluginGovernanceFailureProjection(
    val pluginId: String,
    val status: PluginGovernanceRecoveryStatus = PluginGovernanceRecoveryStatus.IDLE,
    val lastTransitionCode: String = "",
    val lastTransitionAtEpochMillis: Long? = null,
    val consecutiveFailureCount: Int = 0,
    val suspendedUntilEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
)

data class PluginGovernanceReadModel(
    val snapshot: PluginGovernanceSnapshot,
    val observabilitySummary: PluginObservabilitySummary,
    val recentLogs: List<PluginRuntimeLogRecord>,
    val diagnosticsSummary: PluginDiagnosticsSummary,
    val lifecycleDiagnostics: List<PluginLifecycleDiagnostic>,
    val failureProjection: PluginGovernanceFailureProjection,
)

data class PluginRuntimeLogCleanupSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val intervalMinutes: Int = 0,
    val lastCleanupAtEpochMillis: Long = 0L,
) {
    init {
        require(intervalHours >= 0) { "intervalHours must not be negative." }
        require(intervalMinutes in 0..59) { "intervalMinutes must be between 0 and 59." }
    }

    fun intervalMillis(): Long {
        return intervalHours * 60L * 60L * 1000L + intervalMinutes * 60L * 1000L
    }

    fun shouldAutoClear(now: Long): Boolean {
        if (!enabled) return false
        val intervalMillis = intervalMillis()
        if (intervalMillis <= 0L) return false
        if (lastCleanupAtEpochMillis <= 0L) return false
        return now - lastCleanupAtEpochMillis >= intervalMillis
    }
}

data class PluginPackageValidationResult(
    val manifest: PluginManifest,
    val compatibilityState: PluginCompatibilityState,
    val installable: Boolean,
    val packageContract: PluginPackageContract? = null,
    val validationIssues: List<PluginPackageValidationIssue> = emptyList(),
)

data class PluginExecutionHostSnapshot(
    val runtimeKind: ExternalPluginRuntimeKind = ExternalPluginRuntimeKind.JsQuickJs,
    val bridgeMode: String = "compatibility_only",
    val workspaceSnapshot: PluginHostWorkspaceSnapshot = PluginHostWorkspaceSnapshot(),
    val configBoundary: PluginConfigStorageBoundary? = null,
    val configSnapshot: PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(),
    val mergedSettings: Map<String, Any?> = emptyMap(),
)

data class PluginHostActionExecutionResult(
    val succeeded: Boolean,
    val message: String = "",
)

interface PluginRuntimeLogPresentationPort {
    val records: StateFlow<List<PluginRuntimeLogRecord>>

    fun clearPlugin(pluginId: String)

    fun publishPluginRecoveryRequested(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
        recoveryStatus: String,
        consecutiveFailureCount: Int,
        suspendedUntilEpochMillis: Long?,
    )

    fun publishPluginRecoveryCompleted(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
    )

    fun publishPluginRecoveryFailed(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
        recoveryStatus: String,
        consecutiveFailureCount: Int,
        suspendedUntilEpochMillis: Long?,
        errorSummary: String,
    )

    fun publishUiGovernanceProjectionBuilt(
        occurredAtEpochMillis: Long,
        pluginCount: Int,
        selectedPluginId: String?,
        isShowingDetail: Boolean,
        failureUiCount: Int,
        pluginIds: Collection<String>,
        projectionKey: String,
    )
}

interface PluginLogMaintenancePort {
    val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>>

    fun maybeAutoClear(
        pluginId: String,
        onClear: () -> Unit,
    ): Boolean

    fun recordCleanup(pluginId: String)

    fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
    )
}

interface PluginGovernanceReadPort {
    val governanceReadModels: Flow<Map<String, PluginGovernanceReadModel>>

    fun getPluginGovernance(pluginId: String): PluginGovernanceReadModel?

    fun getPluginGovernanceSilently(pluginId: String): PluginGovernanceReadModel?
}

interface PluginEntryExecutionPort {
    fun execute(
        record: PluginInstallRecord,
        context: PluginExecutionContext,
    ): PluginExecutionResult?
}

interface PluginHostCapabilityPresentationPort {
    fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext

    fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): PluginHostActionExecutionResult
}

interface PluginPackageValidationPort {
    fun validate(packageFile: File): PluginPackageValidationResult
}

interface PluginInstallerPort {
    fun installFromLocalPackage(packageFile: File): PluginInstallRecord

    suspend fun upgrade(update: PluginUpdateAvailability): PluginInstallRecord
}

interface PluginCatalogRuntimePort {
    suspend fun sync(sourceId: String): PluginCatalogSyncState

    suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult

    suspend fun subscribeAndSync(rawCatalogUrl: String): PluginCatalogSyncState
}

interface PluginFailureRecoveryPort {
    fun recover(pluginId: String): PluginInstallRecord
}

fun comparePluginVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_')
    val rightParts = right.split('.', '-', '_')
    val max = maxOf(leftParts.size, rightParts.size)
    repeat(max) { index ->
        val leftPart = leftParts.getOrNull(index).orEmpty()
        val rightPart = rightParts.getOrNull(index).orEmpty()
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        val comparison = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> 1
            rightNumber != null -> -1
            else -> leftPart.compareTo(rightPart)
        }
        if (comparison != 0) return comparison
    }
    return 0
}
