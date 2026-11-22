package com.elymbot.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.data.CommandNameEntity
import com.elymbot.android.ui.ChatCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposerAddMenuTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture get() = fixtureRule.fixture

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    @Test
    fun sendingAfterBrowsingHistoryRevealsTheNewTailAboveTheComposer() {
        val service = (fixture.context.applicationContext as ElymBotApplication).uiService
        val title = "Scroll test"
        val sessionId = runBlocking {
            service.newSession(fixture.personaId).also { service.renameSession(it, title) }
        }
        val help = "/${runBlocking { service.commandNames.first().getValue(ChatCommand.Help) }}"
        runBlocking {
            repeat(14) { service.send(sessionId, "$help seed-$it", null) }
        }
        openDedicatedChat(title)
        waitForText("$help seed-13")
        val initialTail = compose.onNodeWithText("$help seed-13")
        initialTail.assertIsDisplayed()
        val timeline = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        val input = description(R.string.elym_type_message)
        val send = description(R.string.elym_send)
        timeline.performTouchInput { swipeDown() }
        initialTail.assertDoesNotExist()
        runBlocking { service.send(sessionId, "$help external", null) }
        val externalMessage = compose.onNodeWithText("$help external")
        externalMessage.assertDoesNotExist()

        input.performTextInput("$help button")
        send.performClick()
        val outgoingButtonMessage = hasText("$help button") and !hasSetTextAction()
        compose.waitUntil(10_000) { compose.onAllNodes(outgoingButtonMessage).fetchSemanticsNodes().isNotEmpty() }
        val buttonMessage = compose.onNode(outgoingButtonMessage)
        buttonMessage.assertIsDisplayed()
        assertTrue(buttonMessage.getUnclippedBoundsInRoot().bottom < send.getUnclippedBoundsInRoot().top)

        timeline.performTouchInput { swipeDown() }
        buttonMessage.assertDoesNotExist()
        input.performTextInput(help)
        input.performImeAction()
        val receiptLead = "$help："
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(receiptLead, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val receipt = compose.onNodeWithText(receiptLead, substring = true)
        receipt.assertIsDisplayed()
        assertTrue(receipt.getUnclippedBoundsInRoot().bottom < send.getUnclippedBoundsInRoot().top)
        runBlocking {
            val messages = service.conversation(sessionId).first().messages
            assertTrue(messages.last().localReceipt)
            assertTrue(!messages[messages.lastIndex - 1].localReceipt)
        }
    }

    @Test
    fun selectingAnAliasKeepsTheDraftAndImageWithoutSending() {
        val database = fixture.database
        val service = (fixture.context.applicationContext as ElymBotApplication).uiService
        val helpAlias = "guide-${fixture.prefix}"
        val aliasKey = ChatCommand.Help.name
        val title = "Composer test"
        val previousHelp = runBlocking { database.commandNameDao().getAll().firstOrNull { it.behaviorId == aliasKey } }
        val sessionId = runBlocking {
            service.newSession(fixture.personaId).also { service.renameSession(it, title) }
        }
        val initialMessages = runBlocking { service.conversation(sessionId).first().messages }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val picker = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action !in setOf(
                        MediaStore.ACTION_PICK_IMAGES,
                        "androidx.activity.result.contract.action.PICK_IMAGES",
                        "com.google.android.gms.provider.action.PICK_IMAGES",
                        Intent.ACTION_OPEN_DOCUMENT,
                    )
                ) return null
                val image = Uri.parse("android.resource://${fixture.context.packageName}/${R.drawable.elym_photo}")
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(image))
            }
        }
        try {
            val checkpoint = runBlocking {
                database.commandNameDao().upsertAll(listOf(CommandNameEntity(aliasKey, helpAlias)))
                "/${database.commandNameDao().getAll().firstOrNull { it.behaviorId == ChatCommand.Checkpoint.name }?.name
                    ?: ChatCommand.Checkpoint.defaultName}"
            }
            openDedicatedChat(title)

            val input = description(R.string.elym_type_message)
            val add = description(R.string.elym_composer_add)
            val commands = compose.onNodeWithText(label(R.string.elym_composer_commands))
            val helpCandidate = compose.onNodeWithText("/$helpAlias")
            val checkpointCandidate = compose.onNodeWithText(checkpoint)
            input.performTextInput("2")
            add.performClick()
            helpCandidate.assertDoesNotExist()
            commands.performClick()
            input.assertTextEquals("/ 2")
            helpCandidate.performClick()
            input.assertTextEquals("/$helpAlias 2")
            compose.runOnIdle {}
            runBlocking {
                assertEquals(initialMessages, service.conversation(sessionId).first().messages)
                assertNull(database.messageDao().latest(sessionId))
            }

            instrumentation.addMonitor(picker)
            add.performClick()
            compose.onNodeWithText(label(R.string.elym_add_image)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(label(R.string.elym_remove_image)).fetchSemanticsNodes().isNotEmpty()
            }
            description(R.string.elym_remove_image).assertIsDisplayed()
            input.assertTextEquals("/$helpAlias 2")

            add.performClick()
            commands.performClick()
            input.assertTextEquals("/ 2")
            checkpointCandidate.performClick()
            input.assertTextEquals("$checkpoint 2")
            description(R.string.elym_remove_image).assertIsDisplayed()

            input.performTextReplacement("https://example.test /guide")
            helpCandidate.assertDoesNotExist()
            input.performTextReplacement("/")
            helpCandidate.assertIsDisplayed()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            helpCandidate.assertDoesNotExist()
            input.assertTextEquals("/")
            input.performTextInput("GUIDE")
            checkpointCandidate.assertDoesNotExist()
            helpCandidate.performClick()
            input.assertTextEquals("/$helpAlias")
            description(R.string.elym_remove_image).assertIsDisplayed()
            compose.runOnIdle {}
            runBlocking {
                assertEquals(initialMessages, service.conversation(sessionId).first().messages)
                assertNull(database.messageDao().latest(sessionId))
                assertNull(database.turnDao().latest(sessionId))
            }
        } finally {
            instrumentation.removeMonitor(picker)
            runBlocking(Dispatchers.IO) {
                if (previousHelp != null) {
                    database.commandNameDao().upsertAll(listOf(previousHelp))
                } else {
                    database.openHelper.writableDatabase.delete("command_names", "behaviorId = ?", arrayOf(aliasKey))
                }
            }
        }
    }

    private fun openDedicatedChat(title: String) {
        waitForText(fixture.personaName)
        compose.onNodeWithText(fixture.personaName).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(label(R.string.elym_chat_menu)).fetchSemanticsNodes().isNotEmpty()
        }
        description(R.string.elym_chat_menu).performClick()
        compose.onNodeWithText(label(R.string.elym_sessions)).performClick()
        waitForText(title)
        compose.onNodeWithText(title).performClick()
    }

    private fun label(resource: Int): String = fixture.context.getString(resource)

    private fun description(resource: Int) = compose.onNodeWithContentDescription(label(resource))

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
