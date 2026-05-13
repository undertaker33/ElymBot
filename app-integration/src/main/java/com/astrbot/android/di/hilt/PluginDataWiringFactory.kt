package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.plugin.data.PluginCatalogSyncStorePortAdapter
import com.astrbot.android.feature.plugin.data.PluginInstallStorePortAdapter
import com.astrbot.android.feature.plugin.data.PluginPackageValidator
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.data.config.DefaultPluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.PluginConfigStorage
import com.astrbot.android.feature.plugin.data.config.PluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.RoomPluginConfigStorage
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.data.state.RoomPluginStateStore
import com.astrbot.android.feature.plugin.domain.PluginCatalogRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginInstallRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginPackageValidationPort
import com.astrbot.android.feature.plugin.domain.PluginRepositoryPort
import com.astrbot.android.feature.plugin.domain.cleanup.DefaultPluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.PluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.PluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.runtime.DefaultPluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.PluginInstaller
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.RuntimePluginPackageValidationPort
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RegistryCompiler
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSessionFactory
import com.astrbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import com.astrbot.android.feature.plugin.runtime.catalog.PluginCatalogFetcher
import com.astrbot.android.feature.plugin.runtime.catalog.PluginCatalogSynchronizer
import com.astrbot.android.feature.plugin.runtime.catalog.PluginInstallIntentHandler
import com.astrbot.android.feature.plugin.runtime.catalog.PluginRepositorySubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider

internal class PluginDataWiringFactory @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pluginDao: PluginInstallAggregateDao,
    private val pluginCatalogDao: PluginCatalogDao,
    private val pluginConfigDao: PluginConfigSnapshotDao,
    private val pluginStateEntryDao: PluginStateEntryDao,
) {
    private val storagePaths: PluginStoragePaths
        get() = PluginStoragePaths.fromFilesDir(appContext.filesDir)

    fun createPluginRepositoryPort(
        runtimeLoaderProvider: Provider<PluginV2RuntimeLoader>,
    ): PluginRepositoryPort {
        val pluginConfigStorage = createPluginConfigStorage()
        val pluginDataCleanupService = createPluginDataCleanupService(
            configStorage = pluginConfigStorage,
            stateStore = createPluginStateStore(),
            runtimeArtifactCleaner = DefaultPluginRuntimeArtifactCleaner(runtimeLoaderProvider),
        )
        return FeaturePluginRepository(
            appContext = appContext,
            pluginDao = pluginDao,
            pluginCatalogDao = pluginCatalogDao,
            pluginConfigStorage = pluginConfigStorage,
            pluginDataCleanupService = pluginDataCleanupService,
        )
    }

    private fun createPluginConfigStorage(): PluginConfigStorage = RoomPluginConfigStorage(
        pluginInstallDao = pluginDao,
        pluginConfigDao = pluginConfigDao,
    )

    private fun createPluginHostConfigResolver(
        configStorage: PluginConfigStorage,
    ): PluginHostConfigResolver = DefaultPluginHostConfigResolver(
        storagePaths = storagePaths,
        configStorage = configStorage,
    )

    private fun createPluginStateStore(): PluginStateStore = RoomPluginStateStore(
        dao = pluginStateEntryDao,
    )

    private fun createPluginDataCleanupService(
        configStorage: PluginConfigStorage,
        stateStore: PluginStateStore,
        runtimeArtifactCleaner: PluginRuntimeArtifactCleaner,
    ): PluginDataCleanupService = DefaultPluginDataCleanupService(
        storagePaths = storagePaths,
        configStorage = configStorage,
        stateStore = stateStore,
        runtimeArtifactCleaner = runtimeArtifactCleaner,
    )

    fun createDefaultPluginExecutionHostOperations(): DefaultPluginExecutionHostOperations {
        val configStorage = createPluginConfigStorage()
        return DefaultPluginExecutionHostOperations(
            hostConfigResolver = createPluginHostConfigResolver(configStorage),
        )
    }

    fun createPluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        hostOperations: PluginExecutionHostOperations,
        repositoryStatePort: com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort,
    ): PluginV2RuntimeLoader = PluginV2RuntimeLoader(
        sessionFactory = PluginV2RuntimeSessionFactory(),
        compiler = PluginV2RegistryCompiler(logBus = logBus),
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
        lifecycleManager = lifecycleManager,
        hostOperations = hostOperations,
        repositoryStatePort = repositoryStatePort,
        stateStore = createPluginStateStore(),
    )

    fun createPluginInstaller(
        hostVersion: String,
        supportedProtocolVersion: Int,
        repository: PluginInstallRepositoryPort,
        remotePackageDownloader: RemotePluginPackageDownloader,
        logBus: PluginRuntimeLogBus,
    ): PluginInstaller = PluginInstaller(
        validator = createPluginPackageValidator(
            hostVersion = hostVersion,
            supportedProtocolVersion = supportedProtocolVersion,
        ),
        storagePaths = storagePaths,
        installStore = PluginInstallStorePortAdapter(repository),
        remotePackageDownloader = remotePackageDownloader,
        logBus = logBus,
    )

    fun createPluginPackageValidationPort(
        hostVersion: String,
        supportedProtocolVersion: Int,
    ): PluginPackageValidationPort = RuntimePluginPackageValidationPort(
        validator = createPluginPackageValidator(
            hostVersion = hostVersion,
            supportedProtocolVersion = supportedProtocolVersion,
        ),
    )

    private fun createPluginPackageValidator(
        hostVersion: String,
        supportedProtocolVersion: Int,
    ): PluginPackageValidator = PluginPackageValidator(
        hostVersion = hostVersion,
        supportedProtocolVersion = supportedProtocolVersion,
    )

    fun createPluginCatalogSynchronizer(
        repository: PluginCatalogRepositoryPort,
        fetcher: PluginCatalogFetcher,
        logBus: PluginRuntimeLogBus,
    ): PluginCatalogSynchronizer = PluginCatalogSynchronizer(
        store = PluginCatalogSyncStorePortAdapter(repository),
        fetcher = fetcher,
        logBus = logBus,
    )

    fun createPluginRepositorySubscriptionManager(
        repository: PluginCatalogRepositoryPort,
        synchronizer: PluginCatalogSynchronizer,
    ): PluginRepositorySubscriptionManager = PluginRepositorySubscriptionManager(
        store = PluginCatalogSyncStorePortAdapter(repository),
        synchronizer = synchronizer,
    )

    fun createPluginInstallIntentHandler(
        installer: PluginInstaller,
        subscriptionManager: PluginRepositorySubscriptionManager,
    ): PluginInstallIntentHandler = PluginInstallIntentHandler(
        installer = installer,
        repositorySubscriptionManager = subscriptionManager,
    )
}
