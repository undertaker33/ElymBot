package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.domain.PluginCatalogRuntimePort
import com.astrbot.android.feature.plugin.domain.PluginEntryExecutionPort
import com.astrbot.android.feature.plugin.domain.PluginExecutionHostSnapshot as DomainPluginExecutionHostSnapshot
import com.astrbot.android.feature.plugin.domain.PluginFailureRecoveryPort
import com.astrbot.android.feature.plugin.domain.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.domain.PluginGovernanceReadPort
import com.astrbot.android.feature.plugin.domain.PluginHostActionExecutionResult
import com.astrbot.android.feature.plugin.domain.PluginHostCapabilityPresentationPort
import com.astrbot.android.feature.plugin.domain.PluginInstallerPort
import com.astrbot.android.feature.plugin.domain.PluginLogMaintenancePort
import com.astrbot.android.feature.plugin.domain.PluginPackageValidationPort
import com.astrbot.android.feature.plugin.domain.PluginPackageValidationResult
import com.astrbot.android.feature.plugin.domain.PluginRuntimeLogCleanupSettings
import com.astrbot.android.feature.plugin.domain.PluginRuntimeLogPresentationPort
import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.feature.plugin.runtime.catalog.PluginCatalogSynchronizer
import com.astrbot.android.feature.plugin.runtime.catalog.PluginInstallIntentHandler
import com.astrbot.android.feature.plugin.runtime.catalog.PluginRepositorySubscriptionManager
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class RuntimePluginRuntimeLogPresentationPort @Inject constructor(
    private val logBus: PluginRuntimeLogBus,
) : PluginRuntimeLogPresentationPort {
    override val records: StateFlow<List<PluginRuntimeLogRecord>> = logBus.records

    override fun clearPlugin(pluginId: String) {
        logBus.clearPlugin(pluginId)
    }

    override fun publishPluginRecoveryRequested(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
        recoveryStatus: String,
        consecutiveFailureCount: Int,
        suspendedUntilEpochMillis: Long?,
    ) {
        logBus.publishPluginRecoveryRequested(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            occurredAtEpochMillis = occurredAtEpochMillis,
            recoveryStatus = recoveryStatus,
            consecutiveFailureCount = consecutiveFailureCount,
            suspendedUntilEpochMillis = suspendedUntilEpochMillis,
        )
    }

    override fun publishPluginRecoveryCompleted(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
    ) {
        logBus.publishPluginRecoveryCompleted(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
    }

    override fun publishPluginRecoveryFailed(
        pluginId: String,
        pluginVersion: String,
        occurredAtEpochMillis: Long,
        recoveryStatus: String,
        consecutiveFailureCount: Int,
        suspendedUntilEpochMillis: Long?,
        errorSummary: String,
    ) {
        logBus.publishPluginRecoveryFailed(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            occurredAtEpochMillis = occurredAtEpochMillis,
            recoveryStatus = recoveryStatus,
            consecutiveFailureCount = consecutiveFailureCount,
            suspendedUntilEpochMillis = suspendedUntilEpochMillis,
            errorSummary = errorSummary,
        )
    }

    override fun publishUiGovernanceProjectionBuilt(
        occurredAtEpochMillis: Long,
        pluginCount: Int,
        selectedPluginId: String?,
        isShowingDetail: Boolean,
        failureUiCount: Int,
        pluginIds: Collection<String>,
        projectionKey: String,
    ) {
        logBus.publishUiGovernanceProjectionBuilt(
            occurredAtEpochMillis = occurredAtEpochMillis,
            pluginCount = pluginCount,
            selectedPluginId = selectedPluginId,
            isShowingDetail = isShowingDetail,
            failureUiCount = failureUiCount,
            pluginIds = pluginIds,
            projectionKey = projectionKey,
        )
    }
}

class RuntimePluginLogMaintenancePort @Inject constructor(
    private val service: PluginLogMaintenanceService,
) : PluginLogMaintenancePort {
    override val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>> = service.settings

    override fun maybeAutoClear(
        pluginId: String,
        onClear: () -> Unit,
    ): Boolean = service.maybeAutoClear(pluginId = pluginId, onClear = onClear)

    override fun recordCleanup(pluginId: String) {
        service.recordCleanup(pluginId)
    }

    override fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
    ) {
        service.updateSettings(
            pluginId = pluginId,
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
        )
    }
}

