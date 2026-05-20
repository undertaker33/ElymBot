package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.qq.domain.IncomingQqMessage
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqReplyPayload
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostSendResult
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.MessageSessionRef
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginTriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqPluginDispatchServiceTest {
    @Test
    fun plugin_command_permission_blocks_non_admin_when_admin_only_enabled() {
        assertFalse(
            QqSlashCommandPermissionPolicy.canTrigger(
                adminOnlyEnabled = true,
                isAdmin = false,
            ),
        )
    }

    @Test
    fun plugin_command_permission_allows_admin_when_admin_only_enabled() {
        assertTrue(
            QqSlashCommandPermissionPolicy.canTrigger(
                adminOnlyEnabled = true,
                isAdmin = true,
            ),
        )
    }

    @Test
    fun plugin_command_permission_allows_normal_user_when_admin_only_disabled() {
        assertTrue(
            QqSlashCommandPermissionPolicy.canTrigger(
                adminOnlyEnabled = false,
                isAdmin = false,
            ),
        )
    }

    @Test
    fun qq_bot_command_permission_blocks_non_admin_help_when_admin_only_enabled() {
        val bot = BotProfile(id = "qq-main", configProfileId = "config-1")
        val config = ConfigProfile(
            id = "config-1",
            adminUids = listOf("admin-1"),
            pluginCommandsAdminOnlyEnabled = true,
            replyWhenPermissionDenied = true,
        )
        val session = ConversationSession(
            id = "session-1",
            title = "Group",
            botId = bot.id,
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
            messageType = MessageType.GroupMessage,
        )
        val replies = mutableListOf<QqReplyPayload>()
        val botPort = FakeBotPort(bot)
        val configPort = FakeConfigPort(config)
        val providerPort = FakeProviderPort()
        val personaPort = FakePersonaPort()
        val service = QqBotCommandRuntimeService(
            botPort = botPort,
            configPort = configPort,
            providerPort = providerPort,
            conversationPort = FakeQqConversationPort(session),
            replySender = QqReplySender(
                socketSender = {},
                resolveReplyConfig = { null },
                sendOverride = { payload, _ ->
                    replies += payload
                    PluginV2HostSendResult(success = true)
                },
            ),
            profileResolver = QqRuntimeProfileResolver(
                botPort = botPort,
                configPort = configPort,
                personaPort = personaPort,
                providerPort = providerPort,
            ),
            currentLanguageTag = { "zh-CN" },
        )

        val handled = service.handle(
            message = IncomingQqMessage(
                selfId = "10001",
                messageId = "msg-1",
                conversationId = "1091021328",
                senderId = "3299479490",
                senderName = "NonAdmin",
                text = "/help",
                messageType = MessageType.GroupMessage,
                rawPayload = "{}",
            ),
            bot = bot,
            config = config,
            sessionId = session.id,
            session = session,
            currentPersona = null,
        )

        assertTrue(handled)
        assertEquals(1, replies.size)
        assertEquals(QqSlashCommandPermissionPolicy.ADMIN_ONLY_NOTICE, replies.single().text)
    }

    @Test
    fun to_plugin_message_event_includes_session_unified_origin_from_runtime_session_ref() {
        val sessionRef = MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group:30003:user:20002",
        )
        val event = IncomingQqMessage(
            selfId = "30001",
            messageId = "msg-1",
            conversationId = "30003",
            senderId = "20002",
            senderName = "Alice",
            text = "hello",
            messageType = MessageType.GroupMessage,
            rawPayload = "{}",
        ).toPluginMessageEvent(
            trigger = PluginTriggerSource.BeforeSendMessage,
            conversationId = "group:30003:user:20002",
            sessionUnifiedOrigin = sessionRef.unifiedOrigin,
            botId = "qq-main",
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "provider-1",
        )

        assertEquals(sessionRef.unifiedOrigin, event.extras["sessionUnifiedOrigin"])
        assertFalse(event.extras["sessionUnifiedOrigin"] == "qq-session-db-id")
    }

    private class FakeBotPort(private var bot: BotProfile) : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(listOf(bot))
        override val selectedBotId: StateFlow<String> = MutableStateFlow(bot.id)
        override fun currentBot(): BotProfile = bot
        override fun snapshotProfiles(): List<BotProfile> = listOf(bot)
        override fun create(name: String): BotProfile = bot.copy(displayName = name)
        override suspend fun save(profile: BotProfile) {
            bot = profile
        }
        override suspend fun create(profile: BotProfile) {
            bot = profile
        }
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakeConfigPort(private var config: ConfigProfile) : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(listOf(config))
        override val selectedProfileId: StateFlow<String> = MutableStateFlow(config.id)
        override fun snapshotProfiles(): List<ConfigProfile> = listOf(config)
        override fun create(name: String): ConfigProfile = config.copy(name = name)
        override fun resolve(id: String): ConfigProfile = config
        override fun resolveExistingId(id: String?): String = config.id
        override suspend fun save(profile: ConfigProfile) {
            config = profile
        }
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakeProviderPort : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())
        override fun snapshotProfiles(): List<ProviderProfile> = emptyList()
        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> = emptyList()
        override fun toggleEnabled(id: String) = Unit
        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit
        override suspend fun save(profile: ProviderProfile) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class FakePersonaPort : PersonaRepositoryPort {
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())
        override fun snapshotProfiles(): List<PersonaProfile> = emptyList()
        override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> = emptyList()
        override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? = null
        override suspend fun add(profile: PersonaProfile) = Unit
        override suspend fun update(profile: PersonaProfile) = Unit
        override suspend fun toggleEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun toggleEnabled(id: String) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class FakeQqConversationPort(private val session: ConversationSession) : QqConversationPort {
        override fun sessions(): List<ConversationSession> = listOf(session)
        override fun resolveOrCreateSession(
            sessionId: String,
            title: String,
            messageType: MessageType,
        ): ConversationSession = session
        override fun session(sessionId: String): ConversationSession = session
        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String = "message-1"
        override fun updateSessionBindings(
            sessionId: String,
            botId: String,
            providerId: String,
            personaId: String,
        ) = Unit
        override fun updateSessionServiceFlags(
            sessionId: String,
            sessionSttEnabled: Boolean?,
            sessionTtsEnabled: Boolean?,
        ) = Unit
        override fun replaceMessages(
            sessionId: String,
            messages: List<ConversationMessage>,
        ) = Unit
        override fun renameSession(sessionId: String, title: String) = Unit
        override fun deleteSession(sessionId: String) = Unit
    }
}
