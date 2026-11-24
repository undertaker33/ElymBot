package com.elymbot.android

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.ui.ChatMessage
import com.elymbot.android.ui.ChatPage
import com.elymbot.android.ui.Conversation
import com.elymbot.android.ui.ElymBotTheme
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.android.ui.ElymBotViewModel
import com.elymbot.android.ui.PersonaCard
import com.elymbot.android.ui.PlatformAvailability
import com.elymbot.android.ui.PlatformConnectionState
import com.elymbot.android.ui.PlatformUiActionResult
import com.elymbot.android.ui.PlatformUiFailure
import com.elymbot.android.ui.QqPlatformCommandSettingsUiState
import com.elymbot.android.ui.QqPlatformPage
import com.elymbot.android.ui.QqPlatformSettingsUiState
import com.elymbot.android.ui.QqPluginId
import com.elymbot.android.ui.QqPolicyEditor
import com.elymbot.core.contract.PlatformLoginChallenge
import com.elymbot.core.contract.PlatformQrMatrix
import com.elymbot.core.contract.PlatformRuntimePhase
import com.elymbot.core.contract.PlatformRuntimeState
import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformCommandSettingsSnapshot
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import com.elymbot.core.contract.ToolInstallationIdentity
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QqPlatformUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun changingFromLoginToConnectedRemovesTheQr() {
        val challenge = PlatformLoginChallenge(PlatformQrMatrix.copyOf(21, List(21 * 21) { it % 9 == 0 }))
        val runtime = mutableStateOf(PlatformRuntimeState(PlatformRuntimePhase.WAITING_FOR_LOGIN, loginChallenge = challenge))
        compose.activityRule.scenario.onActivity { activity ->
            val service = (activity.application as ElymBotApplication).uiService
            val model = ElymBotViewModel(service)
            activity.setContent {
                ElymBotTheme(dark = false) {
                    QqPlatformPage(model, "main", PlatformConnectionState(QqPluginId, true, PlatformAvailability.Active, runtime.value),
                        current = true, back = {}, onPlugins = null)
                }
            }
        }
        val qr = compose.onNodeWithContentDescription(label(R.string.elym_qq_login_qr))
        qr.assertIsDisplayed()
        compose.runOnIdle {
            runtime.value = PlatformRuntimeState(PlatformRuntimePhase.CONNECTED, accountId = "1000")
        }
        compose.onNodeWithText(label(R.string.elym_qq_connected)).assertIsDisplayed()
        qr.assertDoesNotExist()
    }

    @Test
    fun externalConversationKeepsHistoryVisibleWithoutComposerOrEditMenu() {
        val message = "External test history"
        val persona = PersonaCard(id = "test-persona", name = "Test Persona")
        compose.activityRule.scenario.onActivity { activity ->
            val origin = (activity.application as ElymBotApplication).uiService
            val service = object : ElymBotUiService by origin {
                override fun conversation(sessionId: String) = flowOf(Conversation(
                    readOnly = true, platformChannelId = QqPluginId,
                    messages = listOf(ChatMessage(id = "test-message", text = message)),
                ))
            }
            val model = ElymBotViewModel(service)
            activity.setContent {
                ElymBotTheme(dark = false) {
                    ChatPage(model, persona, "test-session", "External test conversation", false, listOf(persona), {}, true,
                        onSessions = {}, onEdit = {}, onDelete = {}, onRename = {})
                }
            }
        }
        compose.onNodeWithText(label(R.string.elym_qq_read_only)).assertIsDisplayed()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.elym_type_message)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.elym_send)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.elym_chat_menu)).assertDoesNotExist()
    }

    @Test
    fun mentionOnlySwitchChangesOnlyItsPolicyField() {
        val settings = QqPlatformSettings(wakeWords = listOf("wake"))
        val saved = AtomicReference<QqPlatformSettingsSnapshot?>()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ElymBotTheme(dark = false) {
                    QqPolicyEditor(QqPlatformSettingsSnapshot(3, settings), busy = false, failure = null, dismiss = {}) { revision, draft ->
                        saved.set(QqPlatformSettingsSnapshot(revision, draft))
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.elym_qq_group_at)).performScrollTo()
            .assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText(label(R.string.elym_save)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(QqPlatformSettingsSnapshot(3, settings.copy(replyOnAtOnlyEnabled = true)), saved.get())
        }
    }

    @Test
    fun commandSwitchesRequireCapabilityAndRetryFromLatestRevision() {
        val identity = ToolInstallationIdentity(QqPluginId, "test-installation")
        val rules = QqPlatformSettingsSnapshot(3, QqPlatformSettings())
        val initial = QqPlatformCommandSettingsSnapshot(7, QqPlatformCommandSettings())
        val current = QqPlatformCommandSettingsSnapshot(8, QqPlatformCommandSettings(pluginCommandsAdminOnlyEnabled = true))
        val policy = MutableStateFlow(QqPlatformSettingsUiState(available = true, snapshot = rules))
        val requests = mutableListOf<Pair<ToolInstallationIdentity, QqPlatformCommandSettingsSnapshot>>()
        var stored = current
        compose.activityRule.scenario.onActivity { activity ->
            val origin = (activity.application as ElymBotApplication).uiService
            val service = object : ElymBotUiService by origin {
                override fun qqPlatformSettings(sessionId: String) = policy

                override suspend fun updateQqPlatformCommandSettings(
                    sessionId: String,
                    expectedInstallation: ToolInstallationIdentity,
                    expectedRevision: Long,
                    settings: QqPlatformCommandSettings,
                ): PlatformUiActionResult {
                    assertEquals("main", sessionId)
                    requests += expectedInstallation to QqPlatformCommandSettingsSnapshot(expectedRevision, settings)
                    val accepted = expectedRevision == stored.revision && expectedInstallation == identity
                    if (accepted) stored = QqPlatformCommandSettingsSnapshot(expectedRevision + 1, settings)
                    policy.value = policy.value.copy(commandSettings = QqPlatformCommandSettingsUiState(identity, stored))
                    return if (accepted) PlatformUiActionResult.Completed
                    else PlatformUiActionResult.Rejected(PlatformUiFailure.SettingsConflict)
                }
            }
            val model = ElymBotViewModel(service)
            activity.setContent {
                ElymBotTheme(dark = false) {
                    QqPlatformPage(
                        model, "main", PlatformConnectionState(QqPluginId, true, PlatformAvailability.Active),
                        current = true, back = {}, onPlugins = null,
                    )
                }
            }
        }
        val admins = compose.onNodeWithContentDescription(label(R.string.elym_qq_plugin_commands_admin_only))
        val denied = compose.onNodeWithContentDescription(label(R.string.elym_qq_reply_permission_denied))
        compose.onNodeWithText(label(R.string.elym_qq_edit_rules)).assertIsDisplayed()
        admins.assertDoesNotExist()
        denied.assertDoesNotExist()
        compose.runOnIdle {
            policy.value = policy.value.copy(commandSettings = QqPlatformCommandSettingsUiState(identity, initial))
        }
        admins.performScrollTo().assertIsOff()
        denied.performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText(label(R.string.elym_qq_settings_conflict)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.elym_close)).performClick()
        admins.assertIsOn()
        denied.assertIsOff().performClick().assertIsOn()
        admins.performScrollTo().performClick().assertIsOff()
        denied.assertIsOn()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    identity to initial.copy(settings = initial.settings.copy(replyWhenPermissionDenied = true)),
                    identity to current.copy(settings = current.settings.copy(replyWhenPermissionDenied = true)),
                    identity to QqPlatformCommandSettingsSnapshot(9, QqPlatformCommandSettings(replyWhenPermissionDenied = true)),
                ),
                requests,
            )
            assertEquals(rules, policy.value.snapshot)
        }
    }

    private fun label(resource: Int) = compose.activity.getString(resource)
}
