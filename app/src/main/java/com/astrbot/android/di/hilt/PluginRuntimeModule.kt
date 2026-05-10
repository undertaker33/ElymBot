package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.EmptyPluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.FeaturePluginRepositoryStateOwner
import com.astrbot.android.feature.plugin.data.PersistentPluginFailureStateStorePortAdapter
import com.astrbot.android.feature.plugin.data.config.DefaultPluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.PluginConfigStorage
import com.astrbot.android.feature.plugin.data.config.PluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.RoomPluginConfigStorage
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.data.state.RoomPluginStateStore
import com.astrbot.android.data.db.PluginStateEntryDao
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
import com.astrbot.android.feature.plugin.domain.cleanup.DefaultPluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.DefaultPluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.domain.cleanup.PluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.PluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginLogMaintenanceService
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceRepository
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.RuntimePluginCatalogRuntimePort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginEntryExecutionPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginFailureRecoveryPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginGovernanceReadPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginHostCapabilityPresentationPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginInstallerPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginLogMaintenancePort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginPackageValidationPort
import com.astrbot.android.feature.plugin.runtime.RuntimePluginRuntimeLogPresentationPort
import com.astrbot.android.feature.plugin.runtime.PluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2FilterEvaluator
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RegistryCompiler
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSessionFactory
import com.astrbot.android.feature.plugin.runtime.createSharedPreferencesPluginLogMaintenanceService
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginRuntimeModule {

    @Provides
    @Singleton
    fun providePluginRepositoryStatePort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginRepositoryStatePort = owner

    @Provides
    @Singleton
    fun providePluginRepositoryPort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginRepositoryPort = owner

    @Provides
    @Singleton
    fun providePluginInstallRepositoryPort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginInstallRepositoryPort = owner

    @Provides
    @Singleton
    fun providePluginCatalogRepositoryPort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginCatalogRepositoryPort = owner

    @Provides
    @Singleton
    fun providePluginConfigRepositoryPort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginConfigRepositoryPort = owner

    @Provides
    @Singleton
    fun providePluginConfigStorage(
        storage: RoomPluginConfigStorage,
    ): PluginConfigStorage = storage

    @Provides
    @Singleton
    fun providePluginHostConfigResolver(
        resolver: DefaultPluginHostConfigResolver,
    ): PluginHostConfigResolver = resolver

    @Provides
    @Singleton
    fun providePluginStateStore(
        dao: PluginStateEntryDao,
    ): PluginStateStore = RoomPluginStateStore(
        dao = dao,
    )

    @Provides
    @Singleton
    fun providePluginRuntimeArtifactCleaner(
        cleaner: DefaultPluginRuntimeArtifactCleaner,
    ): PluginRuntimeArtifactCleaner = cleaner

    @Provides
    @Singleton
    fun providePluginDataCleanupService(
        service: DefaultPluginDataCleanupService,
    ): PluginDataCleanupService = service

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
        adapter: RuntimePluginPackageValidationPort,
    ): PluginPackageValidationPort = adapter

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
        repositoryStatePort: PluginRepositoryStatePort,
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
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        hostOperations: PluginExecutionHostOperations,
        repositoryStatePort: PluginRepositoryStatePort,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = createPluginV2RuntimeLoader(
        activeRuntimeStore = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        hostOperations = hostOperations,
        repositoryStatePort = repositoryStatePort,
        stateStore = stateStore,
    )

    internal fun providePluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        hostOperations: PluginExecutionHostOperations,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = createPluginV2RuntimeLoader(
        activeRuntimeStore = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        hostOperations = hostOperations,
        repositoryStatePort = EmptyPluginRepositoryStatePort,
        stateStore = stateStore,
    )

    private fun createPluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        hostOperations: PluginExecutionHostOperations,
        repositoryStatePort: PluginRepositoryStatePort,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = PluginV2RuntimeLoader(
        sessionFactory = PluginV2RuntimeSessionFactory(),
        compiler = PluginV2RegistryCompiler(logBus = logBus),
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
        lifecycleManager = lifecycleManager,
        hostOperations = hostOperations,
        repositoryStatePort = repositoryStatePort,
        stateStore = stateStore,
    )
}
