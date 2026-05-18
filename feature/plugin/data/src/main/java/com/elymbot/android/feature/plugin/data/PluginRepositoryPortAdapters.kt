package com.elymbot.android.feature.plugin.data

import com.elymbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.elymbot.android.feature.plugin.domain.PluginCatalogRepositoryPort
import com.elymbot.android.feature.plugin.domain.PluginInstallRepositoryPort
import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginCatalogSyncState
import com.elymbot.android.model.plugin.PluginCatalogVersion
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginRepositorySource

class PluginInstallStorePortAdapter(
    private val repository: PluginInstallRepositoryPort,
) : PluginInstallStore {
    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        return repository.findByPluginId(pluginId)
    }

    override fun upsert(record: PluginInstallRecord) {
        repository.upsert(record)
    }
}

class PluginCatalogSyncStorePortAdapter(
    private val repository: PluginCatalogRepositoryPort,
) : PluginCatalogSyncStore {
    override fun getRepositorySource(sourceId: String): PluginRepositorySource? {
        return repository.getRepositorySource(sourceId)
    }

    override fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState? {
        return repository.getRepositorySourceSyncState(sourceId)
    }

    override fun listRepositorySources(): List<PluginRepositorySource> {
        return repository.listRepositorySources()
    }

    override fun listAllCatalogEntries(): List<PluginCatalogEntryRecord> {
        return repository.listAllCatalogEntries()
    }

    override fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion> {
        return repository.listCatalogVersions(sourceId, pluginId)
    }

    override fun replaceRepositoryCatalog(source: PluginRepositorySource) {
        repository.replaceRepositoryCatalog(source)
    }

    override fun upsertRepositorySource(source: PluginRepositorySource) {
        repository.upsertRepositorySource(source)
    }
}

