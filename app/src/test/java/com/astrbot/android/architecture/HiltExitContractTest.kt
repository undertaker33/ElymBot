package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltExitContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = listOf(
        projectRoot.resolve("src/main/java/com/astrbot/android"),
        projectRoot.resolve("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun main_activity_must_be_android_entry_point() {
        val file = mainRoot.resolve("MainActivity.kt")
        assertTrue("MainActivity.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "MainActivity must be a Hilt Android entry point after phase 5",
            text.contains("@AndroidEntryPoint"),
        )
        assertTrue(
            "MainActivity must not read dependencies through the legacy app container",
            !text.contains("appContainer") && !text.contains("MainActivityDependencies"),
        )
    }

    @Test
    fun production_view_models_must_be_hilt_view_models() {
        val missing = productionViewModelFiles()
            .filterNot { file -> file.readText().contains("@HiltViewModel") }
            .map { file -> projectRoot.relativize(file).toString().replace('\\', '/') }

        assertTrue("Production ViewModels missing @HiltViewModel: $missing", missing.isEmpty())
    }

    @Test
    fun production_sources_must_not_use_legacy_view_model_helper_or_container_bridge() {
        val forbiddenTokens = listOf(
            "astrBotViewModel(",
            "LegacyContainerEntryPoint",
            "viewModelFactory",
            "DefaultBridgeViewModelDependencies",
            "DefaultBotViewModelDependencies",
            "DefaultProviderViewModelDependencies",
            "DefaultConfigViewModelDependencies",
            "DefaultConversationViewModelDependencies",
            "DefaultPersonaViewModelDependencies",
            "DefaultPluginViewModelDependencies",
            "DefaultQQLoginViewModelDependencies",
            "DefaultRuntimeAssetViewModelDependencies",
            "DefaultChatViewModelDependencies",
            "DefaultMainActivityDependencies",
            "MainActivityDependencies",
        )
        val violations = kotlinFilesUnder(mainRoot)
            .filterNot { file ->
                mainRoot.relativize(file).toString().replace('\\', '/') in allowedMentionFiles
            }
            .flatMap { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText()
                forbiddenTokens.mapNotNull { token ->
                    if (text.contains(token)) "$relative contains $token" else null
                }
            }

        assertTrue("Legacy DI tokens still present in production sources: $violations", violations.isEmpty())
    }

    @Test
    fun runtime_seams_must_stay_confined_to_explicit_entry_files() {
        assertTokenAbsentFromProductionFiles(
            "EntryPointAccessors.fromApplication(",
        )
        assertTokenAbsentFromProductionFiles(
            "EntryPoints.get(",
        )
        assertTokenAbsentFromProductionFiles(
            "installRuntimeDependencies(",
        )
        assertTokenAbsentFromProductionFiles(
            "installRuntimeDependencies =",
        )
        assertTokenAbsentFromProductionFiles(
            "RuntimeDependenciesTestInstaller",
        )
        assertTokenAbsentFromProductionFiles(
            "updateRuntimeDependenciesForTests(",
        )
    }

    @Test
    fun application_must_not_create_legacy_container() {
        val file = mainRoot.resolve("AstrBotApplication.kt")
        assertTrue("AstrBotApplication.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "AstrBotApplication must remain @HiltAndroidApp",
            text.contains("@HiltAndroidApp"),
        )
        assertTrue(
            "AstrBotApplication must implement WorkManager Configuration.Provider for HiltWorkerFactory",
            text.contains("Configuration.Provider") && text.contains("HiltWorkerFactory"),
        )
        assertTrue(
            "AstrBotApplication must not manually create ElymBotAppContainer after phase 5",
            !text.contains("ElymBotAppContainer("),
        )
        val forbiddenStartupInjections = listOf(
            "QqBridgeRuntime",
            "ContainerRuntimeInstaller",
            "AppStartupRunner",
            "BootstrapPrerequisitesStartupChain",
            "RepositoryInitializationStartupChain",
            "ReferenceGuardStartupChain",
            "PluginRuntimeObservationStartupChain",
            "RuntimeLaunchStartupChain",
        )
        val violations = forbiddenStartupInjections.filter { token ->
            text.contains("lateinit var ${token.replaceFirstChar(Char::lowercase)}") ||
                text.contains(": $token")
        }
        assertTrue(
            "AstrBotApplication must keep startup ownership behind AppBootstrapper instead of injecting startup chains directly: $violations",
            violations.isEmpty(),
        )
    }

    private fun productionViewModelFiles(): List<Path> {
        return listOf(
            "app/src/main/java/com/astrbot/android/ui/viewmodel/BridgeViewModel.kt",
            "feature/bot/presentation/src/main/java/com/astrbot/android/feature/bot/presentation/BotViewModel.kt",
            "feature/provider/presentation/src/main/java/com/astrbot/android/feature/provider/presentation/ProviderViewModel.kt",
            "feature/config/presentation/src/main/java/com/astrbot/android/feature/config/presentation/ConfigViewModel.kt",
            "feature/chat/presentation/src/main/java/com/astrbot/android/feature/chat/presentation/ConversationViewModel.kt",
            "feature/persona/presentation/src/main/java/com/astrbot/android/feature/persona/presentation/PersonaViewModel.kt",
            "feature/plugin/presentation/src/main/java/com/astrbot/android/feature/plugin/presentation/PluginViewModel.kt",
            "feature/qq/presentation/src/main/java/com/astrbot/android/feature/qq/presentation/QQLoginViewModel.kt",
            "feature/chat/presentation/src/main/java/com/astrbot/android/feature/chat/presentation/ChatViewModel.kt",
            "feature/voiceasset/presentation/src/main/java/com/astrbot/android/ui/settings/RuntimeAssetViewModel.kt",
            "app/src/main/java/com/astrbot/android/ui/viewmodel/RuntimeLogViewModel.kt",
            "feature/cron/presentation/src/main/java/com/astrbot/android/ui/settings/CronJobsViewModel.kt",
        ).map(projectRoot::resolve)
            .onEach { file -> assertTrue("${projectRoot.relativize(file)} must exist", file.exists()) }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private companion object {
        val allowedMentionFiles = setOf(
            "di/hilt/ViewModelDependencyModule.kt",
        )
    }

    private fun assertTokenAbsentFromProductionFiles(
        token: String,
    ) {
        val actualPaths = kotlinFilesUnder(mainRoot)
            .filter { file -> file.readText().contains(token) }
            .map { file -> mainRoot.relativize(file).toString().replace('\\', '/') }
            .toSet()

        assertTrue(
            "Token '$token' must not appear in production sources. Found in: $actualPaths",
            actualPaths.isEmpty(),
        )
    }
}
