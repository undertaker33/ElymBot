package com.elymbot.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.MessageEntity
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.TurnEntity
import com.elymbot.android.ui.ChatCommand
import com.elymbot.android.ui.PersonaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElymBotUiIntegrationTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture get() = fixtureRule.fixture
    private val service get() = (fixture.context.applicationContext as ElymBotApplication).uiService

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    @Test
    fun settingsAndModelNavigationLeadBackToChatThenOpeningClearsUnreadAndAllowsBatchDelete() {
        val previousNames = runBlocking { fixture.database.commandNameDao().getAll() }
        val helpName = "help-${fixture.prefix}"
        val firstTitle = "First UI session"
        val secondTitle = "Second UI session"
        try {
            val firstId = runBlocking {
                val id = service.newSession(fixture.personaId)
                service.renameSession(id, firstTitle)
                fixture.database.conversationDao().setUnread(id, true)
                service.renameSession(service.newSession(fixture.personaId), secondTitle)
                id
            }
            click(R.string.elym_settings)
            click(R.string.elym_command_names)
            description(R.string.elym_command_help).performTextReplacement(helpName)
            scrollAndClick(R.string.elym_save)
            compose.waitUntil(10_000) {
                runBlocking { fixture.database.commandNameDao().getAll().any { it.name == helpName } }
            }

            click(R.string.elym_persona)
            openPersonaChat()
            openChatMenu()
            click(R.string.elym_model_config)
            scrollAndClick(R.string.elym_add_service)
            click(R.string.elym_select_provider)
            click(R.string.elym_provider_custom)
            description(R.string.elym_base_url).assertIsDisplayed()
            description(R.string.elym_close).performClick()
            scrollAndClick(R.string.elym_text_role)
            compose.onNodeWithText(label(R.string.elym_text_model)).assertIsDisplayed()
            description(R.string.elym_close).performClick()
            description(R.string.elym_back).performClick()

            openChatMenu()
            click(R.string.elym_sessions)
            waitForText(firstTitle)
            compose.onNode(hasText(firstTitle) and hasText(label(R.string.elym_unread))).assertIsDisplayed()
            compose.onNodeWithText(firstTitle).performClick()
            val input = description(R.string.elym_type_message)
            input.performTextInput("/$helpName")
            input.performImeAction()
            waitForText("/$helpName：", substring = true)
            description(R.string.elym_back).performClick()
            waitForText(firstTitle)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(label(R.string.elym_unread)).fetchSemanticsNodes().isEmpty()
            }
            assertFalse(runBlocking { requireNotNull(fixture.database.conversationDao().get(firstId)).unread })

            click(R.string.elym_select)
            compose.onNodeWithContentDescription(firstTitle).performClick()
            compose.onNodeWithContentDescription(secondTitle).performClick()
            click(R.string.elym_delete_selected)
            val confirmation = fixture.context.resources.getQuantityString(R.plurals.elym_confirm_sessions, 2, 2)
            compose.onNodeWithText(confirmation).assertIsDisplayed()
            click(R.string.elym_delete_session)
            waitForText(label(R.string.elym_no_sessions))
            assertEquals(emptyList<String>(), runBlocking {
                fixture.database.conversationDao().getDedicated(fixture.personaId).map { it.id }
            })
        } finally {
            runBlocking { fixture.database.commandNameDao().upsertAll(previousNames) }
        }
    }

    @Test
    fun renamedPersonaCommandSwitchesMainAndDedicatedSessionsRetainTheirOwner() {
        val previousNames = runBlocking { fixture.database.commandNameDao().getAll() }
        val personaCommand = "persona-${fixture.prefix}"
        val otherName = "P-${fixture.prefix.takeLast(12)}"
        val primaryTitle = "Original owner session"
        val otherTitle = "Second owner session"
        val otherId = runBlocking { service.savePersona(PersonaCard(name = otherName)) }
        var otherSessionId: String? = null
        try {
            val (mainId, primarySessionId, otherPosition) = runBlocking {
                val dao = fixture.database.conversationDao()
                val mainId = requireNotNull(dao.getMain()).id
                val primarySessionId = service.newSession(fixture.personaId)
                service.renameSession(primarySessionId, primaryTitle)
                val secondSessionId = service.newSession(otherId)
                otherSessionId = secondSessionId
                service.renameSession(secondSessionId, otherTitle)
                val otherPosition = fixture.database.personaDao().getAll().indexOfFirst { it.id == otherId } + 1
                Triple(mainId, primarySessionId, otherPosition)
            }
            click(R.string.elym_settings)
            click(R.string.elym_command_names)
            description(R.string.elym_switch_persona).performTextReplacement(personaCommand)
            scrollAndClick(R.string.elym_save)
            compose.waitUntil(10_000) {
                runBlocking {
                    fixture.database.commandNameDao().getAll().any {
                        it.behaviorId == ChatCommand.Persona.name && it.name == personaCommand
                    }
                }
            }

            click(R.string.elym_persona)
            openPersonaChat()
            openChatMenu()
            click(R.string.elym_delete_persona)
            waitForText(label(R.string.elym_persona_has_sessions))
            click(R.string.elym_view_sessions)
            waitForText(primaryTitle)
            compose.onNodeWithText(otherTitle).assertDoesNotExist()
            compose.onNodeWithText(primaryTitle).performClick()
            sendWithIme("/$personaCommand $otherPosition")
            val helpName = previousNames.first { it.behaviorId == ChatCommand.Help.name }.name
            waitForText(fixture.context.getString(R.string.elym_command_invalid, helpName))
            assertEquals(fixture.personaId, runBlocking {
                fixture.database.conversationDao().get(primarySessionId)?.personaId
            })

            description(R.string.elym_back).performClick()
            waitForText(primaryTitle)
            description(R.string.elym_back).performClick()
            sendWithIme("/$personaCommand $otherPosition")
            waitForText(fixture.context.getString(R.string.elym_command_persona_switched, otherName))
            val switchedMain = runBlocking { requireNotNull(fixture.database.conversationDao().getMain()) }
            assertEquals(mainId, switchedMain.id)
            assertEquals(otherId, switchedMain.personaId)
            openChatMenu()
            click(R.string.elym_sessions)
            waitForText(otherTitle)
            compose.onNodeWithText(primaryTitle).assertDoesNotExist()
        } finally {
            runBlocking {
                fixture.database.commandNameDao().upsertAll(previousNames)
                service.activatePersona(fixture.personaId)
                otherSessionId?.let { service.deleteSessions(otherId, setOf(it)) }
                service.deletePersona(otherId)
            }
        }
    }

    @Test
    fun checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint() {
        val title = "Checkpoint UI session"
        val reply = "Reply before restoring the empty point"
        val sessionId = runBlocking {
            val id = service.newSession(fixture.personaId)
            service.renameSession(id, title)
            fixture.database.withTransaction {
                val timestamp = System.currentTimeMillis()
                fixture.database.turnDao().insert(
                    TurnEntity(
                        sessionId = id,
                        sequence = 1,
                        executionId = "${fixture.prefix}-execution",
                        attemptId = "${fixture.prefix}-attempt",
                        status = "COMPLETED",
                        startedAt = timestamp,
                        finishedAt = timestamp,
                        personaId = fixture.personaId,
                        personaName = fixture.personaName,
                        personaPrompt = "",
                        primaryServiceId = null,
                        primaryModelId = null,
                        visionServiceId = null,
                        visionModelId = null,
                        failureKind = null,
                        retryable = false,
                    ),
                )
                fixture.database.messageDao().insert(
                    MessageEntity(
                        id = "${fixture.prefix}-reply",
                        sessionId = id,
                        turnSequence = 1,
                        messageIndex = 0,
                        role = "ASSISTANT",
                        text = reply,
                        category = "HISTORY",
                        status = null,
                        author = fixture.personaName,
                    ),
                )
            }
            id
        }
        openPersonaChat()
        openChatMenu()
        click(R.string.elym_sessions)
        waitForText(title)
        compose.onNodeWithText(title).performClick()
        waitForText(reply)

        openChatMenu()
        click(R.string.elym_checkpoints)
        val emptyPoint = fixture.context.getString(R.string.elym_checkpoint_title, 1)
        val savedPoint = fixture.context.getString(R.string.elym_checkpoint_title, 2)
        val current = label(R.string.elym_checkpoint_current)
        waitForText(emptyPoint)
        compose.onNode(hasText(emptyPoint) and hasText(current)).assertIsDisplayed()
        click(R.string.elym_save_checkpoint)
        waitForText(savedPoint)
        val retained = fixture.context.resources.getQuantityString(R.plurals.elym_checkpoint_retained_messages, 1, 1)
        compose.onNode(hasText(savedPoint) and hasText(current) and hasText(retained)).assertIsDisplayed()
        compose.onNodeWithText(emptyPoint).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(reply).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText(emptyPoint).fetchSemanticsNodes().isEmpty()
        }
        waitForText(fixture.context.getString(R.string.elym_command_checkpoint_restored, 1))
        assertEquals(null, runBlocking { fixture.database.turnDao().latest(sessionId) })

        openChatMenu()
        click(R.string.elym_checkpoints)
        waitForText(emptyPoint)
        compose.onNode(hasText(emptyPoint) and hasText(current)).assertIsDisplayed()
        compose.onNodeWithText(savedPoint).assertDoesNotExist()
    }

    @Test
    fun personaEditorSavesIndependentCoverAndAvatarPositions() {
        val path = "media/${fixture.prefix}.png"
        val assets = PrivateAssetStore(fixture.context)
        val image = assets.file(path)
        try {
            image.parentFile?.mkdirs()
            val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLUE)
            try {
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            runBlocking {
                val persona = service.personas.first().single { it.id == fixture.personaId }
                service.savePersona(persona.copy(cover = assets.uri(path)))
            }

            openPersonaChat()
            openChatMenu()
            click(R.string.elym_edit_persona)
            val horizontal = compose.onAllNodesWithContentDescription(label(R.string.elym_horizontal))
            horizontal[0].performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
            horizontal[1].performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
            scrollAndClick(R.string.elym_save)
            compose.waitUntil(10_000) {
                runBlocking {
                    val persona = fixture.database.personaDao().get(fixture.personaId)
                    persona?.coverX == 0.25f && persona.avatarX == 0.75f
                }
            }
            val saved = runBlocking { requireNotNull(fixture.database.personaDao().get(fixture.personaId)) }
            assertEquals(path, saved.coverPath)
            assertEquals(0.5f, saved.coverY, 0f)
            assertEquals(0.5f, saved.avatarY, 0f)
        } finally {
            image.delete()
        }
    }

    private fun openPersonaChat() {
        waitForText(fixture.personaName)
        waitForText(label(R.string.elym_active))
        compose.onNodeWithText(fixture.personaName).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(label(R.string.elym_chat_menu)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openChatMenu() = description(R.string.elym_chat_menu).performClick()

    private fun sendWithIme(text: String) {
        val input = description(R.string.elym_type_message)
        input.performTextInput(text)
        input.performImeAction()
    }

    private fun label(resource: Int): String = fixture.context.getString(resource)

    private fun click(resource: Int) = compose.onNode(hasText(label(resource)) and hasClickAction()).performClick()

    private fun scrollAndClick(resource: Int) =
        compose.onNode(hasText(label(resource)) and hasClickAction()).performScrollTo().performClick()

    private fun description(resource: Int) = compose.onNodeWithContentDescription(label(resource))

    private fun waitForText(value: String, substring: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(value, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
