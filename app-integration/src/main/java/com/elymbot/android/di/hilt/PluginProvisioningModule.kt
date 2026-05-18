
package com.elymbot.android.di.hilt

import android.content.Context
import com.elymbot.android.feature.plugin.domain.PluginCatalogRepositoryPort
import com.elymbot.android.feature.plugin.domain.PluginHostVersion
import com.elymbot.android.feature.plugin.domain.PluginInstallRepositoryPort
import com.elymbot.android.feature.plugin.domain.SupportedPluginProtocolVersion
import com.elymbot.android.feature.plugin.runtime.DownloadManagerRemotePluginPackageDownloader
import com.elymbot.android.feature.plugin.runtime.PluginInstaller
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.elymbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import com.elymbot.android.feature.plugin.runtime.catalog.PluginCatalogFetcher
import com.elymbot.android.feature.plugin.runtime.catalog.PluginCatalogSynchronizer
import com.elymbot.android.feature.plugin.runtime.catalog.PluginInstallIntentHandler
import com.elymbot.android.feature.plugin.runtime.catalog.PluginRepositorySubscriptionManager
import com.elymbot.android.feature.plugin.runtime.catalog.UrlConnectionPluginCatalogFetcher
import com.elymbot.android.model.plugin.PluginPackageContract
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginProvisioningModule {

    @Binds
    @Singleton
    abstract fun bindPluginCatalogFetcher(
        fetcher: UrlConnectionPluginCatalogFetcher,
    ): PluginCatalogFetcher

    @Binds
    @Singleton
    abstract fun bindRemotePluginPackageDownloader(
        downloader: DownloadManagerRemotePluginPackageDownloader,
    ): RemotePluginPackageDownloader

    companion object {
        @Provides
        @Singleton
        fun providePluginInstaller(
            factory: PluginDataWiringFactory,
            @PluginHostVersion hostVersion: String,
            @SupportedPluginProtocolVersion supportedProtocolVersion: Int,
            repository: PluginInstallRepositoryPort,
            remotePackageDownloader: RemotePluginPackageDownloader,
            logBus: PluginRuntimeLogBus,
        ): PluginInstaller = factory.createPluginInstaller(
            hostVersion = hostVersion,
            supportedProtocolVersion = supportedProtocolVersion,
            repository = repository,
            remotePackageDownloader = remotePackageDownloader,
            logBus = logBus,
        )

        @Provides
        @Singleton
        fun providePluginCatalogSynchronizer(
            factory: PluginDataWiringFactory,
            repository: PluginCatalogRepositoryPort,
            fetcher: PluginCatalogFetcher,
            logBus: PluginRuntimeLogBus,
        ): PluginCatalogSynchronizer = factory.createPluginCatalogSynchronizer(
            repository = repository,
            fetcher = fetcher,
            logBus = logBus,
        )

        @Provides
        @Singleton
        fun providePluginRepositorySubscriptionManager(
            factory: PluginDataWiringFactory,
            repository: PluginCatalogRepositoryPort,
            synchronizer: PluginCatalogSynchronizer,
        ): PluginRepositorySubscriptionManager = factory.createPluginRepositorySubscriptionManager(
            repository = repository,
            synchronizer = synchronizer,
        )

        @Provides
        @Singleton
        fun providePluginInstallIntentHandler(
            factory: PluginDataWiringFactory,
            installer: PluginInstaller,
            subscriptionManager: PluginRepositorySubscriptionManager,
        ): PluginInstallIntentHandler = factory.createPluginInstallIntentHandler(
            installer = installer,
            subscriptionManager = subscriptionManager,
        )

        @Provides
        @Singleton
        @PluginHostVersion
        fun providePluginHostVersion(
            @ApplicationContext appContext: Context,
        ): String {
            return runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "0.0.0" }
        }

        @Provides
        @Singleton
        @SupportedPluginProtocolVersion
        fun provideSupportedPluginProtocolVersion(): Int = PluginPackageContract.SUPPORTED_PROTOCOL_VERSION

    }
}
