package com.elymbot.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.data.CommandNameEntity
import com.elymbot.android.ui.ChatCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            waitForText(fixture.personaName)
            compose.onNodeWithText(fixture.personaName).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(label(R.string.elym_chat_menu)).fetchSemanticsNodes().isNotEmpty()
            }
            description(R.string.elym_chat_menu).performClick()
            compose.onNodeWithText(label(R.string.elym_sessions)).performClick()
            waitForText(title)
            compose.onNodeWithText(title).performClick()

            val input = description(R.string.elym_type_message)
            val add = description(R.string.elym_composer_add)
            input.performTextInput("2")
            add.performClick()
            compose.onNodeWithText("/$helpAlias").performClick()
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
            compose.onNodeWithText(checkpoint).performClick()
            input.assertTextEquals("$checkpoint 2")
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

    private fun label(resource: Int): String = fixture.context.getString(resource)

    private fun description(resource: Int) = compose.onNodeWithContentDescription(label(resource))

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
