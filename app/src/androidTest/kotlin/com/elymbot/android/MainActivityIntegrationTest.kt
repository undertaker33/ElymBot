package com.elymbot.android

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityIntegrationTest {
    private val fixture = AndroidTestAppFixtureRule()
    private val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(fixture).around(activityRule)

    @Test
    fun launchRendersTestOwnedPersonaThroughApplicationService() {
        activityRule.waitUntil(timeoutMillis = 10_000) {
            activityRule.onAllNodesWithText(fixture.fixture.personaName, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        activityRule.onNodeWithText(fixture.fixture.personaName, useUnmergedTree = true).assertExists()
    }
}
