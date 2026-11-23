package com.elymbot.android

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ReplyDeliveryStatus
import com.elymbot.android.data.ReplyDeliveryTargetKind
import com.elymbot.android.ui.ChatDeliveryStatus
import com.elymbot.android.ui.ModelServiceItem
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.ModelProbeTarget
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfiguredModelAcceptanceTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture get() = fixtureRule.fixture

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    @Test
    fun configuredTextModelListsProbesAndRepliesToATestPrompt() {
        val app = compose.activity.application as ElymBotApplication
        val service = app.uiService
        val config = runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(30)) { service.models.first() }
        }
        val textChoice = checkNotNull(config.text) { "The device has no assigned Text model." }
        val textService = requireNotNull(config.services.firstOrNull { it.id == textChoice.serviceId })
        check(textService.hasKey) { "The configured Text model has no saved credential." }

        openModelEditor(textService)
        val strings = compose.activity
        compose.onNodeWithText(strings.getString(R.string.elym_probe_list)).performClick()
        waitForText(compose, strings.getString(R.string.elym_probe_result))
        compose.onNodeWithText(strings.getString(R.string.elym_use_model)).assertIsEnabled()
        closeTopDialog()

        compose.onNodeWithText(strings.getString(R.string.elym_probe_capabilities)).performClick()
        waitForText(compose, strings.getString(R.string.elym_model_available))
        waitForText(compose, strings.getString(R.string.elym_vision_support))
        waitForText(compose, strings.getString(R.string.elym_streaming_support))
        compose.waitUntil(180_000) {
            compose.onAllNodesWithText(strings.getString(R.string.elym_probe_waiting)).fetchSemanticsNodes().isEmpty()
        }

        val probes = readProbes(textService, textChoice.modelId)
        val statuses = probes.associate { it.target to it.status }
        assertTrue("Availability result was not stored", statuses.containsKey("AVAILABILITY"))
        assertTrue("Vision result was not stored", statuses.containsKey("VISION"))
        assertTrue("Streaming result was not stored", statuses.containsKey("NATIVE_STREAMING"))
        assertEquals(
            "The configured Text model requires structured tools to send Reply",
            CapabilityStatus.SUPPORTED.name,
            statuses[ModelProbeTarget.STRUCTURED_TOOL_CALLS.name],
        )

        closeTopDialog()
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_close)).performClick()
        compose.onNodeWithText(strings.getString(R.string.elym_persona)).performClick()
        waitForText(compose, fixture.personaName)
        compose.onNodeWithText(fixture.personaName).performClick()
        waitForDescription(compose, strings.getString(R.string.elym_chat_menu))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        compose.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(compose, strings.getString(R.string.elym_sessions))
        openNewSession(compose, strings.getString(R.string.elym_new_session))

        val replyCode = "DEVICE_TEST_OK"
        val prompt = "t02-${UUID.randomUUID().toString().take(10)} reply with $replyCode"
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_type_message)).performTextInput(prompt)
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_send)).performClick()
        val session = awaitSession(prompt.take(26))
        waitForCompleted(session.id)
        val reply = runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                service.conversation(session.id).map { conversation ->
                    conversation.messages.firstOrNull { message ->
                        message.assistant && replyCode in message.text && message.deliveryTargets.any { target ->
                            target.kind == ReplyDeliveryTargetKind.APP_TRANSCRIPT.name &&
                                target.status == ChatDeliveryStatus.Delivered
                        }
                    }
                }.filterNotNull().first()
            }
        }
        val receipt = runBlocking {
            withContext(Dispatchers.IO) {
                fixture.database.replyDeliveryDao().forMessage(reply.id)
                    .single { it.targetKind == ReplyDeliveryTargetKind.APP_TRANSCRIPT.name }
            }
        }
        assertEquals(ReplyDeliveryStatus.DELIVERED.name, receipt.status)
        waitForText(compose, reply.text)
    }

    private fun closeTopDialog() {
        val description = compose.activity.getString(R.string.elym_close)
        val closes = compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes()
        require(closes.isNotEmpty()) { "No close action is available." }
        compose.onAllNodesWithContentDescription(description)[closes.lastIndex].performClick()
    }

    private fun openModelEditor(service: ModelServiceItem) {
        val strings = compose.activity
        waitForText(compose, strings.getString(R.string.elym_config))
        compose.onNodeWithText(strings.getString(R.string.elym_config)).performClick()
        waitForText(compose, service.name)
        compose.onNodeWithText(service.name).performClick()
        waitForText(compose, strings.getString(R.string.elym_probe_list))
    }

    private fun readProbes(service: ModelServiceItem, modelId: String): List<ModelProbeEntity> = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var probes = fixture.database.modelConfigurationDao().getProbes(service.id, modelId, service.baseUrl)
            while (probes.size < ModelProbeTarget.entries.size && System.nanoTime() < deadline) {
                Thread.sleep(25)
                probes = fixture.database.modelConfigurationDao().getProbes(service.id, modelId, service.baseUrl)
            }
            probes
        }
    }

    private fun waitForCompleted(sessionId: String) {
        var status: String? = null
        compose.waitUntil(180_000) {
            status = runBlocking {
                withContext(Dispatchers.IO) { fixture.database.turnDao().latest(sessionId)?.status }
            }
            status != null && status != "RUNNING"
        }
        assertEquals("The configured Text model did not complete a Reply turn", "COMPLETED", status)
    }

    private fun awaitSession(title: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var found = fixture.database.conversationDao().getDedicated(fixture.personaId).firstOrNull { it.title == title }
            while (found == null && System.nanoTime() < deadline) {
                Thread.sleep(25)
                found = fixture.database.conversationDao().getDedicated(fixture.personaId).firstOrNull { it.title == title }
            }
            requireNotNull(found) { "No dedicated session was persisted for '$title'." }
        }
    }

    private fun openNewSession(compose: ComposeContentTestRule, label: String) {
        compose.waitUntil(20_000) {
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
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
