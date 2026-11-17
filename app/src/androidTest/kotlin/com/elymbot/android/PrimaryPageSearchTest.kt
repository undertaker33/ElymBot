package com.elymbot.android

import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.ui.PersonaCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryPageSearchTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture get() = fixtureRule.fixture
    private val service get() = (fixture.context.applicationContext as ElymBotApplication).uiService

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    @Test
    fun consumedGalleryScrollFoldsHeaderAndSearchReturnsToBrowsingPosition() {
        val prefix = fixture.prefix.takeLast(8)
        val ids = mutableListOf<String>()
        try {
            runBlocking {
                repeat(16) { index ->
                    ids += service.savePersona(PersonaCard(name = "$prefix-$index", favorite = true))
                }
            }
            compose.onNodeWithText(label(R.string.elym_favorites)).performClick()
            val grid = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            val trigger = description(R.string.elym_search)
            val compactWidth = trigger.fetchSemanticsNode().boundsInRoot.width
            val pixelsPerDp = with(compose.density) { 1.dp.toPx() }
            grid.performTouchInput {
                down(Offset(centerX, height * 0.78f))
                moveBy(Offset(0f, -42 * pixelsPerDp), delayMillis = 150)
            }
            val draggedWidth = trigger.fetchSemanticsNode().boundsInRoot.width
            assertTrue(draggedWidth > compactWidth)
            grid.performTouchInput {
                moveBy(Offset(0f, 18 * pixelsPerDp), delayMillis = 150)
            }
            val reversedWidth = trigger.fetchSemanticsNode().boundsInRoot.width
            assertTrue(reversedWidth in compactWidth..<draggedWidth)
            grid.performTouchInput {
                moveBy(Offset(0f, -120 * pixelsPerDp), delayMillis = 600)
                up()
            }
            heading(R.string.elym_persona).assertDoesNotExist()
            compose.onNodeWithText(label(R.string.elym_favorites)).assertDoesNotExist()
            val folded = trigger.fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val allowedMargins = with(compose.density) { 22.dp.toPx() }
            assertTrue(folded.width >= root.width - allowedMargins)
            assertEquals(62 * pixelsPerDp, folded.height, 1f)
            assertTrue(draggedWidth < folded.width)
            assertTrue(grid.fetchSemanticsNode().boundsInRoot.top < folded.bottom)

            val scroll = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            trigger.performClick()
            val search = description(R.string.elym_search_hint)
            search.performTextInput("$prefix-13")
            val result = compose.onNode(hasText("$prefix-13") and !hasSetTextAction()).assertIsDisplayed()
            assertTrue(result.fetchSemanticsNode().boundsInRoot.top >= search.fetchSemanticsNode().boundsInRoot.bottom)
            compose.onNode(hasText("$prefix-14") and !hasSetTextAction()).assertDoesNotExist()
            description(R.string.elym_close_search).performClick()
            assertEquals(scroll, grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)

            grid.performTouchInput {
                swipe(Offset(centerX, height * 0.27f), Offset(centerX, height * 0.75f), 600)
            }
            heading(R.string.elym_persona).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.elym_favorites)).assertIsDisplayed()
        } finally {
            runBlocking { ids.forEach { service.deletePersona(it) } }
        }
    }

    @Test
    fun emptyTabsRemainExpandedAndBackClosesSearchBeforeReturningToGallery() {
        val tabs = listOf(R.string.elym_platform to R.string.elym_no_platforms, R.string.elym_plugins to R.string.elym_no_plugins)
        tabs.forEach { (tab, empty) ->
            val tabLabel = label(tab)
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(tabLabel) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(tabLabel) and hasClickAction()).performClick()
            val message = compose.onNodeWithText(label(empty)).assertIsDisplayed()
            compose.onRoot().performTouchInput {
                swipe(Offset(centerX, height * 0.70f), Offset(centerX, height * 0.25f), 500)
            }
            heading(tab).assertIsDisplayed()
            message.assertIsDisplayed()
            description(R.string.elym_search).performClick()
            compose.onNode(hasContentDescription(label(R.string.elym_search)) and hasSetTextAction()).performTextInput("No matches")
            compose.onNodeWithText(label(R.string.elym_no_search_results)).assertIsDisplayed()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            heading(tab).assertIsDisplayed()
            message.assertIsDisplayed()
        }
    }

    @Test
    fun settingsSearchOnlyShowsMatchingActionAndCloseRestoresBoth() {
        compose.onNode(hasText(label(R.string.elym_settings)) and hasClickAction()).performClick()
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), 0.01f)
        scroll.performTouchInput {
            swipe(Offset(centerX, height * 0.70f), Offset(centerX, height * 0.25f), 500)
        }
        heading(R.string.elym_settings).assertIsDisplayed()
        description(R.string.elym_search).performClick()
        compose.onNode(hasContentDescription(label(R.string.elym_search)) and hasSetTextAction())
            .performTextInput(label(R.string.elym_appearance))
        val appearance = compose.onNode(hasText(label(R.string.elym_appearance)) and hasClickAction() and !hasSetTextAction())
        val commands = compose.onNode(hasText(label(R.string.elym_command_names)) and hasClickAction())
        appearance.assertIsDisplayed()
        commands.assertDoesNotExist()
        description(R.string.elym_close_search).performClick()
        appearance.assertIsDisplayed()
        commands.assertIsDisplayed()
    }

    private fun heading(resource: Int) = compose.onNode(
        hasText(label(resource)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
    )

    private fun description(resource: Int) = compose.onNodeWithContentDescription(label(resource))
    private fun label(resource: Int): String = fixture.context.getString(resource)
}
