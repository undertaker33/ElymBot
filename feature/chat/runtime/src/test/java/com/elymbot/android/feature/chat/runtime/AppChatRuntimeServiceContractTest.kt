package com.elymbot.android.feature.chat.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppChatRuntimeServiceContractTest {
    private val projectRoot = detectProjectRoot()

    @Test
    fun service_uses_injected_runtime_context_resolver_and_orchestrator_port() {
        val text = productionFile("feature/chat/runtime/AppChatRuntimeService.kt").readText()

        assertTrue(
            "AppChatRuntimeService must resolve context through AppChatRuntimeBindings.",
            text.contains("chatDependencies.runtimeContextResolverPort.resolve("),
        )
        assertTrue(
            "AppChatRuntimeService must depend on RuntimeLlmOrchestratorPort.",
            text.contains("import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort"),
        )
        assertFalse(
            "AppChatRuntimeService must not import the static RuntimeContextResolver.",
            text.contains("import com.elymbot.android.core.runtime.context.RuntimeContextResolver"),
        )
        assertFalse(
            "AppChatRuntimeService must not import the RuntimeOrchestrator compatibility shell.",
            text.contains("import com.elymbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
    }

    @Test
    fun runtime_helper_services_exist_after_feature_module_extraction() {
        val required = listOf(
            "feature/chat/runtime/AppChatProviderInvocationService.kt",
            "feature/chat/runtime/AppChatPreparedReplyService.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
        )

        val missing = required.filterNot { productionFileOrNull(it)?.exists() == true }

        assertTrue("Missing App Chat runtime helper services: $missing", missing.isEmpty())
    }

    @Test
    fun service_delegates_provider_invocation_after_helper_extraction() {
        val text = productionFile("feature/chat/runtime/AppChatRuntimeService.kt").readText()

        assertFalse(
            "AppChatRuntimeService must not call sendConfiguredChatWithTools directly.",
            text.contains("sendConfiguredChatWithTools("),
        )
        assertFalse(
            "AppChatRuntimeService must not call sendConfiguredChatStreamWithTools directly.",
            text.contains("sendConfiguredChatStreamWithTools("),
        )
        assertTrue(
            "AppChatRuntimeService should route provider calls through AppChatProviderInvocationService.",
            text.contains("providerInvocationService.invokeProvider("),
        )
    }

    private fun productionFile(relativePath: String): File =
        productionFileOrNull(relativePath) ?: error("Missing production file: $relativePath")

    private fun productionFileOrNull(relativePath: String): File? {
        val roots = listOf(
            File(projectRoot, "feature/chat/runtime/src/main/java/com/elymbot/android"),
            File(projectRoot, "app/src/main/java/com/elymbot/android"),
            File(projectRoot, "src/main/java/com/elymbot/android"),
        )
        return roots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { file -> file.exists() }
    }

    private fun detectProjectRoot(): File {
        var current = File("").absoluteFile
        while (current.parentFile != null) {
            if (current.resolve("settings.gradle.kts").exists() ||
                current.resolve("settings.gradle").exists()
            ) {
                return current
            }
            current = current.parentFile
        }
        error("Unable to locate project root from ${File("").absolutePath}")
    }
}
