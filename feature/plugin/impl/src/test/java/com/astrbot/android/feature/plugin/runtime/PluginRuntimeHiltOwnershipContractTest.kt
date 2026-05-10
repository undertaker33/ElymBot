package com.astrbot.android.feature.plugin.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeHiltOwnershipContractTest {

    private val projectRoot: Path = detectProjectRoot()

    @Test
    fun plugin_runtime_log_bus_and_cleanup_are_hilt_owned_services() {
        val logBus = productionFile("feature/plugin/impl/src/main/java/com/astrbot/android/feature/plugin/runtime/PluginRuntimeLogBus.kt")
            .readText()
        val cleanup = productionFile("feature/plugin/impl/src/main/java/com/astrbot/android/feature/plugin/runtime/PluginRuntimeLogCleanupRepository.kt")
            .readText()
        val hiltModule = productionFile("app/src/main/java/com/astrbot/android/di/hilt/PluginRuntimeModule.kt")
            .readText()

        assertTrue("PluginRuntimeLogBus must be an injected contract, not an object facade.", logBus.contains("interface PluginRuntimeLogBus"))
        assertTrue("PluginRuntimeLogBus must keep an injectable implementation.", logBus.contains("class InMemoryPluginRuntimeLogBus"))
        assertTrue("Plugin runtime cleanup must expose an injected service contract.", cleanup.contains("interface PluginLogMaintenanceService"))
        assertTrue("Plugin runtime cleanup must keep a concrete service implementation.", cleanup.contains("class DefaultPluginLogMaintenanceService"))
        assertTrue("Hilt must provide PluginLogMaintenanceService.", hiltModule.contains("fun providePluginLogMaintenanceService"))
        assertTrue("Hilt must provide PluginRuntimeLogBus.", hiltModule.contains("fun providePluginRuntimeLogBus"))

        val forbidden = listOf(
            "object PluginRuntimeLogBus",
            "object PluginRuntimeLogCleanupRepository",
            "PluginRuntimeLogCleanupRepository.",
            "PluginRuntimeLogCleanupRepository.settings",
            "PluginRuntimeLogBusProvider.",
        )
        assertNoProductionTokens(forbidden)
    }

    @Test
    fun production_host_capability_path_does_not_call_static_compat_api() {
        val forbidden = listOf(
            "PluginExecutionHostApi.resolve(",
            "PluginExecutionHostApi.inject(",
            "PluginExecutionHostApi.registerHostBuiltinTools(",
            "PluginExecutionHostApi.executeHostBuiltinTool(",
            "PluginRuntimeCatalog.",
        )
        assertNoProductionTokens(forbidden)

        val module = productionFile("app/src/main/java/com/astrbot/android/di/hilt/PluginHostCapabilityModule.kt")
            .readText()
        assertTrue(module.contains("bindPluginExecutionHostResolver"))
        assertTrue(module.contains("providePluginHostCapabilityGatewayFactory"))
        assertTrue(module.contains("providePluginExecutionHostOperations"))
    }

    private fun assertNoProductionTokens(forbidden: List<String>) {
        val violations = productionKotlinFiles().flatMap { file ->
            val relativePath = projectRoot.relativize(file).toString().replace('\\', '/')
            val text = file.readText()
            forbidden.mapNotNull { token ->
                if (text.contains(token)) "$relativePath contains $token" else null
            }
        }

        assertTrue(
            "Production plugin runtime must not depend on static compat seams: $violations",
            violations.isEmpty(),
        )
    }

    private fun productionKotlinFiles(): List<Path> {
        val roots = listOf(
            "app/src/main/java/com/astrbot/android",
            "feature/plugin/impl/src/main/java/com/astrbot/android",
            "feature/chat/runtime/src/main/java/com/astrbot/android",
            "feature/qq/impl/src/main/java/com/astrbot/android",
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
