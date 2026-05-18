package com.elymbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound2PluginRuntimeContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/elymbot/android")
    private val productionSourceRoots: List<Path> = listOf(
        "app/src/main/java/com/elymbot/android",
        "app-integration/src/main/java/com/elymbot/android",
        "feature/chat/runtime/src/main/java/com/elymbot/android",
        "feature/plugin/data/src/main/java/com/elymbot/android",
        "feature/plugin/presentation/src/main/java/com/elymbot/android",
        "feature/plugin/runtime/src/main/java/com/elymbot/android",
        "feature/qq/data/src/main/java/com/elymbot/android",
        "feature/qq/impl/src/main/java/com/elymbot/android",
        "feature/qq/presentation/src/main/java/com/elymbot/android",
        "feature/qq/runtime/src/main/java/com/elymbot/android",
    ).map(projectRoot::resolve).filter { root -> root.exists() }

    @Test
    fun plugin_runtime_hotspots_must_not_reference_transition_provider_or_bridge_tokens() {
        val hotspotFiles = listOf(
            "di/hilt/PluginRuntimeModule.kt",
            "di/hilt/ViewModelDependencyModule.kt",
            "feature/plugin/runtime/PluginFailureGuard.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/plugin/runtime/PluginRuntimeLogBus.kt",
            "feature/plugin/runtime/PluginRuntimeScheduler.kt",
            "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
            "feature/plugin/runtime/PluginV2DispatchEngine.kt",
            "feature/plugin/runtime/PluginV2LifecycleManager.kt",
            "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
        )

        val forbiddenTokens = listOf(
            "PluginRuntimeDependencyBridge.",
            "PluginRuntimeFailureStateStoreProvider.",
            "PluginRuntimeScopedFailureStateStoreProvider.",
            "PluginRuntimeLogBusProvider.",
            "PluginRuntimeScheduleStateStoreProvider.",
            "PluginV2ActiveRuntimeStoreProvider.",
            "PluginV2DispatchEngineProvider.",
            "PluginV2LifecycleManagerProvider.",
            "PluginV2RuntimeLoaderProvider.",
            "installFromHilt(",
        )

        assertHotspotFilesDoNotContain(forbiddenTokens, hotspotFiles)
    }

    @Test
    fun plugin_runtime_mainlines_must_not_backslide_to_legacy_adapter_or_bridge_names() {
        val hotspotFiles = listOf(
            "feature/plugin/runtime/AppChatPluginRuntime.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
        )

        val forbiddenTokens = listOf(
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
            "PluginRuntimeDependencyBridge.",
        )

        assertHotspotFilesDoNotContain(forbiddenTokens, hotspotFiles)
    }

    private fun assertHotspotFilesDoNotContain(forbiddenTokens: List<String>, hotspotFiles: List<String>) {
        val violations = buildList {
            hotspotFiles.forEach { relativePath ->
                val file = resolveProductionFile(relativePath)
                assertTrue("Expected plugin runtime hotspot to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Plugin runtime hotspots must stay fail-closed after phase 5: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_plugin_runtime_store_providers_must_be_deleted_after_hilt_closure() {
        val forbiddenDeclarations = listOf(
            "object PluginRuntimeLogBusProvider",
            "object PluginRuntimeFailureStateStoreProvider",
            "object PluginRuntimeScopedFailureStateStoreProvider",
            "object PluginRuntimeScheduleStateStoreProvider",
            "object PluginV2ActiveRuntimeStoreProvider",
            "object PluginV2DispatchEngineProvider",
            "object PluginV2LifecycleManagerProvider",
            "object PluginV2RuntimeLoaderProvider",
        )

        val violations = buildList {
            productionSourceRoots.flatMap { root -> kotlinFilesUnder(root) }.forEach { file ->
                val relativePath = relativeProductionPath(file)
                val text = file.readText()
                forbiddenDeclarations.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Plugin runtime store providers must not remain in production after Hilt closure: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_runtime_log_cleanup_must_be_hilt_owned_not_static_repository() {
        val forbiddenTokens = listOf(
            "object PluginRuntimeLogCleanupRepository",
            "PluginRuntimeLogCleanupRepository.",
            "PluginRuntimeLogCleanupRepository.settings",
        )

        val violations = buildList {
            productionSourceRoots.flatMap { root -> kotlinFilesUnder(root) }.forEach { file ->
                val relativePath = relativeProductionPath(file)
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Plugin runtime log cleanup must be owned by injected PluginLogMaintenanceService, not a static repository: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_presentation_must_not_import_runtime_implementation_package() {
        val presentationRoot = projectRoot.resolve(
            "feature/plugin/presentation/src/main/java/com/elymbot/android/feature/plugin/presentation",
        )
        assertTrue(
            "Expected plugin presentation source root to exist: ${presentationRoot.toAbsolutePath()}",
            presentationRoot.exists(),
        )

        val violations = kotlinFilesUnder(presentationRoot)
            .mapNotNull { file ->
                val text = file.readText()
                if (text.contains("com.elymbot.android.feature.plugin.runtime")) {
                    relativeProductionPath(file)
                } else {
                    null
                }
            }
            .toList()

        assertTrue(
            "Plugin presentation must use domain/presentation-facing ports instead of runtime implementation imports: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): Sequence<Path> =
        java.nio.file.Files.walk(root).use { paths ->
            paths.filter { path -> path.toString().endsWith(".kt") }.toList().asSequence()
        }

    private fun resolveProductionFile(relativePath: String): Path {
        return productionSourceRoots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { file -> file.exists() }
            ?: mainRoot.resolve(relativePath)
    }

    private fun relativeProductionPath(file: Path): String {
        val sourceRoot = productionSourceRoots.firstOrNull { root -> file.startsWith(root) }
            ?: mainRoot
        return sourceRoot.relativize(file).toString().replace('\\', '/')
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
