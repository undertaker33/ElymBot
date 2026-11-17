package com.elymbot.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import org.junit.Assert.assertTrue
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
    fun personaCreatedThroughGalleryCanBeFoundByNameAndTag() {
        val name = "UI-${fixture.prefix.takeLast(12)}"
        val originalTag = "Existing-${fixture.prefix.takeLast(8)}"
        val newTag = "Custom-${fixture.prefix.takeLast(8)}"
        val personaCard = compose.onNode(hasText(name) and hasClickAction() and !hasSetTextAction())
        val originalSelection = compose.onNode(hasText(originalTag) and isToggleable())
        val newSelection = compose.onNode(hasText(newTag) and isToggleable())
        val originalFilter = compose.onNode(hasText(originalTag) and hasClickAction())
        val newFilter = compose.onNode(hasText(newTag) and hasClickAction())
        val personaDao = fixture.database.personaDao()
        val beforeIds = runBlocking { personaDao.getAll().map { it.id }.toSet() }
        var createdId: String? = null
        try {
            waitForText(fixture.personaName)
            createGalleryTag(originalTag)
            waitForText(label(R.string.elym_no_personas))
            description(R.string.elym_create_persona).performClick()
            description(R.string.elym_name).performTextInput(name)
            originalSelection.performScrollTo().assertIsOff().performClick()
            description(R.string.elym_prompt).performScrollTo().performTextInput("Gallery test prompt")
            scrollAndClick(R.string.elym_create)
            compose.waitUntil(10_000) {
                createdId = runBlocking {
                    personaDao.getAll().singleOrNull { it.id !in beforeIds && it.name == name }?.id
                }
                createdId != null
            }

            description(R.string.elym_search).performClick()
            val search = description(R.string.elym_search_hint)
            search.performTextInput("Absent-${fixture.prefix.takeLast(12)}")
            waitForText(label(R.string.elym_no_personas))
            personaCard.assertDoesNotExist()
            search.performTextReplacement(name)
            personaCard.assertIsDisplayed()
            compose.onNodeWithText(fixture.personaName).assertDoesNotExist()
            description(R.string.elym_close_search).performClick()
            originalFilter.performScrollTo().performClick()
            personaCard.assertIsDisplayed()
            compose.onNodeWithText(fixture.personaName).assertDoesNotExist()

            createGalleryTag(newTag)
            waitForText(label(R.string.elym_no_personas))
            assertTrue(runBlocking { service.personas.first().none { newTag in it.tags } })
            originalFilter.performScrollTo().performClick()
            personaCard.performClick()
            description(R.string.elym_persona_menu).performClick()
            click(R.string.elym_edit_persona)
            originalSelection.performScrollTo().assertIsOn()
            newSelection.performScrollTo().assertIsOff().performClick()
            assertTrue(runBlocking { service.personas.first().none { newTag in it.tags } })
            saveGalleryPersonaEdits()
            compose.waitUntil(10_000) {
                runBlocking {
                    val personas = service.personas.first()
                    personas.any { it.id == createdId && originalTag in it.tags && newTag in it.tags } &&
                        personas.none { it.id != createdId && newTag in it.tags }
                }
            }
            description(R.string.elym_back).performClick()
            newFilter.performScrollTo().performClick()
            personaCard.assertIsDisplayed()
            compose.onNodeWithText(fixture.personaName).assertDoesNotExist()

            personaCard.performClick()
            description(R.string.elym_persona_menu).performClick()
            click(R.string.elym_edit_persona)
            newSelection.performScrollTo().assertIsOn().performClick()
            saveGalleryPersonaEdits()
            description(R.string.elym_back).performClick()
            newFilter.performScrollTo().performClick()
            waitForText(label(R.string.elym_no_personas))
            assertTrue(runBlocking { newTag in service.personaTags.first() })
        } finally {
            runBlocking {
                createdId?.let { service.deletePersona(it) }
                fixture.database.openHelper.writableDatabase.delete("persona_tags", "name IN (?, ?)", arrayOf(originalTag, newTag))
            }
        }
    }

    private fun createGalleryTag(tag: String) {
        description(R.string.elym_add_tag).performScrollTo().performClick()
        val save = compose.onNode(hasText(label(R.string.elym_save)) and hasClickAction())
        save.assertIsNotEnabled()
        description(R.string.elym_tag_name).performTextInput(tag)
        save.performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(label(R.string.elym_tag_name)).fetchSemanticsNodes().isEmpty() &&
                runBlocking { tag in service.personaTags.first() }
        }
    }

    private fun saveGalleryPersonaEdits() {
        scrollAndClick(R.string.elym_save)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(label(R.string.elym_name)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun openingOtherPersonaKeepsMainOwnerUntilGlobalEditIsSaved() {
        val otherName = "P-${fixture.prefix.takeLast(12)}"
        val sessionTitle = "Owned-${fixture.prefix.takeLast(8)}"
        val otherId = runBlocking { service.savePersona(PersonaCard(name = otherName)) }
        var sessionId: String? = null
        val dao = fixture.database.conversationDao()
        val main = runBlocking { requireNotNull(dao.getMain()) }
        val globalOption = compose.onNode(hasText(label(R.string.elym_set_global_persona)) and isToggleable())
        try {
            sessionId = runBlocking {
                val id = service.newSession(otherId)
                service.renameSession(id, sessionTitle)
                id
            }
            waitForText(fixture.personaName)
            description(R.string.elym_search).performClick()
            description(R.string.elym_search_hint).performTextInput(otherName)
            waitForText(otherName)
            compose.onNode(hasText(otherName) and hasClickAction() and !hasSetTextAction()).performClick()
            waitForText(sessionTitle)
            assertEquals(main.personaId, runBlocking { dao.getMain()?.personaId })
            compose.onNodeWithText(sessionTitle).performClick()
            openChatMenu()
            click(R.string.elym_model_config)
            description(R.string.elym_back).performClick()
            description(R.string.elym_chat_menu).assertIsDisplayed()
            description(R.string.elym_back).performClick()
            waitForText(sessionTitle)

            description(R.string.elym_persona_menu).performClick()
            compose.onNode(hasText(label(R.string.elym_delete_persona)) and hasClickAction()).assertIsDisplayed()
            click(R.string.elym_edit_persona)
            globalOption.performScrollTo().assertIsOff().performClick()
            globalOption.assertIsOn().performClick()
            globalOption.assertIsOff().performClick()
            description(R.string.elym_close).performClick()
            assertEquals(main.personaId, runBlocking { dao.getMain()?.personaId })

            description(R.string.elym_persona_menu).performClick()
            click(R.string.elym_edit_persona)
            globalOption.performScrollTo().performClick()
            saveGalleryPersonaEdits()
            assertEquals(main.id, runBlocking { dao.getMain()?.id })
            assertEquals(otherId, runBlocking { dao.getMain()?.personaId })
            assertEquals(otherId, runBlocking { dao.get(requireNotNull(sessionId))?.personaId })
            description(R.string.elym_back).performClick()
            description(R.string.elym_create_persona).assertIsDisplayed()
            description(R.string.elym_search).performClick()
            description(R.string.elym_search_hint).performTextInput(otherName)
            waitForText(otherName)
            compose.onNode(hasText(otherName) and hasClickAction() and !hasSetTextAction()).performClick()
            description(R.string.elym_chat_menu).assertIsDisplayed()
            openChatMenu()
            click(R.string.elym_edit_persona)
            globalOption.performScrollTo().assertIsOn().assertIsNotEnabled()
        } finally {
            runBlocking {
                service.activatePersona(fixture.personaId)
                sessionId?.let { service.deleteSessions(otherId, setOf(it)) }
                service.deletePersona(otherId)
            }
        }
    }

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

            click(R.string.elym_config)
            compose.onNodeWithText(label(R.string.elym_config_models)).assertIsSelected()
            click(R.string.elym_config_context)
            compose.onNodeWithText(label(R.string.elym_config_context)).assertIsSelected()
            compose.onNodeWithText(label(R.string.elym_config_empty)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.elym_model_services)).assertDoesNotExist()
            click(R.string.elym_config_tools)
            compose.onNodeWithText(label(R.string.elym_config_tools)).assertIsSelected()
            compose.onNodeWithText(label(R.string.elym_config_empty)).assertIsDisplayed()

            click(R.string.elym_persona)
            openPersonaChat()
            openChatMenu()
            click(R.string.elym_model_config)
            compose.onNodeWithText(label(R.string.elym_config_models)).assertIsSelected()
            click(R.string.elym_config_context)
            compose.onNodeWithText(label(R.string.elym_config_empty)).assertIsDisplayed()
            click(R.string.elym_config_models)
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
    fun savingAnUnchangedModelServiceKeepsItsApiKey() {
        val serviceName = "Key retention ${fixture.prefix.takeLast(12)}"
        val serviceDao = fixture.database.modelConfigurationDao()
        val row = compose.onNode(hasText(serviceName) and hasClickAction())
        val savedKey = label(R.string.elym_saved_key)
        val editTitle = label(R.string.elym_edit_service)
        try {
            openPersonaChat()
            openChatMenu()
            click(R.string.elym_model_config)
            scrollAndClick(R.string.elym_add_service)
            click(R.string.elym_select_provider)
            click(R.string.elym_provider_openai)
            description(R.string.elym_base_url).performScrollTo().performTextReplacement("https://example.invalid/v1")
            description(R.string.elym_key).performScrollTo().performTextInput("synthetic-key-test")
            description(R.string.elym_service_name).performScrollTo().performTextReplacement(serviceName)
            scrollAndClick(R.string.elym_save)
            waitForText(serviceName)

            val serviceId = runBlocking { serviceDao.getServices().single { it.name == serviceName }.id }
            row.performScrollTo().performClick()
            waitForText(savedKey)
            scrollAndClick(R.string.elym_save)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(editTitle).fetchSemanticsNodes().isEmpty()
            }

            assertTrue("Saving without a replacement must preserve the credential", runBlocking {
                serviceDao.hasCredential(serviceId)
            })
            row.performScrollTo().performClick()
            waitForText(savedKey)
        } finally {
            runBlocking {
                serviceDao.getServices().singleOrNull { it.name == serviceName }?.let {
                    service.deleteModelService(it.id)
                }
            }
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
        compose.onNodeWithText(savedPoint).performClick()
        compose.onNodeWithText(fixture.context.getString(R.string.elym_restore_checkpoint_title, 2)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.elym_restore_checkpoint_message)).assertIsDisplayed()
        click(R.string.elym_cancel)
        compose.onNodeWithText(savedPoint).assertIsDisplayed()
        assertEquals("COMPLETED", runBlocking { fixture.database.turnDao().latest(sessionId)?.status })

        compose.onNodeWithText(emptyPoint).performClick()
        compose.onNodeWithText(fixture.context.getString(R.string.elym_restore_checkpoint_title, 1)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.elym_restore_empty_checkpoint_message)).assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(emptyPoint).assertIsDisplayed()
        assertEquals("COMPLETED", runBlocking { fixture.database.turnDao().latest(sessionId)?.status })

        compose.onNodeWithText(emptyPoint).performClick()
        click(R.string.elym_restore_checkpoint)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(reply).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText(emptyPoint).fetchSemanticsNodes().isEmpty()
        }
        val receipt = fixture.context.getString(R.string.elym_command_checkpoint_restored, 1)
        waitForText(receipt)
        assertEquals(1, compose.onAllNodesWithText(receipt).fetchSemanticsNodes().size)
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
            val crop = description(R.string.elym_crop_image)
            description(R.string.elym_cover_position).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                crop.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
                    ?.map { it.label } == listOf(label(R.string.elym_crop_move_left), label(R.string.elym_crop_move_right))
            }
            crop.performTouchInput { swipeLeft() }
            click(R.string.elym_cancel)
            description(R.string.elym_cover_position).performScrollTo().performClick()
            click(R.string.elym_done)
            description(R.string.elym_avatar_position).performScrollTo().performClick()
            crop.performTouchInput { swipeRight() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            crop.assertDoesNotExist()
            description(R.string.elym_avatar_position).performScrollTo().performClick()
            click(R.string.elym_done)
            scrollAndClick(R.string.elym_save)
            val unchanged = runBlocking { requireNotNull(fixture.database.personaDao().get(fixture.personaId)) }
            assertEquals(0.5f, unchanged.coverX, 0f)
            assertEquals(0.5f, unchanged.avatarX, 0f)

            openChatMenu()
            click(R.string.elym_edit_persona)
            description(R.string.elym_cover_position).performScrollTo().performClick()
            crop.performTouchInput { swipeRight() }
            click(R.string.elym_done)
            description(R.string.elym_avatar_position).performScrollTo().performClick()
            crop.performTouchInput { swipeLeft() }
            click(R.string.elym_done)
            scrollAndClick(R.string.elym_save)
            compose.waitUntil(10_000) {
                runBlocking {
                    val persona = fixture.database.personaDao().get(fixture.personaId)
                    persona?.coverX == 0f && persona.avatarX == 1f
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
