package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginExecutionContract
import com.astrbot.android.model.plugin.ExternalPluginExecutionEntryPoint
import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginTriggerManagementPresentationTest {

    @Test
    fun build_trigger_management_state_splits_open_and_closed_triggers_from_contract() {
        val state = buildPluginTriggerManagementState(
            binding = ExternalPluginRuntimeBinding(
                installRecord = pluginRecord("plugin.demo"),
                status = ExternalPluginExecutionBindingStatus.READY,
                contract = ExternalPluginExecutionContract(
                    contractVersion = 1,
                    entryPoint = ExternalPluginExecutionEntryPoint(
                        runtimeKind = ExternalPluginRuntimeKind.JsQuickJs,
                        path = "dist/index.js",
                        entrySymbol = "main",
                    ),
                    supportedTriggers = setOf(
                        PluginTriggerSource.OnPluginEntryClick,
                        PluginTriggerSource.OnCommand,
                        PluginTriggerSource.OnSchedule,
                    ),
                ),
                entryAbsolutePath = "C:/plugins/demo/dist/index.js",
            ),
        )

        assertEquals(PluginTriggerManagementStatus.Ready, state.status)
        assertEquals("JavaScript (QuickJS)", state.runtimeLabel)
        assertEquals(
            listOf(
                PluginTriggerSource.OnPluginEntryClick,
                PluginTriggerSource.OnCommand,
            ),
            state.openTriggers.map { it.trigger },
        )
        assertEquals(
            listOf(PluginTriggerSource.OnSchedule),
            state.closedTriggers.map { it.trigger },
        )
        assertTrue(state.canManualOpen)
    }

    @Test
    fun build_trigger_management_state_surfaces_binding_error_when_contract_is_unavailable() {
        val state = buildPluginTriggerManagementState(
            binding = ExternalPluginRuntimeBinding(
                installRecord = pluginRecord("plugin.demo"),
                status = ExternalPluginExecutionBindingStatus.MISSING_CONTRACT,
                errorSummary = "Missing external execution contract: android-execution.json",
            ),
        )

        assertEquals(PluginTriggerManagementStatus.MissingContract, state.status)
        assertTrue(state.openTriggers.isEmpty())
        assertTrue(state.closedTriggers.isEmpty())
        assertEquals("Missing external execution contract: android-execution.json", state.statusDetail)
    }
}

private fun pluginRecord(pluginId: String): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Demo",
            description = "Demo plugin",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "send_message",
                    title = "Send message",
                    description = "Send messages to chat",
                ),
            ),
            minHostVersion = "1.0.0",
            maxHostVersion = "2.0.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Entry summary",
            riskLevel = PluginRiskLevel.MEDIUM,
        ),
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "C:/plugins/demo.zip",
            importedAt = 1L,
        ),
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        extractedDir = "C:/plugins/demo",
    )
}
