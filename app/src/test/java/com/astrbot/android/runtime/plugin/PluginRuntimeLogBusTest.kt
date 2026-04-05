package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeLogBusTest {

    @Test
    fun in_memory_log_bus_keeps_recent_entries_newest_first_and_supports_plugin_filtering() {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 2)
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 10L,
                pluginId = "alpha",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Execution,
                level = PluginRuntimeLogLevel.Info,
                code = "execution_succeeded",
                message = "alpha",
            ),
        )
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 20L,
                pluginId = "beta",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Execution,
                level = PluginRuntimeLogLevel.Error,
                code = "execution_failed",
                message = "beta",
            ),
        )
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 30L,
                pluginId = "alpha",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Info,
                code = "dispatcher_queued",
                message = "alpha queued",
            ),
        )

        assertEquals(
            listOf("dispatcher_queued", "execution_failed"),
            bus.snapshot().map(PluginRuntimeLogRecord::code),
        )
        assertEquals(
            listOf("dispatcher_queued"),
            bus.snapshot(pluginId = "alpha").map(PluginRuntimeLogRecord::code),
        )
    }

    @Test
    fun engine_and_dispatcher_publish_runtime_logs_for_queued_skipped_success_and_failure_paths() {
        val clock = TestClock(now = 1_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 32)
        val failureGuard = PluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
            clock = { clock.now },
            logBus = bus,
        )
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                logBus = bus,
                clock = { clock.now },
            ),
            failureGuard = failureGuard,
            logBus = bus,
            clock = { clock.now },
        )

        engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = listOf(
                runtimePlugin(pluginId = "alpha") { TextResult("alpha-ok") },
                runtimePlugin(pluginId = "disabled", enabled = false),
                runtimePlugin(pluginId = "boom") { error("boom") },
            ),
            contextFactory = ::executionContextFor,
        )

        val codes = bus.snapshot().map(PluginRuntimeLogRecord::code)
        assertTrue(codes.contains("dispatcher_queued"))
        assertTrue(codes.contains("dispatcher_skipped"))
        assertTrue(codes.contains("execution_succeeded"))
        assertTrue(codes.contains("execution_failed"))
        assertTrue(codes.contains("failure_guard_recorded"))
    }

    @Test
    fun host_action_executor_publishes_success_and_failure_logs() {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val executor = ExternalPluginHostActionExecutor(
            failureGuard = PluginFailureGuard(
                store = InMemoryPluginFailureStateStore(),
                logBus = bus,
            ),
            logBus = bus,
            sendMessageHandler = {},
        )
        val successContext = executionContextFor(runtimePlugin("plugin.message")).copy(
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = "send_message",
                    title = "send_message",
                    granted = true,
                    riskLevel = PluginRiskLevel.LOW,
                ),
            ),
        )

        executor.execute(
            pluginId = "plugin.message",
            request = HostActionRequest(
                action = PluginHostAction.SendMessage,
                payload = mapOf("text" to "hello"),
            ),
            context = successContext,
        )
        executor.execute(
            pluginId = "plugin.message",
            request = HostActionRequest(
                action = PluginHostAction.OpenHostPage,
                payload = emptyMap(),
            ),
            context = executionContextFor(runtimePlugin("plugin.message")),
        )

        val codes = bus.snapshot(pluginId = "plugin.message").map(PluginRuntimeLogRecord::code)
        assertTrue(codes.contains("host_action_succeeded"))
        assertTrue(codes.contains("host_action_failed"))
    }

    @Test
    fun log_bus_auto_clears_expired_plugin_logs_before_publishing_new_records() {
        val store = InMemoryPluginRuntimeLogCleanupSettingsStore()
        PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(store)
        try {
            var now = 1_000L
            PluginRuntimeLogCleanupRepository.updateSettings(
                pluginId = "plugin.demo",
                enabled = true,
                intervalHours = 1,
                intervalMinutes = 0,
                now = now,
            )
            val bus = InMemoryPluginRuntimeLogBus(
                capacity = 16,
                clock = { now },
            )
            bus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = 2_000L,
                    pluginId = "plugin.demo",
                    trigger = PluginTriggerSource.OnCommand,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "old_record",
                    message = "stale",
                ),
            )
            now += 60 * 60 * 1000L
            bus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = 3_000L + now,
                    pluginId = "plugin.demo",
                    trigger = PluginTriggerSource.OnCommand,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "fresh_record",
                    message = "fresh",
                ),
            )

            assertEquals(
                listOf("fresh_record"),
                bus.snapshot(pluginId = "plugin.demo").map(PluginRuntimeLogRecord::code),
            )
        } finally {
            PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(null)
        }
    }
}
