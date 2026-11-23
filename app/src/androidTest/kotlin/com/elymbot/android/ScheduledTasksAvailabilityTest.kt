package com.elymbot.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledTasksAvailabilityTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    @Test
    fun scheduledTaskEntryFollowsMainAuthorizationAndIsAbsentInDedicatedChat() {
        val fixture = fixtureRule.fixture
        val service = (fixture.context.applicationContext as ElymBotApplication).uiService
        val tasksLabel = label(R.string.elym_scheduled_tasks)
        val chatMenu = compose.onNodeWithContentDescription(label(R.string.elym_chat_menu))
        val back = compose.onNodeWithContentDescription(label(R.string.elym_back))
        val scheduledTasks = compose.onNodeWithText(tasksLabel)
        val available = runBlocking {
            withTimeout(10_000) {
                val mainId = checkNotNull(service.mainSession.first { it != null }).id
                service.pluginManagement.first { it.restorationComplete }
                service.scheduledTasksAvailable(mainId).first()
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodes(hasText(fixture.personaName) and hasClickAction()).fetchSemanticsNodes().size == 1 }
        compose.onNode(hasText(fixture.personaName) and hasClickAction()).performClick()
        chatMenu.performClick()
        if (available) {
            scheduledTasks.assertIsDisplayed().performClick()
            scheduledTasks.assertIsDisplayed()
            back.performClick()
            chatMenu.performClick()
        } else scheduledTasks.assertDoesNotExist()

        compose.onNodeWithText(label(R.string.elym_sessions)).performClick()
        val create = label(R.string.elym_new_session)
        val createIcons = compose.onAllNodes(hasContentDescription(create))
        if (createIcons.fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription(create).performClick()
        else compose.onNodeWithText(create).performClick()
        chatMenu.performClick()
        scheduledTasks.assertDoesNotExist()
    }

    private fun label(resource: Int) = compose.activity.getString(resource)
}
