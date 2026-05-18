package com.elymbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginConfigStateBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/elymbot/android")
    private val pluginApiRoot: Path = projectRoot.resolve("feature/plugin/api/src/main/java/com/elymbot/android")
    private val pluginPresentationRoot: Path = projectRoot.resolve("feature/plugin/presentation/src/main/java/com/elymbot/android")
    private val pluginRuntimeRoot: Path = projectRoot.resolve("feature/plugin/runtime/src/main/java/com/elymbot/android")

    @Test
    fun plugin_view_model_must_not_assemble_controller_or_anonymous_ports_inline() {
        val source = pluginPresentationRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()

        val forbiddenTokens = listOf(
            "PluginManagementUseCases(",
            "object : PluginRepositoryPort",
            "object : PluginRuntimePort",
            "object : PluginGovernancePort",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "PluginViewModel must not keep inline controller/port assembly after Phase 3: $violations",
            violations.isEmpty(),
        )
        assertTrue(
            "PluginViewModel should inject a PluginPresentationController instead of hand-assembling one.",
            source.contains("private val pluginPresentationController: PluginPresentationController"),
        )
    }

    @Test
    fun plugin_presentation_bindings_must_be_split_by_market_config_workspace_and_governance() {
        val requiredFiles = listOf(
            "feature/plugin/presentation/bindings/PluginMarketBindings.kt",
            "feature/plugin/presentation/bindings/PluginConfigBindings.kt",
            "feature/plugin/presentation/bindings/PluginWorkspaceBindings.kt",
            "feature/plugin/presentation/bindings/PluginGovernanceBindings.kt",
        )
        val missing = requiredFiles.filterNot { relativePath ->
            pluginPresentationRoot.resolve(relativePath).exists()
        }

        assertTrue(
            "Phase 3 must land smaller PluginViewModel binding groups: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun plugin_view_model_bindings_must_not_rely_on_static_feature_plugin_repository_entry_points() {
        val source = pluginPresentationRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()

        val forbiddenTokens = listOf(
            "PluginRepository.",
            "FeaturePluginRepository.",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "DefaultPluginViewModelBindings should use injected seams instead of static FeaturePluginRepository entry points: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_repository_phase16_ports_must_be_declared_in_feature_plugin_api() {
        val source = pluginApiRoot
            .resolve("feature/plugin/domain/PluginRepositoryPort.kt")
            .readText()

        val requiredPorts = listOf(
            "interface PluginInstallRepositoryPort",
            "interface PluginCatalogRepositoryPort",
            "interface PluginConfigRepositoryPort",
            "interface PluginStateRepositoryPort",
        )
        val missingPorts = requiredPorts.filterNot(source::contains)

        assertTrue(
            "Phase 16 plugin data/config/state/catalog ports must live in feature/plugin/api: $missingPorts",
            missingPorts.isEmpty(),
        )
    }

    @Test
    fun plugin_phase16_production_hotspots_must_not_call_feature_plugin_repository_static_facade() {
        val checkedSources = mapOf(
            "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginProvisioningModule.kt" to
                projectRoot.resolve("app-integration/src/main/java/com/elymbot/android/di/hilt/PluginProvisioningModule.kt"),
            "app/src/main/java/com/elymbot/android/di/startup/PluginRuntimeObservationStartupChain.kt" to
                projectRoot.resolve("app/src/main/java/com/elymbot/android/di/startup/PluginRuntimeObservationStartupChain.kt"),
            "feature/plugin/presentation/src/main/java/com/elymbot/android/feature/plugin/presentation/PluginViewModel.kt" to
                projectRoot.resolve("feature/plugin/presentation/src/main/java/com/elymbot/android/feature/plugin/presentation/PluginViewModel.kt"),
            "feature/chat/runtime/src/main/java/com/elymbot/android/feature/chat/runtime/AppChatPluginCommandService.kt" to
                projectRoot.resolve(
                    "feature/chat/runtime/src/main/java/com/elymbot/android/feature/chat/runtime/AppChatPluginCommandService.kt",
                ),
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginFailureGuard.kt" to
                pluginRuntimeRoot.resolve("feature/plugin/runtime/PluginFailureGuard.kt"),
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginInstaller.kt" to
                pluginRuntimeRoot.resolve("feature/plugin/runtime/PluginInstaller.kt"),
        )
        val forbiddenTokens = listOf(
            "FeaturePluginRepository.",
            "FeaturePluginRepository as",
            "= FeaturePluginRepository",
            "import com.elymbot.android.feature.plugin.data.FeaturePluginRepository",
        )
        val violations = checkedSources.flatMap { (label, path) ->
            val source = path.readText()
            forbiddenTokens
                .filter(source::contains)
                .map { token -> "$label contains $token" }
        }

        assertTrue(
            "Phase 16 production plugin hotspots must use injected ports/adapters, not static FeaturePluginRepository facade: $violations",
            violations.isEmpty(),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/elymbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/elymbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
