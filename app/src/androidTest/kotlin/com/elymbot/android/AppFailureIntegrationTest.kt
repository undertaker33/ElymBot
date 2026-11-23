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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFailureIntegrationTest {
    private val appFixtureRule = AndroidTestAppFixtureRule()
    private val replyFixtureRule = ReplyHostFixtureRule(
        appFixture = { appFixtureRule.fixture },
        structuredToolCallsStatus = "UNSUPPORTED",
    )
    private val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(appFixtureRule).around(replyFixtureRule).around(activityRule)

    @Test
    fun unsupportedStructuredToolCapabilityFailsClosedWithoutStreamingOrRawText() {
        val prompt = "unsupported-${java.util.UUID.randomUUID().toString().take(10)}"
        sendPromptThroughMainActivity(prompt)

        val session = replyFixtureRule.awaitSession(prompt)
        val turn = replyFixtureRule.awaitFinished(session.id)
        assertEquals("FAILED", turn.status)
        assertEquals("PROTOCOL", turn.failureKind)
        assertFalse("A protocol incompatibility must not offer whole-turn retry", turn.retryable)
        assertEquals("No model request should be sent without confirmed tool support", 0, replyFixtureRule.server.receivedRequests)

        val rows = replyFixtureRule.messages(session.id, turn.sequence)
        assertEquals(1, rows.count { it.message.role == "USER" })
        assertTrue(rows.filter { it.message.role == "ASSISTANT" }.all { it.message.text.isBlank() })
        val strings = activityRule.activity
        waitForText(activityRule, strings.getString(R.string.elym_reply_failed))
        waitForText(activityRule, strings.getString(R.string.elym_service_protocol_error))
        assertTrue("Incompatible model text must not be rendered as a Reply", activityRule.onAllNodesWithText(
            strings.getString(R.string.elym_retry), useUnmergedTree = true,
        ).fetchSemanticsNodes().isEmpty())
    }

    private fun sendPromptThroughMainActivity(prompt: String) {
        val strings = activityRule.activity
        waitForText(activityRule, replyFixtureRule.personaName)
        activityRule.onNodeWithText(replyFixtureRule.personaName).performClick()
        waitForDescription(activityRule, strings.getString(R.string.elym_chat_menu))
        activityRule.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        activityRule.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(activityRule, strings.getString(R.string.elym_sessions))
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
