package com.elymbot.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchableFaqFiltersAnswersAndModelConfigurationReturnsToFaq() {
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val faq = label(R.string.elym_faq)
        val settingsSearch = label(R.string.elym_search)
        val modelsQuestion = label(R.string.elym_faq_models_question)
        val modelsAnswer = label(R.string.elym_faq_models_answer)
        val setupQuestion = label(R.string.elym_faq_setup_question)
        val setupAnswer = label(R.string.elym_faq_setup_answer)
        val setupTitle = label(R.string.elym_guide_title)
        val setupLink = label(R.string.elym_faq_open_setup)
        val faqEntry = compose.onNode(hasText(faq) and hasClickAction() and !hasSetTextAction())
        val aboutEntry = compose.onNode(hasText(label(R.string.elym_about)) and hasClickAction() and !hasSetTextAction())
        val configurationTab = compose.onNode(hasText(label(R.string.elym_config)) and hasClickAction())
        val models = compose.onNode(hasText(label(R.string.elym_config_models)) and isSelectable())
        val tools = compose.onNode(hasText(label(R.string.elym_config_tools)) and isSelectable())

        configurationTab.performClick()
        tools.performClick().assertIsSelected()
        compose.onNode(hasText(label(R.string.elym_settings)) and hasClickAction()).performClick()
        val faqBounds = faqEntry.assertIsDisplayed().getUnclippedBoundsInRoot()
        val aboutBounds = aboutEntry.assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(aboutBounds.top - faqBounds.bottom in 0.dp..2.dp)
        compose.onNodeWithContentDescription(settingsSearch).performClick()
        compose.onNode(hasContentDescription(settingsSearch) and hasSetTextAction()).performTextInput(faq)
        aboutEntry.assertDoesNotExist()
        faqEntry.performClick()
        compose.onNode(hasText(faq) and heading).assertIsDisplayed()

        compose.onNode(hasText(setupQuestion) and hasClickAction()).performClick()
        compose.onNodeWithText(setupAnswer).assertIsDisplayed()
        compose.onNode(hasText(modelsQuestion) and hasClickAction()).performClick()
        compose.onNodeWithText(setupAnswer).assertDoesNotExist()
        compose.onNodeWithText(modelsAnswer).assertIsDisplayed()

        compose.onNodeWithText(label(R.string.elym_faq_models)).performClick()
        compose.onNodeWithText(setupQuestion).assertDoesNotExist()
        val faqSearch = label(R.string.elym_faq_search)
        val searchField = compose.onNode(hasContentDescription(faqSearch) and hasSetTextAction())
        searchField.performTextInput(modelsAnswer.takeLast(10))
        compose.onNode(hasText(modelsQuestion) and hasClickAction()).performClick()
        val openModels = compose.onNodeWithText(label(R.string.elym_faq_open_models))
        openModels.performClick()
        compose.onNode(hasText(label(R.string.elym_config)) and heading).assertIsDisplayed()
        models.assertIsSelected()
        tools.performClick().assertIsSelected()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasText(faq) and heading).assertIsDisplayed()
        compose.onNodeWithText(modelsAnswer).assertIsDisplayed()
        compose.onNodeWithText(setupQuestion).assertDoesNotExist()
        openModels.performClick()
        models.assertIsSelected()
        compose.onNodeWithContentDescription(label(R.string.elym_back)).performClick()
        compose.onNode(hasText(faq) and heading).assertIsDisplayed()

        searchField.performTextReplacement("")
        compose.onNodeWithText(label(R.string.elym_faq_getting_started)).performClick()
        compose.onNode(hasText(setupQuestion) and hasClickAction()).performClick()
        compose.onNodeWithText(setupLink).performClick()
        compose.onNode(hasText(setupTitle) and heading).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.elym_guide_later)).performClick()
        compose.onNode(hasText(faq) and heading).assertIsDisplayed()
        compose.onNodeWithText(setupLink).performClick()
        compose.onNode(hasText(setupTitle) and heading).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.elym_back)).performClick()
        compose.onNode(hasText(faq) and heading).assertIsDisplayed()
        compose.onNodeWithText(setupAnswer).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.elym_back)).performClick()
        configurationTab.performClick()
        tools.assertIsSelected()
    }

    private fun label(resource: Int) = compose.activity.getString(resource)
}
