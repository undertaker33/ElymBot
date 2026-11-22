package com.elymbot.android

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.data.AppearanceMode
import com.elymbot.android.data.AppearancePreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun appearanceChangesSystemBarContrastAndSurvivesRelaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppearancePreferences(context)
        val previousMode = preferences.read()
        preferences.write(AppearanceMode.SYSTEM)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openAppearance()
                appearanceOption(R.string.elym_appearance_system).assertIsSelected()
                assertSystemBarAppearance(scenario, AppearanceMode.SYSTEM)

                appearanceOption(R.string.elym_appearance_dark).performClick().assertIsSelected()
                assertSystemBarAppearance(scenario, AppearanceMode.DARK)
                appearanceOption(R.string.elym_appearance_light).performClick().assertIsSelected()
                assertSystemBarAppearance(scenario, AppearanceMode.LIGHT)
                appearanceOption(R.string.elym_appearance_dark).performClick().assertIsSelected()
            }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openAppearance()
                appearanceOption(R.string.elym_appearance_dark).assertIsSelected()
                assertSystemBarAppearance(scenario, AppearanceMode.DARK)
                appearanceOption(R.string.elym_appearance_system).performClick().assertIsSelected()
                assertSystemBarAppearance(scenario, AppearanceMode.SYSTEM)
            }
        } finally {
            preferences.write(previousMode)
        }
    }

    private fun openAppearance() {
        compose.onNode(hasText(label(R.string.elym_settings)) and hasClickAction()).performClick()
        compose.onNode(hasText(label(R.string.elym_appearance)) and hasClickAction()).performClick()
    }

    private fun appearanceOption(resource: Int) = compose.onNode(
        hasText(label(resource)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
    )

    private fun label(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun assertSystemBarAppearance(scenario: ActivityScenario<MainActivity>, mode: AppearanceMode) {
        scenario.onActivity { activity ->
            val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val light = when (mode) {
                AppearanceMode.SYSTEM -> nightMode != Configuration.UI_MODE_NIGHT_YES
                AppearanceMode.LIGHT -> true
                AppearanceMode.DARK -> false
            }
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals(light, controller.isAppearanceLightStatusBars)
            assertEquals(light, controller.isAppearanceLightNavigationBars)
        }
    }
}