class RuntimePluginGovernanceReadPort @Inject constructor(
    private val repository: PluginGovernanceRepository,
) : PluginGovernanceReadPort {
    override val governanceReadModels: Flow<Map<String, PluginGovernanceReadModel>> = repository.observeReadModels()

    override fun getPluginGovernance(pluginId: String): PluginGovernanceReadModel? {
        return repository.get(pluginId)
    }

    override fun getPluginGovernanceSilently(pluginId: String): PluginGovernanceReadModel? {
        return repository.getSilently(pluginId)
    }
}

class RuntimePluginEntryExecutionPort @Inject constructor(
    private val service: PluginEntryExecutionService,
) : PluginEntryExecutionPort {
    override fun execute(
        record: PluginInstallRecord,
        context: PluginExecutionContext,
    ): PluginExecutionResult? = service.execute(record = record, context = context)
}

class RuntimePluginHostCapabilityPresentationPort @Inject constructor(
    private val gateway: PluginHostCapabilityGateway,
) : PluginHostCapabilityPresentationPort {
    override fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: DomainPluginExecutionHostSnapshot,
    ): PluginExecutionContext {
        return gateway.injectContext(
            context = context,
            hostSnapshot = PluginExecutionHostSnapshot(
                runtimeKind = hostSnapshot.runtimeKind,
                bridgeMode = hostSnapshot.bridgeMode,
                workspaceSnapshot = hostSnapshot.workspaceSnapshot,
                configBoundary = hostSnapshot.configBoundary,
                configSnapshot = hostSnapshot.configSnapshot,
                mergedSettings = hostSnapshot.mergedSettings,
            ),
        )
    }

    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): PluginHostActionExecutionResult {
        val result = gateway.executeHostAction(
            pluginId = pluginId,
            request = request,
            context = context,
        )
        return PluginHostActionExecutionResult(
            succeeded = result.succeeded,
            message = result.message,
        )
    }
}

class RuntimePluginPackageValidationPort @Inject constructor(
    private val validator: PluginPackageValidator,
) : PluginPackageValidationPort {
    override fun validate(packageFile: File): PluginPackageValidationResult = validator.validate(packageFile)
}

class RuntimePluginInstallerPort @Inject constructor(
    private val installer: PluginInstaller,
) : PluginInstallerPort {
    override fun installFromLocalPackage(packageFile: File): PluginInstallRecord {
        return installer.installFromLocalPackage(packageFile)
    }

    override suspend fun upgrade(update: PluginUpdateAvailability): PluginInstallRecord {
        return installer.upgrade(update)
    }
}

class RuntimePluginCatalogRuntimePort @Inject constructor(
    private val synchronizer: PluginCatalogSynchronizer,
    private val installIntentHandler: PluginInstallIntentHandler,
    private val subscriptionManager: PluginRepositorySubscriptionManager,
) : PluginCatalogRuntimePort {
    override suspend fun sync(sourceId: String): PluginCatalogSyncState {
        return synchronizer.sync(sourceId)
    }

    override suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult {
        return installIntentHandler.handle(
            intent = intent,
            onDownloadProgress = onDownloadProgress,
        )
    }

    override suspend fun subscribeAndSync(rawCatalogUrl: String): PluginCatalogSyncState {
        return subscriptionManager.subscribeAndSync(rawCatalogUrl).syncState
    }
}

class RuntimePluginFailureRecoveryPort @Inject constructor(
    private val failureGuard: PluginFailureGuard,
    private val stateRepository: PluginStateRepositoryPort,
) : PluginFailureRecoveryPort {
    override fun recover(pluginId: String): PluginInstallRecord {
        failureGuard.recover(pluginId)
        return requireNotNull(stateRepository.findByPluginId(pluginId)) {
            "Plugin $pluginId is not installed."
        }
    }
}
