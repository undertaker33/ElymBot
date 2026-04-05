package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginExecutionResultMergerTest {
    @Test
    fun merger_preserves_chain_order_and_reports_interactive_conflicts() {
        val merger = PluginExecutionResultMerger()
        val merged = merger.merge(
            trigger = PluginTriggerSource.OnCommand,
            outcomes = listOf(
                outcome(pluginId = "alpha", result = TextResult("alpha")),
                outcome(
                    pluginId = "beta",
                    result = SettingsUiRequest(PluginSettingsSchema(title = "beta")),
                ),
                outcome(
                    pluginId = "gamma",
                    result = SettingsUiRequest(PluginSettingsSchema(title = "gamma")),
                ),
            ),
        )

        assertEquals(listOf("alpha", "beta", "gamma"), merged.orderedPluginIds)
        assertEquals("gamma", merged.primaryInteractivePluginId)
        assertEquals("settings_ui", merged.primaryInteractiveResultType)
        assertEquals(listOf("beta"), merged.conflicts.map { conflict -> conflict.pluginId })
        assertEquals("interactive_result_overridden", merged.conflicts.single().reason)
    }

    private fun outcome(
        pluginId: String,
        result: com.astrbot.android.model.plugin.PluginExecutionResult,
    ): PluginExecutionOutcome {
        val plugin = runtimePlugin(pluginId = pluginId) { result }
        return PluginExecutionOutcome(
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
            installState = plugin.installState,
            context = executionContextFor(plugin),
            result = result,
            succeeded = true,
            failureSnapshot = PluginFailureSnapshot(pluginId = pluginId),
        )
    }
}
