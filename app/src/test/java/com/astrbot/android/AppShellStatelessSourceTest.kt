package com.astrbot.android

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellStatelessSourceTest {

    private val projectRoot: Path = detectProjectRoot()

    @Test
    fun app_strings_must_not_cache_application_context_globally() {
        val text = projectRoot
            .resolve("app/src/main/java/com/astrbot/android/AppStrings.kt")
            .readText()

        assertFalse(
            "AppStrings must be a stateless helper or injected resolver; do not cache application Context globally.",
            text.contains("@Volatile") || text.contains("appContext") || text.contains("fun initialize("),
        )
        assertTrue(
            "AppStrings should keep only resource resolution helpers backed by an explicit Context or resolver.",
            text.contains("class AppStringResolver") || text.contains("fun get(context: Context"),
        )
    }

    @Test
    fun app_ui_transition_state_must_be_navigation_scoped_not_process_global() {
        val transitionStateText = projectRoot
            .resolve("app/src/main/java/com/astrbot/android/ui/navigation/AppUiTransitionState.kt")
            .readText()
        val effectsText = projectRoot
            .resolve("app/src/main/java/com/astrbot/android/ui/common/AppUiEffects.kt")
            .readText()

        assertFalse(
            "AppUiTransitionState must not be a global Kotlin object with process-wide mutable UI state.",
            transitionStateText.contains("object AppUiTransitionState") || transitionStateText.contains("@Volatile"),
        )
        assertTrue(
            "AppUiTransitionState should be a holder class remembered/provided from navigation or app composition.",
            transitionStateText.contains("class AppUiTransitionState") &&
                transitionStateText.contains("rememberAppUiTransitionState"),
        )
        assertTrue(
            "Transition effects should receive the scoped holder explicitly instead of reaching for a global object.",
            effectsText.contains("transitionState: AppUiTransitionState"),
        )
    }

    @Test
    fun app_shell_logging_must_use_injected_runtime_logger() {
        val appShellSources = listOf(
            "app/src/main/java/com/astrbot/android/MainActivity.kt",
            "app/src/main/java/com/astrbot/android/di/startup/BootstrapPrerequisitesStartupChain.kt",
            "app/src/main/java/com/astrbot/android/di/startup/RuntimeLaunchStartupChain.kt",
        )

        val violations = appShellSources.filter { relativePath ->
            projectRoot.resolve(relativePath).readText().contains("SharedRuntimeLogStore")
        }

        assertTrue(
            "App shell/startup sources must use injected RuntimeLogger instead of SharedRuntimeLogStore: $violations",
            violations.isEmpty(),
        )
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
