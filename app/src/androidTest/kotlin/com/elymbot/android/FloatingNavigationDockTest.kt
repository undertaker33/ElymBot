package com.elymbot.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingNavigationDockTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun draggingCommitsNearestTabOnlyOnReleaseAndCancellationKeepsCurrentTab() {
        val gallery = tab(R.string.elym_persona)
        val plugins = tab(R.string.elym_plugins)
        val settings = tab(R.string.elym_settings)
        val galleryCenter = gallery.fetchSemanticsNode().boundsInRoot.center
        val pluginsCenter = plugins.fetchSemanticsNode().boundsInRoot.center
        val settingsCenter = settings.fetchSemanticsNode().boundsInRoot.center
        gallery.assertIsSelected().performTouchInput {
            down(center)
            moveBy(Offset(settingsCenter.x - galleryCenter.x, 0f), delayMillis = 300)
        }
        gallery.assertIsSelected()
        gallery.performTouchInput {
            moveBy(Offset(pluginsCenter.x - settingsCenter.x, 0f), delayMillis = 50)
            up()
        }
        plugins.assertIsSelected()
        compose.onNode(hasText(label(R.string.elym_no_plugins))).assertIsDisplayed()

        plugins.performTouchInput {
            down(center)
            moveBy(Offset(settingsCenter.x - pluginsCenter.x, 0f), delayMillis = 150)
            cancel()
        }
        plugins.assertIsSelected()
        settings.performClick().assertIsSelected()
        compose.onNode(hasText(label(R.string.elym_appearance))).assertIsDisplayed()
    }

    private fun tab(resource: Int) = compose.onNode(
        hasText(label(resource)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
    )

    private fun label(resource: Int) = compose.activity.getString(resource)
}
