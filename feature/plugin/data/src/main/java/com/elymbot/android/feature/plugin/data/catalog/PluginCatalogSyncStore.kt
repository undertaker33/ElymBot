package com.elymbot.android.feature.plugin.data.catalog

import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginCatalogSyncState
import com.elymbot.android.model.plugin.PluginCatalogVersion
import com.elymbot.android.model.plugin.PluginRepositorySource

interface PluginCatalogSyncStore {
    fun getRepositorySource(sourceId: String): PluginRepositorySource?

    fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState?

    fun listRepositorySources(): List<PluginRepositorySource>

    fun listAllCatalogEntries(): List<PluginCatalogEntryRecord>

    fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion>

    fun replaceRepositoryCatalog(source: PluginRepositorySource)

    fun upsertRepositorySource(source: PluginRepositorySource)
}
