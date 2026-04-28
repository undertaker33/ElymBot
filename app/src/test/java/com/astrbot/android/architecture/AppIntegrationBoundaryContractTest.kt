package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntegrationBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val appIntegrationRoot: Path =
        projectRoot.resolve("app-integration/src/main/java")

    @Test
    fun app_integration_must_not_become_presentation_or_business_runtime() {
        val forbiddenTokens = listOf(
            "import androidx.compose.",
            "@Composable",
            "ProcessBuilder",
            "while (true)",
            "while(true)",
            "for (;;)",
        )

        val violations = kotlinFilesUnder(appIntegrationRoot)
            .flatMap { file ->
                val text = file.readText()
                forbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${relativePath(file)} contains $token" }
            }

        assertTrue(
            "app-integration is wiring only; do not put UI, process execution, or long-running loops there. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_files_must_be_named_as_wiring_artifacts() {
        val allowedNameSuffixes = listOf(
            "Adapter.kt",
            "Adapters.kt",
            "Binding.kt",
            "Bindings.kt",
            "Contributor.kt",
            "Contributors.kt",
            "Module.kt",
            "Modules.kt",
        )

        val violations = kotlinFilesUnder(appIntegrationRoot)
            .map { file -> relativePath(file) }
            .filterNot { path -> allowedNameSuffixes.any(path::endsWith) }

        assertTrue(
            "app-integration files must stay wiring-shaped, not business-shaped. Found: $violations",
            violations.isEmpty(),
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
            cwd.resolve("app/build.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("app/build.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
