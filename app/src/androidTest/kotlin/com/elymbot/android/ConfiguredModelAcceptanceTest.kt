package com.elymbot.android

import android.util.Log
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
import com.elymbot.android.ui.ModelConfiguration
import com.elymbot.android.ui.ModelServiceItem
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            withTimeout(TimeUnit.SECONDS.toMillis(30)) { service.models.first { it.text != null } }
        }
        val textChoice = requireNotNull(config.text)
        val textService = requireNotNull(config.services.firstOrNull { it.id == textChoice.serviceId })
        check(textService.hasKey) { "The configured Text model has no saved credential." }
        Log.i("W01ModelAcceptance", "Configured Text model=${textChoice.modelId}; service=${textService.name}")

        openModelEditor(textService)
        val strings = compose.activity
        compose.onNodeWithText(strings.getString(R.string.elym_probe_list)).performClick()
        waitForText(compose, strings.getString(R.string.elym_probe_result))
        compose.onNodeWithText(strings.getString(R.string.elym_use_model)).assertIsEnabled()
        closeTopDialog()
        Log.i("W01ModelAcceptance", "Configured Text model is selectable from the model list.")

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
        Log.i(
            "W01ModelAcceptance",
            "Configured Text probe results: " +
                listOf("AVAILABILITY", "VISION", "NATIVE_STREAMING").joinToString { "$it=${statuses[it]}" },
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

        val prompt = "w01-${UUID.randomUUID().toString().take(10)} reply with DEVICE_TEST_OK"
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_type_message)).performTextInput(prompt)
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_send)).performClick()
        val session = awaitSession(prompt.take(26))
        waitForCompleted(session.id)
        val turn = runBlocking {
            withContext(Dispatchers.IO) { requireNotNull(fixture.database.turnDao().latest(session.id)) }
        }
        val messages = runBlocking {
            withContext(Dispatchers.IO) { fixture.database.messageDao().forTurn(session.id, turn.sequence) }
        }
        val reply = messages.single { it.message.role == "ASSISTANT" }.message.text
        assertNotNull("The configured Text model did not persist a reply", reply)
        assertTrue("The configured Text model returned an empty reply", reply.isNotBlank())
        Log.i("W01ModelAcceptance", "Configured Text reply=${reply.take(120)}")
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
            while (probes.size < 3 && System.nanoTime() < deadline) {
                Thread.sleep(25)
                probes = fixture.database.modelConfigurationDao().getProbes(service.id, modelId, service.baseUrl)
            }
            probes
        }
    }

    private fun waitForCompleted(sessionId: String) {
        compose.waitUntil(180_000) {
            runBlocking { withContext(Dispatchers.IO) { fixture.database.turnDao().latest(sessionId)?.status == "COMPLETED" } }
        }
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
