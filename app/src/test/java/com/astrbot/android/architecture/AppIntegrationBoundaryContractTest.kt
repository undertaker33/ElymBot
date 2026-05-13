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
    private val appMainRoot: Path =
        projectRoot.resolve("app/src/main/java")
    private val appIntegrationRoot: Path =
        projectRoot.resolve("app-integration/src/main/java")
    private val appIntegrationBuildFile: Path =
        projectRoot.resolve("app-integration/build.gradle.kts")
    private val settingsApiMainRoot: Path =
        projectRoot.resolve("feature/settings/api/src/main/java")

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
        val allowedExactNames = setOf(
            "PluginDataWiringFactory.kt",
        )

        val violations = kotlinFilesUnder(appIntegrationRoot)
            .map { file -> relativePath(file) }
            .filterNot { path ->
                path.substringAfterLast("/") in allowedExactNames ||
                    allowedNameSuffixes.any(path::endsWith)
            }

        assertTrue(
            "app-integration files must stay wiring-shaped, not business-shaped. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_gradle_must_not_pull_presentation_or_compose() {
        val text = appIntegrationBuildFile.readText()
        val forbiddenPatterns = mapOf(
            "feature presentation dependency" to Regex("""project\(":feature:[^"]+:presentation"\)"""),
            "compose dependency" to Regex("""androidx\.compose|compose\s*="""),
            "android application plugin" to Regex("""com\.android\.application"""),
        )
        val violations = forbiddenPatterns
            .filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .keys
            .toList()

        assertTrue(
            "app-integration Gradle wiring must stay free of presentation, Compose, and app-shell ownership: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_gradle_must_not_expose_feature_implementation_modules_as_api() {
        val text = appIntegrationBuildFile.readText()
        val violations = Regex("""api\(project\("(:feature:[^"]+:(data|runtime|impl))"\)\)""")
            .findAll(text)
            .map { match -> match.groupValues[1] }
            .toList()

        assertTrue(
            "app-integration may wire feature implementation modules internally, but must not leak them to :app through api(project(...)): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_must_not_expose_hidden_any_runtime_seams() {
        val violations = kotlinFilesUnder(appIntegrationRoot)
            .flatMap { file ->
                val text = file.readText()
                val forbiddenTokens = listOf(
                    "chatDependencies: Any",
                    "dependencies: Any",
                    "): Any",
                )
                forbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${relativePath(file)} contains $token" }
            }

        assertTrue(
            "app-integration must expose typed API contracts instead of Any seams that app casts back to runtime implementations: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_must_not_own_core_backup_contract_packages() {
        val violations = kotlinFilesUnder(appIntegrationRoot)
            .filter { file ->
                file.readText().contains("package com.astrbot.android.core.db.backup")
            }
            .map(::relativePath)

        assertTrue(
            "Backup data port contracts must live in their real owner module/package, not app-integration pretending to be core.db.backup: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun backup_data_port_contracts_must_be_owned_by_app_backup_not_app_integration() {
        val appBackupContract = settingsApiMainRoot
            .resolve("com/astrbot/android/feature/settings/api/backup/AppBackupDataPort.kt")
        val conversationBackupContract = settingsApiMainRoot
            .resolve("com/astrbot/android/feature/settings/api/backup/ConversationBackupDataPort.kt")
        val staleCorePackageContracts = kotlinFilesUnder(
            settingsApiMainRoot.resolve("com/astrbot/android/core/db/backup"),
        ).map(::relativePath)
        val appImportViolations = kotlinFilesUnder(appMainRoot)
            .filter { file -> file.readText().contains("com.astrbot.android.app.integration.backup") }
            .map(::relativePath)
        val appIntegrationContractViolations = kotlinFilesUnder(appIntegrationRoot)
            .filter { file ->
                val text = file.readText()
                text.startsWith("package com.astrbot.android.app.integration.backup") &&
                    (text.contains("interface ") || text.contains("data class "))
            }
            .map(::relativePath)
        val appIntegrationBackupImports = kotlinFilesUnder(appIntegrationRoot)
            .filter { file ->
                file.readText().contains("com.astrbot.android.feature.settings.api.backup.AppBackupDataPort")
            }
            .map(::relativePath)

        assertTrue(
            "feature/settings/api backup contracts must not live under a core/db/backup package path: $staleCorePackageContracts",
            staleCorePackageContracts.isEmpty(),
        )
        assertTrue(
            "App backup data port contract must live in the settings api owner package, outside app-integration.",
            appBackupContract.exists() &&
                appBackupContract.readText().contains("package com.astrbot.android.feature.settings.api.backup") &&
                appBackupContract.readText().contains("interface AppBackupDataPort"),
        )
        assertTrue(
            "Conversation backup data port contract must live in the settings api owner package, outside app-integration.",
            conversationBackupContract.exists() &&
                conversationBackupContract.readText().contains(
                    "package com.astrbot.android.feature.settings.api.backup",
                ) &&
                conversationBackupContract.readText().contains("interface ConversationBackupDataPort"),
        )
        assertTrue(
            "App main source must not import app-integration backup contract/DTO packages: $appImportViolations",
            appImportViolations.isEmpty(),
        )
        assertTrue(
            "app-integration may implement backup adapters, but must not own backup business contract/DTO declarations: $appIntegrationContractViolations",
            appIntegrationContractViolations.isEmpty(),
        )
        assertTrue(
            "app-integration backup wiring must import the backup owner contract.",
            appIntegrationBackupImports.isNotEmpty(),
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
