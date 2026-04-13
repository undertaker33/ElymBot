package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeHealthSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginTrustLevel
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginHostWorkspaceUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginStaticConfigUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginConfigurationStructureTest {

    @Test
    fun `config section builder groups basic runtime data when settings are available`() {
        val sections = buildConfigSectionsForTest(
            PluginScreenUiState(
                staticConfigUiState = PluginStaticConfigUiState(
                    schema = PluginStaticConfigSchema(),
                ),
                schemaUiState = PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(title = "Runtime settings"),
                ),
            ),
        )

        assertEquals(
            listOf("BasicSettings", "RuntimeSettings", "DataSettings"),
            sections.map { it.name },
        )
    }

    @Test
    fun `config section builder keeps empty state when no settings are exposed`() {
        val sections = buildConfigSectionsForTest(PluginScreenUiState())

        assertEquals(listOf("EmptyState"), sections.map { it.name })
    }

    @Test
    fun `workspace section builder groups resource import export cache and debug areas`() {
        val method = Class.forName("com.astrbot.android.ui.screen.PluginWorkspaceScreenKt")
            .declaredMethods
            .firstOrNull { it.name == "buildWorkspaceSections" }

        assertNotNull("Expected buildWorkspaceSections to exist for workspace grouping.", method)

        method!!.isAccessible = true
        val sections = method.invoke(
            null,
            PluginHostWorkspaceUiState(
                isVisible = true,
                pluginId = "demo",
                title = "Demo",
                description = "Demo workspace",
                privateRootPath = "/private/demo",
                importsPath = "/private/demo/imports",
                runtimePath = "/private/demo/runtime",
                exportsPath = "/private/demo/exports",
                cachePath = "/private/demo/cache",
                lastActionMessage = PluginActionFeedback.Text("Imported file"),
                managementSchemaState = PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(title = "Debug actions"),
                ),
            ),
        ) as List<*>

        assertEquals(
            listOf("ResourceArea", "ImportArea", "ExportArea", "CacheArea", "DebugArea"),
            sections.map { (it as Enum<*>).name },
        )
    }

    @Test
    fun `config and workspace tertiary pages use unified action button styling`() {
        assertTrue(pluginConfigUsesUnifiedActionButtons())
        assertTrue(pluginConfigUsesExplicitSaveFab())
        assertTrue(pluginWorkspaceUsesUnifiedActionButtons())
    }

    @Test
    fun `config and workspace gates become read only for suspended or unsupported plugins`() {
        val unsupportedState = buildPluginGovernanceReadOnlyState(
            record = pluginRecord("unsupported"),
            actionState = PluginDetailActionState(
                mutationGate = com.astrbot.android.ui.viewmodel.PluginGovernanceMutationGate(
                    isReadOnly = true,
                    blockedMessage = "This plugin is unsupported on this host. Protocol v1 is no longer supported by the host.",
                ),
            ),
        )
        val suspendedState = buildPluginGovernanceReadOnlyState(
            record = pluginRecord("suspended"),
            actionState = PluginDetailActionState(
                mutationGate = com.astrbot.android.ui.viewmodel.PluginGovernanceMutationGate(
                    isReadOnly = true,
                    blockedMessage = "This plugin is suspended until recovery completes. Configuration and workspace actions are read-only.",
                ),
                failureState = PluginFailureUiState(
                    consecutiveFailureCount = 3,
                    isSuspended = true,
                    statusMessage = PluginActionFeedback.Text("Suspended"),
                    summaryMessage = PluginActionFeedback.Text("Repeated runtime failures"),
                    recoveryMessage = PluginActionFeedback.Text("Recover first"),
                ),
            ),
        )

        assertTrue(unsupportedState.isReadOnly)
        assertTrue(unsupportedState.message.contains("unsupported", ignoreCase = true))
        assertTrue(suspendedState.isReadOnly)
        assertTrue(suspendedState.message.contains("suspended", ignoreCase = true))
    }

    private fun buildConfigSectionsForTest(uiState: PluginScreenUiState): List<Enum<*>> {
        val method = Class.forName("com.astrbot.android.ui.screen.PluginConfigScreenKt")
            .getDeclaredMethod("buildConfigSections", PluginScreenUiState::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, uiState) as List<Enum<*>>
    }

    private fun pluginRecord(pluginId: String): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "2.0.0",
                protocolVersion = 2,
                author = "AstrBot",
                title = pluginId,
                description = "Plugin $pluginId",
                minHostVersion = "1.0.0",
                maxHostVersion = "",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            lastUpdatedAt = 1L,
        )
    }
}
