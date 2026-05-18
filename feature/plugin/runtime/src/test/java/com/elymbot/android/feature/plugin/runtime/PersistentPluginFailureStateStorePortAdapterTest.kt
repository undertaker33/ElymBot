package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.elymbot.android.model.plugin.PluginFailureCategory
import com.elymbot.android.model.plugin.PluginFailureState
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginRepositorySource
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentPluginFailureStateStorePortAdapterTest {
    @Test
    fun put_is_noop_for_missing_plugin_without_update_or_clear() {
        val repository = RecordingPluginStateRepositoryPort()
        val store = PersistentPluginFailureStateStorePortAdapter(repository)

        store.put(
            PluginFailureSnapshot(
                pluginId = "missing.plugin",
                consecutiveFailureCount = 1,
                lastFailureAtEpochMillis = 10L,
                lastErrorSummary = "permission denied",
            ),
        )

        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.clearCalls)
    }

    @Test
    fun remove_is_noop_for_missing_plugin_without_clear() {
        val repository = RecordingPluginStateRepositoryPort()
        val store = PersistentPluginFailureStateStorePortAdapter(repository)

        store.remove("missing.plugin")

        assertEquals(0, repository.clearCalls)
    }

    @Test
    fun put_updates_existing_plugin_and_remove_clears_existing_plugin() {
        val repository = RecordingPluginStateRepositoryPort(
            recordsById = mutableMapOf("existing.plugin" to installedRecord("existing.plugin")),
        )
        val store = PersistentPluginFailureStateStorePortAdapter(repository)

        store.put(
            PluginFailureSnapshot(
                pluginId = "existing.plugin",
                consecutiveFailureCount = 2,
                lastFailureAtEpochMillis = 20L,
                lastErrorSummary = "runtime timeout",
            ),
        )
        store.remove("existing.plugin")

        assertEquals(1, repository.updateCalls)
        assertEquals("runtime timeout", repository.lastUpdatedFailureState?.lastErrorSummary)
        assertEquals(1, repository.clearCalls)
    }

    @Test
    fun get_classifies_failure_category_from_error_summary() {
        val repository = RecordingPluginStateRepositoryPort(
            recordsById = mutableMapOf(
                "existing.plugin" to installedRecord(
                    pluginId = "existing.plugin",
                    failureState = PluginFailureState(
                        consecutiveFailureCount = 1,
                        lastFailureAtEpochMillis = 30L,
                        lastErrorSummary = "requires granted permission: filesystem",
                    ),
                ),
            ),
        )
        val store = PersistentPluginFailureStateStorePortAdapter(repository)

        val snapshot = store.get("existing.plugin")

        assertEquals(PluginFailureCategory.PermissionDenied, snapshot?.failureCategory)
        assertEquals("requires granted permission: filesystem", snapshot?.lastErrorSummary)
        assertNull(store.get("missing.plugin"))
    }
}

private class RecordingPluginStateRepositoryPort(
    private val recordsById: MutableMap<String, PluginInstallRecord> = mutableMapOf(),
) : PluginStateRepositoryPort {
    override val records: StateFlow<List<PluginInstallRecord>> = MutableStateFlow(recordsById.values.toList())
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = MutableStateFlow(emptyList())
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = MutableStateFlow(emptyList())
    var updateCalls: Int = 0
        private set
    var clearCalls: Int = 0
        private set
    var lastUpdatedFailureState: PluginFailureState? = null
        private set

    override fun findByPluginId(pluginId: String): PluginInstallRecord? = recordsById[pluginId]

    override fun updateFailureState(
        pluginId: String,
        failureState: PluginFailureState,
    ): PluginInstallRecord {
        updateCalls += 1
        lastUpdatedFailureState = failureState
        recordsById[pluginId] ?: error("missing plugin should not be updated")
        val updated = installedRecord(pluginId, failureState = failureState)
        recordsById[pluginId] = updated
        return updated
    }

    override fun clearFailureState(pluginId: String): PluginInstallRecord {
        clearCalls += 1
        recordsById[pluginId] ?: error("missing plugin should not be cleared")
        val updated = installedRecord(pluginId, failureState = PluginFailureState.none())
        recordsById[pluginId] = updated
        return updated
    }
}

private fun installedRecord(
    pluginId: String,
    failureState: PluginFailureState = PluginFailureState.none(),
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = 2,
            author = "ElymBot",
            title = "Demo",
            description = "Demo plugin",
            minHostVersion = "0.0.1",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "summary",
        ),
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "$pluginId.zip",
            importedAt = 1L,
        ),
        packageContractSnapshot = PluginPackageContractSnapshot(
            protocolVersion = 2,
            runtime = PluginRuntimeDeclarationSnapshot(
                kind = "js_quickjs",
                bootstrap = "runtime/index.js",
                apiVersion = 2,
            ),
            config = PluginConfigEntryPointsSnapshot(
                staticSchema = "config/static-schema.json",
                settingsSchema = "config/settings-schema.json",
            ),
        ),
        enabled = true,
        failureState = failureState,
        installedAt = 1L,
        lastUpdatedAt = 2L,
        localPackagePath = "$pluginId.zip",
        extractedDir = pluginId,
    )
}
