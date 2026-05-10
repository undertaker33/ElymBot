package com.astrbot.android.feature.plugin.data

import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.feature.plugin.domain.PluginCatalogRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginInstallRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.feature.plugin.runtime.PluginFailureSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.classifyFailure
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource

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

class PersistentPluginFailureStateStorePortAdapter(
    private val repository: PluginStateRepositoryPort,
) : PluginFailureStateStore {
    override fun get(pluginId: String): PluginFailureSnapshot? {
        return repository.findByPluginId(pluginId)
            ?.failureState
            ?.takeIf { failureState -> failureState.hasFailures }
            ?.toFailureSnapshot(pluginId)
    }

    override fun put(snapshot: PluginFailureSnapshot) {
        repository.findByPluginId(snapshot.pluginId) ?: return
        val failureState = snapshot.toFailureState()
        if (failureState.hasFailures) {
            repository.updateFailureState(snapshot.pluginId, failureState)
        } else {
            repository.clearFailureState(snapshot.pluginId)
        }
    }

    override fun remove(pluginId: String) {
        repository.findByPluginId(pluginId) ?: return
        repository.clearFailureState(pluginId)
    }
}

private fun PluginFailureState.toFailureSnapshot(pluginId: String): PluginFailureSnapshot {
    val suspendedUntil = suspendedUntilEpochMillis
    return PluginFailureSnapshot(
        pluginId = pluginId,
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        failureCategory = classifyFailure(lastErrorSummary),
        isSuspended = suspendedUntil != null,
        suspendedUntilEpochMillis = suspendedUntil,
    )
}

private fun PluginFailureSnapshot.toFailureState(): PluginFailureState {
    return PluginFailureState(
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        suspendedUntilEpochMillis = suspendedUntilEpochMillis,
    )
}
