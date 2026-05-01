package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeContextBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val runtimeContextBuildFile: Path = projectRoot.resolve("core/runtime-context/build.gradle.kts")
    private val runtimeContextRoot: Path =
        projectRoot.resolve("core/runtime-context/src/main/java")
    private val appHeldRuntimeContextRoot: Path =
        projectRoot.resolve("app/src/main/java/com/astrbot/android/core/runtime/context")

    @Test
    fun runtime_context_module_shell_must_be_registered_reported_and_transition_visible() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)

        assertTrue(
            "8-G must register :core:runtime-context in settings.gradle.kts.",
            settingsText.contains("""include(":core:runtime-context")"""),
        )
        assertTrue(
            "Architecture source roots must include core/runtime-context/src/main/java.",
            rootBuildText.contains("core/runtime-context/src/main/java"),
        )
        assertTrue(
            ":core:runtime-context build file must exist.",
            runtimeContextBuildFile.exists(),
        )
        assertTrue(
            "App must depend on :core:runtime-context during the DTO transition.",
            appBuildText.contains("""implementation(project(":core:runtime-context"))"""),
        )
    }

    @Test
    fun runtime_context_module_must_not_depend_on_app_feature_or_coarse_runtime() {
        assertTrue(
            ":core:runtime-context build file must exist before checking dependencies.",
            runtimeContextBuildFile.exists(),
        )

        val text = runtimeContextBuildFile.readText(UTF_8)
        val forbiddenDependencies = listOf(
            """:app"""",
            """:app-integration"""",
            """:feature:""",
            """:core:runtime"""",
        )
        val violations = forbiddenDependencies.filter(text::contains)

        assertTrue(
            ":core:runtime-context must not depend on app, app-integration, feature modules, or coarse :core:runtime: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_context_build_must_be_pure_kotlin_jvm_without_android_plugin_or_namespace() {
        assertTrue(
            ":core:runtime-context build file must exist before checking build purity.",
            runtimeContextBuildFile.exists(),
        )

        val text = runtimeContextBuildFile.readText(UTF_8)
        val forbiddenBuildTokens = listOf(
            "com.android.library",
            "org.jetbrains.kotlin.android",
            "android {",
            "namespace =",
        )
        val violations = forbiddenBuildTokens.filter(text::contains)

        assertTrue(
            ":core:runtime-context must be a pure Kotlin/JVM module and must not declare Android plugin/config: $violations",
            violations.isEmpty(),
        )
        assertTrue(
            ":core:runtime-context must apply the Kotlin JVM plugin.",
            text.contains("org.jetbrains.kotlin.jvm"),
        )
    }

    @Test
    fun runtime_context_sources_must_remain_pure_core_contracts() {
        val forbiddenTokens = listOf(
            "import com.astrbot.android.model",
            "import com.astrbot.android.feature",
            "import android.",
            "import androidx.compose",
            "import com.astrbot.android.AppStrings",
            "import com.astrbot.android.R",
            "AppStrings",
            "R.",
        )
        val violations = kotlinFilesUnder(runtimeContextRoot).flatMap { file ->
            val text = file.readText(UTF_8)
            forbiddenTokens
                .filter(text::contains)
                .map { token -> "${relativePath(file)} contains $token" }
        }

        assertTrue(
            ":core:runtime-context source must not import feature/app models, Android, Compose, AppStrings, or R: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_context_module_must_own_phase_8i_runtime_context_contracts() {
        val expectedCoreTypes = listOf(
            "PlatformRuntimeAdapter",
            "RuntimePlatform",
            "IngressTrigger",
            "SenderInfo",
            "RuntimeMessageType",
            "RuntimeConversationAttachment",
            "RuntimeConversationToolCall",
            "RuntimeConversationMessage",
            "RuntimeConversationSessionSnapshot",
            "RuntimeBotSnapshot",
            "RuntimeConfigSnapshot",
            "RuntimePersonaSnapshot",
            "RuntimePersonaToolEnablementSnapshot",
            "RuntimeProviderSnapshot",
            "RuntimeMcpServerSnapshot",
            "RuntimeLegacySkillSnapshot",
            "RuntimeResourceItemSnapshot",
            "RuntimeConfigResourceProjectionSnapshot",
            "RuntimeResourceCenterCompatibilitySnapshot",
            "ContextPolicy",
            "ProviderCapabilitySnapshot",
            "DeliveryPolicy",
            "PromptSkillProjection",
            "ToolSkillProjection",
            "RuntimeResourceProjectionSnapshot",
            "PromptSkillScope",
            "PromptSkillConflictPolicy",
            "ToolSourceContext",
            "RuntimeIngressEvent",
            "ResolvedRuntimeContext",
            "RuntimeContextDataPort",
            "RuntimeContextResolverPort",
            "DefaultRuntimeContextResolverPort",
            "RuntimeContextResolver",
            "RuntimeSkillProjectionResolver",
            "RuntimeStreamingMode",
            "StreamingModeResolver",
            "PromptAssembler",
            "SystemPromptBuilder",
            "RuntimeWebSearchPromptIntent",
            "RuntimeWebSearchPromptStringProvider",
            "RuntimeWebSearchPromptGuidance",
        )
        val coreSourceText = kotlinFilesUnder(runtimeContextRoot)
            .joinToString(separator = "\n") { file -> file.readText(UTF_8) }
        val missingTypes = expectedCoreTypes.filterNot { type ->
            coreSourceText.contains("class $type") ||
                coreSourceText.contains("interface $type") ||
                coreSourceText.contains("enum class $type") ||
                coreSourceText.contains("object $type")
        }

        assertTrue(
            "8-I must move runtime-context DTOs/contracts/services into :core:runtime-context: $missingTypes",
            missingTypes.isEmpty(),
        )
    }

    @Test
    fun app_must_not_keep_runtime_context_fake_core_residuals_after_dto_migration() {
        val actual = kotlinFilesUnder(appHeldRuntimeContextRoot)
            .map { file -> appHeldRuntimeContextRoot.relativize(file).toString().replace('\\', '/') }
            .sorted()

        assertEquals(
            "8-I runtime-context migration must leave no production Kotlin files in app/src/main/java/.../core/runtime/context.",
            emptyList<String>(),
            actual,
        )
    }

    @Test
    fun app_runtime_context_adapter_seams_must_be_explicit_and_outside_fake_core_path() {
        val adapterRoot = projectRoot.resolve("app/src/main/java/com/astrbot/android/di/runtime/context")
        val adapterFiles = kotlinFilesUnder(adapterRoot)
            .map { file -> adapterRoot.relativize(file).toString().replace('\\', '/') }
            .sorted()

        assertTrue(
            "8-I may keep app/feature model mapping only as explicit di/runtime/context adapter seams.",
            adapterFiles.contains("RuntimeContextModelAdapters.kt"),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return projectRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private companion object {
        const val runtimeContextResidualExpiry = "phase-08-i-runtime-context-dto-migration-complete"
    }
}
