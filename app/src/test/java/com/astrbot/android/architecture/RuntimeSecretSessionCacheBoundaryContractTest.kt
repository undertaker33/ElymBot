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

class RuntimeSecretSessionCacheBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appIntegrationBuildFile: Path = projectRoot.resolve("app-integration/build.gradle.kts")
    private val coarseRuntimeRoot: Path =
        projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android/core/runtime")

    @Test
    fun runtime_secret_session_cache_modules_must_be_registered_reported_and_consumed() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)
        val appIntegrationBuildText = appIntegrationBuildFile.readText(UTF_8)

        expectedModules.forEach { module ->
            assertTrue(
                "$module must be registered in settings.gradle.kts.",
                settingsText.contains("""include("$module")"""),
            )
            assertTrue(
                "Architecture source roots must include ${module.toSourceRoot()}.",
                rootBuildText.contains(module.toSourceRoot()),
            )
            assertTrue(
                "App must depend on $module while app production call sites still compile from :app.",
                appBuildText.contains("""implementation(project("$module"))"""),
            )
            assertTrue(
                "app-integration must explicitly depend on $module for phase 10 runtime wiring visibility.",
                appIntegrationBuildText.contains("""implementation(project("$module"))"""),
            )
        }
    }

    @Test
    fun coarse_runtime_must_not_own_secret_session_or_cache_sources() {
        val residualFiles = listOf("secret", "session", "cache")
            .flatMap { directory ->
                kotlinFilesUnder(coarseRuntimeRoot.resolve(directory))
                    .map { file -> projectRoot.relativize(file).toString().replace('\\', '/') }
            }
            .sorted()

        assertEquals(
            "Secret/session/cache runtime sources must live in their dedicated core runtime modules.",
            emptyList<String>(),
            residualFiles,
        )
    }

    @Test
    fun production_hot_paths_must_not_call_legacy_static_secret_or_session_objects() {
        val forbiddenImports = listOf(
            "import com.astrbot.android.core.runtime.secret.AppSecretStore",
            "import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository",
            "import com.astrbot.android.core.runtime.session.ConversationSessionLockManager",
        )
        val violations = productionKotlinFiles()
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenImports.mapNotNull { token ->
                    if (text.contains(token)) "${relativePath(file)} contains $token" else null
                }
            }

        assertTrue(
            "Production secret/session hot paths must use injected RuntimeSecretStore or SessionLockCoordinator. " +
                "Found: $violations",
            violations.isEmpty(),
        )
    }

    private fun String.toSourceRoot(): String {
        return removePrefix(":").replace(':', '/') + "/src/main/java"
    }

    private fun productionKotlinFiles(): List<Path> {
        val roots = listOf(
            "app/src/main/java",
            "app-integration/src/main/java",
            "core/runtime-secret/src/main/java",
            "core/runtime-session/src/main/java",
            "core/runtime-cache/src/main/java",
            "feature/qq/impl/src/main/java",
        )
        return roots.map(projectRoot::resolve).flatMap(::kotlinFilesUnder)
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
        val expectedModules = listOf(
            ":core:runtime-secret",
            ":core:runtime-session",
            ":core:runtime-cache",
            ":core:runtime-container",
        )
    }
}
