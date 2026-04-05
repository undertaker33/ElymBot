package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeLogPresentationTest {

    @Test
    fun build_plugin_runtime_log_text_includes_core_runtime_fields_in_stable_order() {
        val text = buildPluginRuntimeLogText(
            listOf(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = 1712300000000L,
                    pluginId = "plugin.demo",
                    pluginVersion = "1.0.0",
                    trigger = PluginTriggerSource.OnCommand,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Error,
                    code = "execution_failed",
                    message = "boom",
                    resultType = "error",
                    metadata = mapOf("failureCategory" to "runtime_error"),
                ),
            ),
        )

        assertTrue(text.contains("plugin.demo"))
        assertTrue(text.contains("execution_failed"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("failureCategory=runtime_error"))
    }

    @Test
    fun format_plugin_runtime_log_cleanup_interval_prefers_human_readable_hour_minute_output() {
        assertEquals("Every 2h 30m", formatTimedCleanupInterval(true, 2, 30))
        assertEquals("Off", formatTimedCleanupInterval(false, 12, 0))
    }
}
