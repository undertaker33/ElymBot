package com.astrbot.android.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.astrbot.android.R
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ResourceCenterScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resourceListAddDialogCreatesRemoteMcpResource() {
        var created: ResourceCenterItem? = null

        composeRule.setContent {
            MaterialTheme {
                ResourceListScreen(
                    kind = ResourceKind.MCP,
                    onBack = {},
                    resources = emptyList(),
                    onAddResource = { created = it },
                )
            }
        }

        composeRule.onNodeWithTag("resource-list-add-fab").performClick()
        composeRule.onNodeWithTag("resource-add-name").performTextInput("Docs MCP")
        composeRule.onNodeWithTag("resource-add-description").performTextInput("Remote docs server")
        composeRule.onNodeWithTag("resource-add-server-url").performTextInput("https://mcp.example.com/sse")
        composeRule.onNodeWithTag("resource-add-transport").performTextClearance()
        composeRule.onNodeWithTag("resource-add-transport").performTextInput("sse")
        composeRule.onNodeWithTag("resource-add-timeout").performTextClearance()
        composeRule.onNodeWithTag("resource-add-timeout").performTextInput("45")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()

        val resource = created
        assertNotNull(resource)
        resource!!
        assertEquals(ResourceCenterKind.MCP_SERVER, resource.kind)
        assertEquals("Docs MCP", resource.name)
        assertEquals("Remote docs server", resource.description)
    }

    @Test
    fun cronJobsPageJumpChangesToSecondPage() {
        composeRule.setContent {
            MaterialTheme {
                CronJobsContent(
                    jobs = sampleJobs(6),
                )
            }
        }

        composeRule.onNodeWithTag("pager-page").performClick()
        composeRule.onNodeWithTag("pager-jump-input").performTextClearance()
        composeRule.onNodeWithTag("pager-jump-input").performTextInput("2")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_confirm)).performClick()
        composeRule.onNodeWithText("Task 6").assertExists()
    }

    private fun sampleJobs(count: Int): List<CronJob> {
        return (1..count).map { index ->
            CronJob(
                jobId = "job-$index",
                name = "Task $index",
                description = "Task $index description",
                cronExpression = "0 9 * * *",
                conversationId = "conversation-$index",
                nextRunTime = 1_735_000_000_000L + index * 1_000L,
                lastRunAt = 1_734_000_000_000L + index * 1_000L,
                status = "scheduled",
                enabled = index % 2 == 1,
                createdAt = 1_733_000_000_000L + index * 1_000L,
            )
        }
    }
}
