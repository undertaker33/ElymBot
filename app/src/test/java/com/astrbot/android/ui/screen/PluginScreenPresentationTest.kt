package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryCardUiState
import com.astrbot.android.ui.viewmodel.PluginRepositorySourceCardUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginScreenPresentationTest {

    @Test
    fun `workspace section builder keeps installed repository and discoverable order`() {
        val sections = buildPluginWorkspaceSections(
            PluginScreenUiState(
                records = listOf(pluginRecord("installed")),
                repositorySources = listOf(
                    PluginRepositorySourceCardUiState(
                        sourceId = "repo-1",
                        title = "AstrBot Repo",
                        catalogUrl = "https://repo.example.com/catalog.json",
                        pluginCount = 2,
                    ),
                ),
                catalogEntries = listOf(
                    PluginCatalogEntryCardUiState(
                        sourceId = "repo-1",
                        pluginId = "discoverable",
                        title = "Discoverable",
                        author = "AstrBot",
                        summary = "Summary",
                        latestVersion = "1.0.0",
                        sourceName = "AstrBot Repo",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                PluginUiSection.Installed,
                PluginUiSection.RepositorySources,
                PluginUiSection.Discoverable,
            ),
            sections.map { it.section },
        )
    }

    @Test
    fun `plugin card and permissions presentation do not expose risk labels`() {
        val record = pluginRecord("demo")

        val card = buildPluginRecordPresentation(record)
        val permissions = buildPluginPermissionPresentation(record)

        assertEquals(listOf("Local file", "Compatible"), card.badges)
        assertFalse(card.badges.any { it.contains("risk", ignoreCase = true) })
        assertFalse(permissions.any { item -> item.requirementLabel.contains("risk", ignoreCase = true) })
    }

    private fun pluginRecord(pluginId: String): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "1.0.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = pluginId,
                description = "Plugin $pluginId",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "$pluginId.permission",
                        title = "Permission",
                        description = "Permission for $pluginId",
                    ),
                ),
                minHostVersion = "1.0.0",
                maxHostVersion = "2.0.0",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            lastUpdatedAt = 1L,
        )
    }
}
