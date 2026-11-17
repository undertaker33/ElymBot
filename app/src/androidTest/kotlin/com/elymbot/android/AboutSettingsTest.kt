package com.elymbot.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Suppress("DEPRECATION")
    fun searchableAboutShowsInstalledVersionAndBothBackActionsReturnToSettings() {
        val activity = compose.activity
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        val settings = hasText(activity.getString(R.string.elym_settings))
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val about = hasText(activity.getString(R.string.elym_about))
        val search = activity.getString(R.string.elym_search)
        val aboutEntry = compose.onNode(about and hasClickAction() and !hasSetTextAction())

        compose.onNode(settings and hasClickAction()).performClick()
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNode(hasContentDescription(search) and hasSetTextAction())
            .performTextInput(activity.getString(R.string.elym_about))
        aboutEntry.assertIsDisplayed().performClick()
        compose.onNode(about and heading).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.app_name)).assertIsDisplayed()
        val versionLabel = activity.getString(R.string.elym_about_version, checkNotNull(version))
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(versionLabel)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(versionLabel).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.elym_about_mit)).assertIsDisplayed()

        compose.onNodeWithContentDescription(activity.getString(R.string.elym_back)).performClick()
        compose.onNode(settings and heading).assertIsDisplayed()
        aboutEntry.performClick()
        compose.onNode(about and heading).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(settings and heading).assertIsDisplayed()
    }
}
