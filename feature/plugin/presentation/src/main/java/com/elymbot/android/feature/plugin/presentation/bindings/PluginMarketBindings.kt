package com.elymbot.android.feature.plugin.presentation.bindings

import com.elymbot.android.feature.plugin.data.PluginCatalogVersionGateResult
import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginCatalogSyncState
import com.elymbot.android.model.plugin.PluginCatalogVersion
import com.elymbot.android.model.plugin.PluginDownloadProgress
import com.elymbot.android.model.plugin.PluginInstallIntent
import com.elymbot.android.model.plugin.PluginInstallIntentResult
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginRepositorySource
import com.elymbot.android.model.plugin.PluginUpdateAvailability
import kotlinx.coroutines.flow.StateFlow

interface PluginMarketBindings {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult

    suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult

    suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState

    suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState>

    fun getHostVersion(): String

    fun evaluateCatalogVersion(version: PluginCatalogVersion): PluginCatalogVersionGateResult

    fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability?

    suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord
}
