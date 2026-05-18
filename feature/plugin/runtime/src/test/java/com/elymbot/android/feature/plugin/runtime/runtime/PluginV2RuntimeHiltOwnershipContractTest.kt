package com.elymbot.android.feature.plugin.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RuntimeHiltOwnershipContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val pluginRuntimeModulePath =
        "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginRuntimeModule.kt"

    @Test
    fun plugin_v2_runtime_hotspots_are_constructor_owned_not_static_provider_owned() {
        val hotspotFiles = listOf(
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2DispatchEngine.kt",
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2LifecycleManager.kt",
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RuntimeLoader.kt",
        )

        val forbidden = listOf(
            "PluginV2ActiveRuntimeStoreProvider.",
            "PluginV2DispatchEngineProvider.",
            "PluginV2LifecycleManagerProvider.",
            "PluginV2RuntimeLoaderProvider.",
            "installFromHilt(",
            "setStoreOverrideForTests(",
            "setEngineOverrideForTests(",
            "setManagerOverrideForTests(",
            "setLoaderOverrideForTests(",
        )

        val violations = hotspotFiles.flatMap { relativePath ->
            val text = productionFile(relativePath).readText()
            forbidden.mapNotNull { token ->
                if (text.contains(token)) "$relativePath contains $token" else null
            }
        }

        assertTrue(
            "Plugin V2 runtime hotspots must stay on constructor/Hilt-owned dependencies: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun hilt_module_provides_singleton_plugin_v2_runtime_graph() {
        val hiltModule = productionFile(pluginRuntimeModulePath)
            .readText()

        val requiredProviders = listOf(
            "fun providePluginV2ActiveRuntimeStore",
            "fun providePluginV2LifecycleManager",
            "fun providePluginV2DispatchEngine",
            "fun providePluginV2RuntimeLoader",
        )
        val missingProviders = requiredProviders.filterNot(hiltModule::contains)
        assertTrue("PluginRuntimeModule must own Plugin V2 runtime graph providers: $missingProviders", missingProviders.isEmpty())

        val forbiddenProductionObjects = listOf(
            "object PluginV2ActiveRuntimeStoreProvider",
            "object PluginV2DispatchEngineProvider",
            "object PluginV2LifecycleManagerProvider",
            "object PluginV2RuntimeLoaderProvider",
            "object PluginRuntimeCatalog",
        )
        val violations = productionKotlinFiles().flatMap { file ->
            val relativePath = projectRoot.relativize(file).toString().replace('\\', '/')
            val text = file.readText()
            forbiddenProductionObjects.mapNotNull { token ->
                if (text.contains(token)) "$relativePath contains $token" else null
            }
        }

        assertTrue(
            "Static Plugin V2 provider/catalog objects must not be present in production sources: $violations",
            violations.isEmpty(),
        )
    }

    private fun productionKotlinFiles(): List<Path> {
        val roots = listOf(
            "app/src/main/java/com/elymbot/android",
            "app-integration/src/main/java/com/elymbot/android",
            "feature/plugin/runtime/src/main/java/com/elymbot/android",
            "feature/chat/runtime/src/main/java/com/elymbot/android",
            "feature/qq/impl/src/main/java/com/elymbot/android",
        ).map(projectRoot::resolve).filter(Path::exists)
        return roots.flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { path -> path.toString().endsWith(".kt") }.toList()
            }
        }
    }

    private fun productionFile(relativePath: String): Path {
        return projectRoot.resolve(relativePath).also { file ->
            assertTrue("Expected production file to exist: ${file.toAbsolutePath()}", file.exists())
        }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return generateSequence(cwd) { it.parent }
            .firstOrNull { root -> root.resolve("settings.gradle.kts").exists() }
            ?: error("Unable to resolve project root from $cwd")
    }
}
