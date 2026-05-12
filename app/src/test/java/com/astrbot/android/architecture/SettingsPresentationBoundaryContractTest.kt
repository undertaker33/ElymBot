package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsModuleRoot: Path = projectRoot.resolve("feature/settings/presentation")
    private val legacyAppSettingsRoot: Path =
        projectRoot.resolve("app/src/main/java/com/astrbot/android/feature/settings/presentation")

    @Test
    fun settings_presentation_must_be_a_dedicated_feature_module() {
        val settingsGradle = projectRoot.resolve("settings.gradle.kts").readText()

        assertTrue(
            "settings.gradle.kts must include :feature:settings:presentation",
            settingsGradle.contains("include(\":feature:settings:presentation\")"),
        )
        assertTrue(
            "feature/settings/presentation/build.gradle.kts must exist",
            settingsModuleRoot.resolve("build.gradle.kts").exists(),
        )
    }

    @Test
    fun settings_presentation_must_not_import_runtime_or_persistence_implementations() {
        val violations = settingsPresentationFiles()
            .flatMap { file ->
                file.readText()
                    .lineSequence()
                    .mapNotNull { line -> importRegex.matchEntire(line.trim())?.groupValues?.get(1) }
                    .filter(::isForbiddenSettingsPresentationImport)
                    .map { importName -> "${projectRoot.relativize(file).toString().replace('\\', '/')} imports $importName" }
                    .toList()
            }

        assertTrue(
            "Settings presentation must stay as entry aggregation/UI intent layer and must not import " +
                "backup/cache/container/plugin/runtime implementation or DAO types: $violations",
            violations.isEmpty(),
        )
    }

    private fun settingsPresentationFiles(): List<Path> {
        val roots = listOf(
            settingsModuleRoot.resolve("src/main/java"),
            legacyAppSettingsRoot,
        ).filter { root -> root.exists() }

        return roots.flatMap { root ->
            Files.walk(root).use { stream ->
                stream
                    .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                    .toList()
            }
        }
    }

    private fun isForbiddenSettingsPresentationImport(importName: String): Boolean {
        return importName == "com.astrbot.android.data.db" ||
            importName.startsWith("com.astrbot.android.data.db.") ||
            importName.startsWith("com.astrbot.android.core.db.backup.") ||
            importName.startsWith("com.astrbot.android.core.runtime.cache.") ||
            importName.startsWith("com.astrbot.android.core.runtime.container.") ||
            importName.startsWith("com.astrbot.android.feature.plugin.runtime.") ||
            importName.startsWith("com.astrbot.android.feature.plugin.data.") ||
            importName.startsWith("com.astrbot.android.runtime.")
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
        private val importRegex = Regex("""^import\s+([A-Za-z0-9_.*]+)$""")
    }
}
