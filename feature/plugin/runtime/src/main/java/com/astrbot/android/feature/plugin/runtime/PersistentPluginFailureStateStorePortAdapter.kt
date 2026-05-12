package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.model.plugin.PluginFailureState

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
