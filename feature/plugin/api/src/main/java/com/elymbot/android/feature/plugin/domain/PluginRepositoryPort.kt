package com.elymbot.android.feature.plugin.domain

import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginCatalogSyncState
import com.elymbot.android.model.plugin.PluginCatalogVersion
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginStaticConfigSchema
import com.elymbot.android.model.plugin.PluginStaticConfigValue
import com.elymbot.android.model.plugin.PluginConfigStorageBoundary
import com.elymbot.android.model.plugin.PluginConfigStoreSnapshot
import com.elymbot.android.model.plugin.PluginFailureState
import com.elymbot.android.model.plugin.PluginRepositorySource
import com.elymbot.android.model.plugin.PluginUpdateAvailability
import com.elymbot.android.model.plugin.PluginUninstallPolicy
import kotlinx.coroutines.flow.StateFlow

interface PluginInstallRepositoryPort {
    fun findByPluginId(pluginId: String): PluginInstallRecord?
    fun upsert(record: PluginInstallRecord)
    fun delete(pluginId: String)
    fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord
    fun uninstall(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult
}

interface PluginCatalogRepositoryPort {
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    fun getRepositorySource(sourceId: String): PluginRepositorySource?
    fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState?
    fun listRepositorySources(): List<PluginRepositorySource>
    fun listAllCatalogEntries(): List<PluginCatalogEntryRecord>
    fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion>
    fun getUpdateAvailability(pluginId: String, hostVersion: String): PluginUpdateAvailability?
    fun replaceRepositoryCatalog(source: PluginRepositorySource)
    fun upsertRepositorySource(source: PluginRepositorySource)
}

interface PluginConfigRepositoryPort {
    fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?
    fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String?
    fun resolveInstalledSettingsSchemaPath(pluginId: String): String?
    fun resolveConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot
    fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot
    fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot
}

interface PluginStateRepositoryPort {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    fun findByPluginId(pluginId: String): PluginInstallRecord?
    fun updateFailureState(pluginId: String, failureState: PluginFailureState): PluginInstallRecord
    fun clearFailureState(pluginId: String): PluginInstallRecord
}

interface PluginRepositoryPort :
    PluginInstallRepositoryPort,
    PluginCatalogRepositoryPort,
    PluginConfigRepositoryPort,
    PluginStateRepositoryPort

data class PluginUninstallResult(
    val pluginId: String,
    val policy: PluginUninstallPolicy,
    val removedData: Boolean,
)
