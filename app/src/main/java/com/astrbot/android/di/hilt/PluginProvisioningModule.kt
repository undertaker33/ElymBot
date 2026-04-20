package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.download.AppDownloadManager
import com.astrbot.android.download.DownloadOwnerType
import com.astrbot.android.download.DownloadRequest
import com.astrbot.android.download.DownloadTaskRecord
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.runtime.PluginInstaller
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import com.astrbot.android.feature.plugin.runtime.catalog.PluginCatalogSynchronizer
import com.astrbot.android.feature.plugin.runtime.catalog.PluginInstallIntentHandler
import com.astrbot.android.feature.plugin.runtime.catalog.PluginRepositorySubscriptionManager
import com.astrbot.android.feature.plugin.runtime.catalog.UrlConnectionPluginCatalogFetcher
import com.astrbot.android.model.plugin.PluginDownloadProgress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginProvisioningModule {

    @Provides
    @Singleton
    fun providePluginCatalogSynchronizer(): PluginCatalogSynchronizer {
        return PluginCatalogSynchronizer(
            store = FeaturePluginRepository,
            fetcher = UrlConnectionPluginCatalogFetcher(),
        )
    }

    @Provides
    @Singleton
    fun providePluginRepositorySubscriptionManager(
        synchronizer: PluginCatalogSynchronizer,
    ): PluginRepositorySubscriptionManager {
        return PluginRepositorySubscriptionManager(
            store = FeaturePluginRepository,
            synchronizer = synchronizer,
        )
    }

    @Provides
    @Singleton
    fun providePluginInstaller(
        @ApplicationContext appContext: Context,
    ): PluginInstaller {
        return PluginInstaller(
            validator = PluginPackageValidator(
                hostVersion = currentHostVersion(appContext),
                supportedProtocolVersion = FeaturePluginRepository.SUPPORTED_PROTOCOL_VERSION,
            ),
            storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir),
            installStore = FeaturePluginRepository,
            remotePackageDownloader = RemotePluginPackageDownloader { packageUrl, destinationFile, onProgress ->
                AppDownloadManager.initialize(appContext)
                val taskKey = "plugin:${packageUrl.sha256Hex()}"
                AppDownloadManager.enqueue(
                    DownloadRequest(
                        taskKey = taskKey,
                        url = packageUrl,
                        targetFilePath = destinationFile.absolutePath,
                        displayName = destinationFile.name,
                        ownerType = DownloadOwnerType.PLUGIN_PACKAGE,
                        ownerId = destinationFile.nameWithoutExtension,
                    ),
                )
                AppDownloadManager.awaitCompletion(taskKey) { task ->
                    task.toPluginDownloadProgress()?.let(onProgress)
                }
            },
        )
    }

    @Provides
    @Singleton
    fun providePluginInstallIntentHandler(
        installer: PluginInstaller,
        repositorySubscriptionManager: PluginRepositorySubscriptionManager,
    ): PluginInstallIntentHandler {
        return PluginInstallIntentHandler(
            installer = installer,
            repositorySubscriptionManager = repositorySubscriptionManager,
        )
    }

    private fun currentHostVersion(appContext: Context): String {
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0.0.0" }
    }

    private fun DownloadTaskRecord.toPluginDownloadProgress(): PluginDownloadProgress? {
        return when (status) {
            com.astrbot.android.download.DownloadTaskStatus.QUEUED,
            com.astrbot.android.download.DownloadTaskStatus.RUNNING,
            com.astrbot.android.download.DownloadTaskStatus.PAUSED,
            -> PluginDownloadProgress.downloading(
                bytesDownloaded = downloadedBytes,
                totalBytes = totalBytes ?: -1L,
                bytesPerSecond = bytesPerSecond,
            )

            else -> null
        }
    }

    private fun String.sha256Hex(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
