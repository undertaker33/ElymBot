package com.astrbot.android.core.runtime.context

import com.astrbot.android.di.runtime.context.toRuntimeBotSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeConfigSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeConversationSessionSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeMessageType
import com.astrbot.android.di.runtime.context.toRuntimeProviderSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeContextResolverProviderTest {
    @Test
    fun config_provider_wins_over_stale_provider_projection() {
        val context = RuntimeContextResolver.resolve(
            event = RuntimeIngressEvent(
                platform = RuntimePlatform.APP_CHAT,
                conversationId = "session-1",
                messageId = "msg-1",
                sender = SenderInfo(userId = "user"),
                messageType = MessageType.FriendMessage.toRuntimeMessageType(),
                text = "hello",
            ),
            bot = BotProfile(
                id = "bot-1",
                displayName = "Bot",
                configProfileId = "config-1",
                defaultProviderId = "deepseek-chat",
            ).toRuntimeBotSnapshot(),
            dataPort = FakeRuntimeContextDataPort(
                config = ConfigProfile(id = "config-1", defaultChatProviderId = "qwen-chat"),
                providers = listOf(
                    provider("deepseek-chat"),
                    provider("qwen-chat"),
                ),
            ),
            overrideProviderId = "deepseek-chat",
        )

        assertEquals("qwen-chat", context.provider.id)
    }

    private class FakeRuntimeContextDataPort(
        private val config: ConfigProfile,
        private val providers: List<ProviderProfile>,
    ) : RuntimeContextDataPort {
        override fun resolveConfig(configProfileId: String): RuntimeConfigSnapshot = config.toRuntimeConfigSnapshot()
        override fun listProviders(): List<RuntimeProviderSnapshot> = providers.map { it.toRuntimeProviderSnapshot() }
        override fun findEnabledPersona(personaId: String) = null
        override fun session(sessionId: String): RuntimeConversationSessionSnapshot {
            return ConversationSession(
                id = sessionId,
                title = "Session",
                botId = "bot-1",
                personaId = "",
                providerId = "",
                maxContextMessages = 12,
                messages = emptyList(),
            ).toRuntimeConversationSessionSnapshot()
        }

        override fun compatibilitySnapshotForConfig(
            config: RuntimeConfigSnapshot,
        ): RuntimeResourceCenterCompatibilitySnapshot {
            return ResourceCenterCompatibilitySnapshot(
                resources = emptyList(),
                projections = emptyList(),
            ).toRuntimeResourceCenterCompatibilitySnapshot()
        }
    }

    private fun provider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com",
            model = "model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
        )
    }
}
