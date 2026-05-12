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

class ModuleDependencyGraphContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appIntegrationBuildFile: Path = projectRoot.resolve("app-integration/build.gradle.kts")

    private val phase3Modules = listOf(
        ":core:common",
        ":core:network",
        ":core:runtime-audio",
        ":core:runtime-cache",
        ":core:runtime-container",
        ":core:runtime-context",
        ":core:runtime-llm",
        ":core:runtime-search",
        ":core:runtime-secret",
        ":core:runtime-session",
        ":core:runtime-tool",
        ":download:api",
        ":download:impl",
        ":feature:bot:api",
        ":feature:bot:data",
        ":feature:config:api",
        ":feature:config:data",
        ":feature:cron:api",
        ":feature:persona:api",
        ":feature:persona:data",
        ":feature:plugin:api",
        ":feature:provider:api",
        ":feature:provider:data",
        ":feature:resource:api",
        ":feature:resource:data",
    )

    private val phase14Modules = listOf(
        ":feature:conversation:api",
        ":feature:conversation:data",
        ":feature:chat:runtime",
        ":feature:chat:presentation",
    )

    private val phase18To20Modules = listOf(
        ":app-integration",
        ":core:backup",
        ":core:logging",
        ":core:runtime-container",
        ":core:runtime-search",
        ":feature:qq:api",
        ":feature:qq:impl",
        ":feature:cron:runtime",
        ":feature:settings:api",
        ":feature:settings:presentation",
        ":feature:voiceasset:api",
    )

    private val phase22PluginModules = listOf(
        ":feature:plugin:data",
        ":feature:plugin:presentation",
        ":feature:plugin:runtime",
    )

    private val phase23AppShellAllowedTransitionalDependencies = mapOf(
        ":feature:voiceasset:api" to TransitionalDependency(
            owner = "feature-voiceasset",
            target = "phase-25 voiceasset data/presentation split",
            reason = "Voice asset UI still keeps an app compatibility entry until the dedicated data/presentation split lands.",
            expires = "phase-25",
            issue = "module-split-phase-25",
        ),
    )

    private val phase23ForbiddenAppShellDependencies = listOf(
        ":download:impl",
        ":feature:plugin:data",
        ":feature:cron:data",
        ":feature:cron:runtime",
        ":feature:resource:data",
        ":core:runtime-secret",
        ":core:runtime-tool",
        ":feature:qq:data",
        ":feature:qq:impl",
        ":feature:qq:runtime",
    )

    @Test
    fun phase3_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase3Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 3 Gradle modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase14_conversation_and_chat_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase14Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 14 conversation/chat Gradle modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase18_to_20_terminal_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase18To20Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 18-20 terminal modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase22_plugin_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase22PluginModules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 22 plugin data/runtime/presentation modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase22_plugin_modules_must_be_in_architecture_source_roots() {
        val text = rootBuildFile.readText(UTF_8)
        val missing = phase22PluginModules
            .map(::sourceRootForModule)
            .filterNot { sourceRoot -> text.contains("\"$sourceRoot\"") }

        assertTrue(
            "Phase 22 plugin module source roots must be scanned by architecture contracts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase18_to_20_terminal_modules_must_be_in_architecture_source_roots() {
        val text = rootBuildFile.readText(UTF_8)
        val missing = phase18To20Modules
            .map(::sourceRootForModule)
            .filterNot { sourceRoot -> text.contains("\"$sourceRoot\"") }

        assertTrue(
            "Phase 18-20 terminal module source roots must be scanned by architecture contracts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun app_shell_must_depend_on_app_integration_module() {
        val text = appBuildFile.readText(UTF_8)

        assertTrue(
            "App shell must depend on :app-integration for production wiring.",
            text.contains("""implementation(project(":app-integration"))""") ||
                text.contains("""api(project(":app-integration"))"""),
        )
    }

    @Test
    fun app_integration_must_not_depend_on_feature_presentation_modules() {
        val text = appIntegrationBuildFile.readText(UTF_8)
        val violations = Regex("""project\(":feature:[^"]+:presentation"\)""")
            .findAll(text)
            .map { match -> match.value }
            .toList()

        assertTrue(
            "app-integration is wiring only and must not depend on feature presentation modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun phase23_app_shell_project_dependency_count_must_drop() {
        val dependencies = projectDependencies(appBuildFile)

        assertTrue(
            "Phase 23 app shell must reduce direct project dependencies to at most $PHASE23_APP_DEPENDENCY_LIMIT, found ${dependencies.size}: $dependencies",
            dependencies.size <= PHASE23_APP_DEPENDENCY_LIMIT,
        )
    }

    @Test
    fun phase23_app_shell_must_not_keep_split_feature_data_runtime_or_old_impl_dependencies() {
        val dependencies = projectDependencies(appBuildFile)
        val oldImplDependencies = dependencies
            .filter { dependency -> Regex(""":feature:[^:]+:impl$""").matches(dependency) }
            .filterNot(phase23AppShellAllowedTransitionalDependencies::containsKey)
        val splitDataRuntimeDependencies = phase23ForbiddenAppShellDependencies.filter(dependencies::contains)

        assertTrue(
            "Phase 23 app shell must not directly depend on old feature impl modules except precise QQ transition: $oldImplDependencies",
            oldImplDependencies.isEmpty(),
        )
        assertTrue(
            "Phase 23 app shell must move split feature data/runtime and core runtime implementation dependencies behind app-integration: $splitDataRuntimeDependencies",
            splitDataRuntimeDependencies.isEmpty(),
        )
    }

    @Test
    fun phase23_remaining_app_shell_transitional_dependencies_must_be_precise() {
        val malformed = phase23AppShellAllowedTransitionalDependencies.filter { (_, entry) ->
            entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.owner.contains("*") ||
                entry.target.contains("*")
        }

        assertTrue(
            "Phase 23 app shell transition entries must be precise and documented: $malformed",
            malformed.isEmpty(),
        )

        val dependencies = projectDependencies(appBuildFile)
        val stale = phase23AppShellAllowedTransitionalDependencies.keys.filterNot(dependencies::contains)
        assertEquals(
            "Remove stale Phase 23 app shell transition entries once the direct dependency is gone.",
            emptyList<String>(),
            stale,
        )
    }

    @Test
    fun core_modules_must_not_depend_on_feature_or_app_modules() {
        val violations = buildFilesUnder(projectRoot.resolve("core"))
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenCoreModuleDependencyPatterns
                    .filter { pattern -> pattern.containsMatchIn(text) }
                    .map { pattern -> "${relativePath(file)} -> ${pattern.pattern}" }
            }

        assertTrue(
            "Core modules must not depend on app or feature modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun foundational_core_modules_must_not_depend_on_runtime_or_business_modules() {
        val foundationalModules = listOf(
            "core/common/build.gradle.kts",
            "core/logging/build.gradle.kts",
            "core/network/build.gradle.kts",
            "core/runtime-audio/build.gradle.kts",
            "core/runtime-cache/build.gradle.kts",
            "core/runtime-container/build.gradle.kts",
            "core/runtime-context/build.gradle.kts",
            "core/runtime-llm/build.gradle.kts",
            "core/runtime-search/build.gradle.kts",
            "core/runtime-secret/build.gradle.kts",
            "core/runtime-session/build.gradle.kts",
            "core/runtime-tool/build.gradle.kts",
        )
        val forbiddenDependencyPatterns = mapOf(
            ":core:runtime" to Regex("""project\(":core:runtime"\)"""),
            ":app" to Regex("""project\(":app"\)"""),
            ":app-integration" to Regex("""project\(":app-integration"\)"""),
            ":feature:*" to Regex("""project\(":feature:"""),
        )
        val violations = foundationalModules
            .map { path -> projectRoot.resolve(path) }
            .filter { file -> file.exists() }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenDependencyPatterns
                    .filter { (_, pattern) -> pattern.containsMatchIn(text) }
                    .map { (dependency, _) -> "${relativePath(file)} depends on $dependency" }
            }

        assertTrue(
            "Foundational core modules must stay independent from runtime/app/feature modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_api_modules_must_not_depend_on_app_or_impl_modules() {
        val violations = buildFilesUnder(projectRoot.resolve("feature"))
            .filter { file -> relativePath(file).contains("/api/build.gradle.kts") }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenFeatureApiDependencyPatterns
                    .filter { pattern -> pattern.containsMatchIn(text) }
                    .map { pattern -> "${relativePath(file)} -> ${pattern.pattern}" }
            }

        assertTrue(
            "Feature API modules must not depend on app or implementation modules: $violations",
            violations.isEmpty(),
        )
    }

    private fun buildFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }

        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString() == "build.gradle.kts" }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return projectRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun sourceRootForModule(module: String): String {
        return module.removePrefix(":").replace(':', '/') + "/src/main/java"
    }

    private fun projectDependencies(buildFile: Path): List<String> {
        val text = buildFile.readText(UTF_8)
        return Regex("""(?:api|implementation)\(project\("([^"]+)"\)\)""")
            .findAll(text)
            .map { match -> match.groupValues[1] }
            .toList()
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
        private const val PHASE23_APP_DEPENDENCY_LIMIT = 45

        val forbiddenCoreModuleDependencyPatterns = listOf(
            Regex("""project\(":app"\)"""),
            Regex("""project\(":feature:"""),
        )

        val forbiddenFeatureApiDependencyPatterns = listOf(
            Regex("""project\(":app"\)"""),
            Regex("""project\(":feature:[^"]+:impl"\)"""),
            Regex("""project\(":feature:[^"]+:data"\)"""),
            Regex("""project\(":feature:[^"]+:runtime"\)"""),
            Regex("""project\(":feature:[^"]+:presentation"\)"""),
        )
    }

    private data class TransitionalDependency(
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )
}
