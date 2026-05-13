package com.astrbot.android.feature.plugin.runtime

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeOrchestratorCompatContractTest {
    @Test
    fun unused_static_runtime_orchestrator_shell_must_be_removed_from_main_source() {
        val runtimeOrchestratorFile = projectRoot()
            .resolve("feature/plugin/runtime/src/main/java/com/astrbot/android/feature/plugin/runtime/RuntimeOrchestrator.kt")

        assertTrue(
            "RuntimeOrchestrator static compat shell has no production callers; runtime code should use RuntimeLlmOrchestratorPort.",
            !runtimeOrchestratorFile.exists(),
        )
    }

    private fun projectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return generateSequence(cwd) { it.parent }
            .firstOrNull { it.resolve("settings.gradle.kts").exists() }
            ?: error("Unable to resolve project root from $cwd")
    }
}
