package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmSourceContractTest {

    private val repoRoot: Path = detectProjectRoot()
    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }
    private val runtimeLlmRoot: Path = repoRoot
        .resolve("core/runtime-llm/src/main/java/com/astrbot/android/core/runtime/llm")

    @Test
    fun core_runtime_llm_contracts_exist() {
        val required = listOf(
            runtimeLlmRoot.resolve("LlmInvocationContracts.kt"),
            runtimeLlmRoot.resolve("LlmClientPort.kt"),
            runtimeLlmRoot.resolve("LlmProviderProbePort.kt"),
            runtimeLlmRoot.resolve("StreamingResponseSegmenter.kt"),
        )
        val missing = required.filterNot { path -> path.exists() }

        assertTrue("Missing LLM contract/support files: $missing", missing.isEmpty())
    }

    @Test
    fun app_llm_package_contains_only_android_adapter_or_compat_facade() {
        val allowedAppFiles = setOf(
            "ChatCompletionService.kt",
            "ChatCompletionServiceLlmClient.kt",
            "HiltLlmProviderProbePort.kt",
            "LlmMediaService.kt",
        )
        val unexpected = Files.walk(mainRoot.resolve("core/runtime/llm")).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .map { it.fileName.toString() }
                .filter { fileName -> fileName !in allowedAppFiles }
                .toList()
        }

        assertTrue(
            "App LLM package may only keep Android adapters or explicit compat facades: $unexpected",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun core_runtime_llm_must_not_depend_on_app_or_feature_models() {
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import com.astrbot.android.model.",
            "import com.astrbot.android.feature.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.di.",
        )
        val violations = kotlinFilesUnder(runtimeLlmRoot).flatMap { file ->
            val text = file.readText()
            forbiddenImports
                .filter { forbidden -> text.contains(forbidden) }
                .map { forbidden -> "${runtimeLlmRoot.relativize(file)} imports $forbidden" }
        }

        assertTrue(
            "core:runtime-llm must expose core-owned DTO/ports, not app or feature models: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_llm_adapter_paths_and_names_must_be_absent_from_production() {
        val legacyPaths = listOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "runtime/llm/LegacyLlmProviderProbeAdapter.kt",
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
        ).filter { relativePath -> mainRoot.resolve(relativePath).exists() }

        val legacyNameViolations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            Regex("""\bLegacy(?:ChatCompletionService|LlmProviderProbe|RuntimeOrchestrator)Adapter\b""")
                .findAll(file.readText())
                .map { match -> "$relative references ${match.value}" }
                .toList()
        }

        assertTrue("Legacy LLM adapter paths must be removed: $legacyPaths", legacyPaths.isEmpty())
        assertTrue(
            "Production code must not reference legacy LLM adapter names: $legacyNameViolations",
            legacyNameViolations.isEmpty(),
        )
    }

    @Test
    fun feature_code_must_not_directly_import_chat_completion_service() {
        val allowedFiles = setOf(
            "core/runtime/llm/ChatCompletionService.kt",
            "core/runtime/llm/ChatCompletionServiceLlmClient.kt",
            "core/runtime/llm/HiltLlmProviderProbePort.kt",
            "core/runtime/llm/LlmMediaService.kt",
            "di/runtime/audio/CompatChatCompletionAudioRuntimePort.kt",
            "di/hilt/runtime/LlmRuntimeModule.kt",
        )

        val violations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            if (relative in allowedFiles) {
                emptyList()
            } else if (file.readText().contains("import com.astrbot.android.core.runtime.llm.ChatCompletionService")) {
                listOf("$relative imports ChatCompletionService directly")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "Only core LLM implementation files may import ChatCompletionService directly: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_directly_new_default_runtime_orchestrator_outside_hilt_module() {
        val allowedFiles = setOf(
            "di/hilt/RuntimeServicesModule.kt",
            "di/hilt/runtime/LlmRuntimeModule.kt",
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",
        )

        val violations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            if (relative in allowedFiles) {
                emptyList()
            } else if (file.readText().contains("DefaultRuntimeLlmOrchestrator()")) {
                listOf("$relative directly constructs DefaultRuntimeLlmOrchestrator()")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "Only the Hilt module/definition site may construct DefaultRuntimeLlmOrchestrator(): $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        val root = if (relativeRoot == ".") mainRoot else mainRoot.resolve(relativeRoot)
        return kotlinFilesUnder(root)
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
