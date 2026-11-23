package com.elymbot.android

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSessionIntegrationTest {
    private val appFixtureRule = AndroidTestAppFixtureRule()
    private val replyFixtureRule = ReplyHostFixtureRule(appFixture = { appFixtureRule.fixture })
    private val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(appFixtureRule).around(replyFixtureRule).around(activityRule)

    @Test
    fun mainActivityKeepsDurableRepliesScopedToTheirSessions() {
        val promptA = "session-a-${UUID.randomUUID().toString().take(8)}"
        val replyA = "reply-a-${UUID.randomUUID().toString().take(8)}"
        replyFixtureRule.server.prepare(listOf(ReplyItem(replyA, 0)))
        sendPromptFromSessions(promptA)

        val sessionA = replyFixtureRule.awaitSession(promptA)
        val firstRequest = replyFixtureRule.awaitInitialRequest(sessionA.id, turnIndex = 0)
        assertTrue(firstRequest.contains("\"stream\":false"))
        assertTrue(firstRequest.contains(promptA))
        replyFixtureRule.server.awaitFollowUpRequest(turnIndex = 0)
        replyFixtureRule.server.releaseFinalResponse(turnIndex = 0)
        val turnA = replyFixtureRule.awaitCompleted(sessionA.id)
        waitForText(activityRule, replyA)

        activityRule.onNodeWithContentDescription(activityRule.activity.getString(R.string.elym_back)).performClick()
        waitForText(activityRule, activityRule.activity.getString(R.string.elym_sessions))

        val promptB = "session-b-${UUID.randomUUID().toString().take(8)}"
        val replyB = "reply-b-${UUID.randomUUID().toString().take(8)}"
        replyFixtureRule.server.prepare(listOf(ReplyItem(replyB, 0)))
        enterNewSessionPrompt(promptB)

        val sessionB = replyFixtureRule.awaitSession(promptB)
        val secondRequest = replyFixtureRule.awaitInitialRequest(sessionB.id, turnIndex = 1)
        assertTrue(secondRequest.contains("\"stream\":false"))
        assertTrue(secondRequest.contains(promptB))
        replyFixtureRule.server.awaitFollowUpRequest(turnIndex = 1)
        replyFixtureRule.server.releaseFinalResponse(turnIndex = 1)
        val turnB = replyFixtureRule.awaitCompleted(sessionB.id)
        waitForText(activityRule, replyB)

        assertEquals(listOf(promptA, replyA), replyFixtureRule.history(sessionA.id, turnA.sequence).map { it.text })
        assertEquals(listOf(promptB, replyB), replyFixtureRule.history(sessionB.id, turnB.sequence).map { it.text })
        assertTrue(activityRule.onAllNodesWithText(replyA, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(activityRule.onAllNodesWithText(replyB, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun sendPromptFromSessions(prompt: String) {
        val strings = activityRule.activity
        waitForText(activityRule, replyFixtureRule.personaName)
        activityRule.onNodeWithText(replyFixtureRule.personaName).performClick()
        waitForDescription(activityRule, strings.getString(R.string.elym_chat_menu))
        activityRule.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        activityRule.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(activityRule, strings.getString(R.string.elym_sessions))
        enterNewSessionPrompt(prompt)
    }

    private fun enterNewSessionPrompt(prompt: String) {
        val strings = activityRule.activity
        openNewSession(activityRule, strings.getString(R.string.elym_new_session))
        activityRule.onNodeWithContentDescription(strings.getString(R.string.elym_type_message)).performTextInput(prompt)
        activityRule.onNodeWithContentDescription(strings.getString(R.string.elym_send)).performClick()
    }

    private fun openNewSession(compose: ComposeContentTestRule, label: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(label).performClick()
        } else {
            compose.onNodeWithText(label).performClick()
        }
    }

    private fun waitForDescription(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
