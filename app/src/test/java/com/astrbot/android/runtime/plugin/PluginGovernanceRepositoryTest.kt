package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginGovernanceRepositoryTest {

    @Test
    fun getSilently_does_not_emit_projection_feedback_logs() = runTest {
        val pluginId = "com.example.governance.silent"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = InMemoryPluginFailureStateStore(),
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = bus,
            clock = { 1_000L },
        )

        val readModel = repository.getSilently(pluginId)

        assertEquals(pluginId, readModel?.snapshot?.pluginId)
        assertEquals(emptyList<String>(), bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code))
    }

    @Test
    fun observeReadModels_reacts_to_runtime_logs_without_emitting_projection_feedback_logs() = runTest {
        val pluginId = "com.example.governance.observe"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = InMemoryPluginFailureStateStore(),
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = bus,
            clock = { 1_000L },
        )

        val initialReadModels = repository.observeReadModels().first()
        assertEquals(pluginId, initialReadModels.getValue(pluginId).snapshot.pluginId)
        assertEquals(emptyList<String>(), bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code))

        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 1_050L,
                pluginId = pluginId,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Warning,
                code = "failure_guard_suspended",
                message = "Plugin suspended by failure guard.",
            ),
        )

        val latestReadModel = repository.observeReadModels().first().getValue(pluginId)
        assertEquals("failure_guard_suspended", latestReadModel.observabilitySummary.lastCode)
        assertEquals(listOf("failure_guard_suspended"), latestReadModel.recentLogs.map(PluginRuntimeLogRecord::code))
        assertEquals(
            listOf("failure_guard_suspended"),
            bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code),
        )
    }
}
