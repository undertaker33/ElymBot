package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.domain.PluginCatalogRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginCatalogRuntimePort
import com.astrbot.android.feature.plugin.domain.PluginConfigRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginEntryExecutionPort
import com.astrbot.android.feature.plugin.domain.PluginFailureRecoveryPort
import com.astrbot.android.feature.plugin.domain.PluginGovernanceReadPort
import com.astrbot.android.feature.plugin.domain.PluginHostCapabilityPresentationPort
import com.astrbot.android.feature.plugin.domain.PluginInstallRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginInstallerPort
import com.astrbot.android.feature.plugin.domain.PluginLogMaintenancePort
import com.astrbot.android.feature.plugin.domain.PluginPackageValidationPort
import com.astrbot.android.feature.plugin.domain.PluginRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginRuntimeLogPresentationPort
import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginHostVersion
import com.astrbot.android.feature.plugin.domain.SupportedPluginProtocolVersion
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginLogMaintenanceService
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceRepository
import com.astrbot.android.feature.plugin.runtime.PersistentPluginFailureStateStorePortAdapter
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.RuntimePluginCatalogRuntimePort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginEntryExecutionPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginFailureRecoveryPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginGovernanceReadPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginHostCapabilityPresentationPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginInstallerPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginLogMaintenancePort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginRuntimeLogPresentationPort
import com.astrbot.android.feature.plugin.runtime.PluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2FilterEvaluator
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.createSharedPreferencesPluginLogMaintenanceService
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginRuntimeModule {

    @Provides
    @Singleton
    fun providePluginRepositoryPort(
        factory: PluginDataWiringFactory,
        runtimeLoaderProvider: Provider<PluginV2RuntimeLoader>,
    ): PluginRepositoryPort = factory.createPluginRepositoryPort(
        runtimeLoaderProvider = runtimeLoaderProvider,
    )

    @Provides
    @Singleton
    fun providePluginStateRepositoryPort(
        repository: PluginRepositoryPort,
    ): PluginStateRepositoryPort = repository

    @Provides
    @Singleton
    fun providePluginInstallRepositoryPort(
        repository: PluginRepositoryPort,
    ): PluginInstallRepositoryPort = repository

    @Provides
    @Singleton
    fun providePluginCatalogRepositoryPort(
        repository: PluginRepositoryPort,
    ): PluginCatalogRepositoryPort = repository

    @Provides
    @Singleton
    fun providePluginConfigRepositoryPort(
        repository: PluginRepositoryPort,
    ): PluginConfigRepositoryPort = repository

    @Provides
    @Singleton
    fun providePluginLogMaintenanceService(
        @ApplicationContext context: Context,
    ): PluginLogMaintenanceService = createSharedPreferencesPluginLogMaintenanceService(context)

    @Provides
    @Singleton
    fun providePluginRuntimeLogBus(
        logMaintenanceService: PluginLogMaintenanceService,
    ): PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(
        logMaintenanceService = logMaintenanceService,
    )

    @Provides
    @Singleton
    fun providePluginRuntimeLogPresentationPort(
        adapter: RuntimePluginRuntimeLogPresentationPort,
    ): PluginRuntimeLogPresentationPort = adapter

    @Provides
    @Singleton
    fun providePluginLogMaintenancePort(
        adapter: RuntimePluginLogMaintenancePort,
    ): PluginLogMaintenancePort = adapter

    @Provides
    @Singleton
    fun providePluginGovernanceReadPort(
        adapter: RuntimePluginGovernanceReadPort,
    ): PluginGovernanceReadPort = adapter

    @Provides
    @Singleton
    fun providePluginEntryExecutionPort(
        adapter: RuntimePluginEntryExecutionPort,
    ): PluginEntryExecutionPort = adapter

    @Provides
    @Singleton
    fun providePluginHostCapabilityPresentationPort(
        adapter: RuntimePluginHostCapabilityPresentationPort,
    ): PluginHostCapabilityPresentationPort = adapter

    @Provides
    @Singleton
    fun providePluginPackageValidationPort(
        factory: PluginDataWiringFactory,
        @PluginHostVersion hostVersion: String,
        @SupportedPluginProtocolVersion supportedProtocolVersion: Int,
    ): PluginPackageValidationPort = factory.createPluginPackageValidationPort(
        hostVersion = hostVersion,
        supportedProtocolVersion = supportedProtocolVersion,
    )

    @Provides
    @Singleton
    fun providePluginInstallerPort(
        adapter: RuntimePluginInstallerPort,
    ): PluginInstallerPort = adapter

    @Provides
    @Singleton
    fun providePluginCatalogRuntimePort(
        adapter: RuntimePluginCatalogRuntimePort,
    ): PluginCatalogRuntimePort = adapter

    @Provides
    @Singleton
    fun providePluginFailureRecoveryPort(
        adapter: RuntimePluginFailureRecoveryPort,
    ): PluginFailureRecoveryPort = adapter

    @Provides
    @Singleton
    fun providePluginFailureStateStore(
        repository: PluginStateRepositoryPort,
    ): PluginFailureStateStore = PersistentPluginFailureStateStorePortAdapter(repository)

    @Provides
    @Singleton
    fun providePluginGovernanceRepository(
        repositoryStatePort: PluginStateRepositoryPort,
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        failureStateStore: PluginFailureStateStore,
        logBus: PluginRuntimeLogBus,
    ): PluginGovernanceRepository = PluginGovernanceRepository(
        repositoryStatePort = repositoryStatePort,
        runtimeSnapshotProvider = activeRuntimeStore::snapshot,
        failureStateStore = failureStateStore,
        logBus = logBus,
    )

    @Provides
    @Singleton
    fun providePluginScopedFailureStateStore(): PluginScopedFailureStateStore = InMemoryPluginScopedFailureStateStore()

    @Provides
    @Singleton
    fun providePluginScheduleStateStore(): PluginScheduleStateStore = InMemoryPluginScheduleStateStore()

    @Provides
    @Singleton
    fun providePluginV2ActiveRuntimeStore(
        logBus: PluginRuntimeLogBus,
    ): PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStore(logBus = logBus)

    @Provides
    @Singleton
    fun providePluginFailureGuard(
        failureStateStore: PluginFailureStateStore,
        scopedFailureStateStore: PluginScopedFailureStateStore,
        logBus: PluginRuntimeLogBus,
    ): PluginFailureGuard = PluginFailureGuard(
        store = failureStateStore,
        scopedStore = scopedFailureStateStore,
        logBus = logBus,
    )

    @Provides
    @Singleton
    fun providePluginRuntimeScheduler(
        scheduleStateStore: PluginScheduleStateStore,
    ): PluginRuntimeScheduler = PluginRuntimeScheduler(
        store = scheduleStateStore,
    )

    @Provides
    @Singleton
    fun providePluginV2LifecycleManager(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
    ): PluginV2LifecycleManager = PluginV2LifecycleManager(
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
    )

    @Provides
    @Singleton
    fun providePluginV2DispatchEngine(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
    ): PluginV2DispatchEngine = PluginV2DispatchEngine(
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
        lifecycleManager = lifecycleManager,
        filterEvaluator = PluginV2FilterEvaluator(
            logBus = logBus,
        ),
    )

    @Provides
    @Singleton
    fun providePluginV2RuntimeLoader(
        factory: PluginDataWiringFactory,
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        hostOperations: PluginExecutionHostOperations,
        repositoryStatePort: PluginStateRepositoryPort,
    ): PluginV2RuntimeLoader = factory.createPluginV2RuntimeLoader(
        activeRuntimeStore = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        hostOperations = hostOperations,
        repositoryStatePort = repositoryStatePort,
    )
}
