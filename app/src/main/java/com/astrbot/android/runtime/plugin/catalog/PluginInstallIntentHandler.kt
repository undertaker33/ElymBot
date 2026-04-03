package com.astrbot.android.runtime.plugin.catalog

import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.runtime.plugin.PluginInstaller

class PluginInstallIntentHandler(
    private val installer: PluginInstaller,
    private val repositorySubscriptionManager: PluginRepositorySubscriptionManager,
) {
    suspend fun handle(intent: PluginInstallIntent): PluginInstallIntentResult {
        return when (intent) {
            is PluginInstallIntent.CatalogVersion -> PluginInstallIntentResult.Installed(
                record = installer.install(intent),
            )
            is PluginInstallIntent.DirectPackageUrl -> PluginInstallIntentResult.Installed(
                record = installer.install(intent),
            )
            is PluginInstallIntent.RepositoryUrl -> PluginInstallIntentResult.RepositorySynced(
                syncState = repositorySubscriptionManager.subscribeAndSync(intent.url).syncState,
            )
        }
    }
}
