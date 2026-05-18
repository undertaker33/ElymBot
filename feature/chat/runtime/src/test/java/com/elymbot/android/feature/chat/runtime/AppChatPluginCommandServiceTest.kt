package com.elymbot.android.feature.chat.runtime

import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutionResult
import com.elymbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.elymbot.android.feature.plugin.runtime.PluginExecutionBatchResult
import com.elymbot.android.feature.plugin.runtime.PluginExecutionHostSnapshot
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.runtime.PluginMessageEvent
import com.elymbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.elymbot.android.feature.plugin.runtime.PluginToolArgs
import com.elymbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.elymbot.android.feature.plugin.runtime.PluginToolResult
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.elymbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeSnapshot
import com.elymbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.elymbot.android.feature.plugin.runtime.PluginV2ToolRegistryEntry
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageSessionRef
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.HostActionRequest
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginTriggerSource
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppChatPluginCommandServiceTest {
    @Test
    fun app_chat_plugin_message_event_uses_unified_origin_instead_of_database_session_id() {
        val service = AppChatPluginCommandService(
            dependencies = unsupportedBindingsProxy(),
            appChatPluginRuntime = EmptyAppChatPluginRuntime,
            hostCapabilityGateway = ChatNoOpPluginHostCapabilityGateway,
            hostActionExecutor = ExternalPluginHostActionExecutor(),
            dispatchEngine = PluginV2DispatchEngine(),
        )
        val session = ConversationSession(
            id = "db-session-42",
            title = "Main Chat",
            botId = "bot-1",
            personaId = "",
            providerId = "",
            platformId = "app",
            messageType = MessageType.OtherMessage,
            originSessionId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        val message = ConversationMessage(
            id = "msg-1",
            role = "user",
            content = "hello",
            timestamp = 123L,
        )

        val event = buildPluginMessageEvent(service, session, message)

        assertEquals(
            MessageSessionRef(
                platformId = session.platformId,
                messageType = session.messageType,
                originSessionId = session.originSessionId,
            ).unifiedOrigin,
            event.extras["sessionUnifiedOrigin"],
        )
        assertFalse(event.extras["sessionUnifiedOrigin"] == session.id)
    }

    private fun buildPluginMessageEvent(
        service: AppChatPluginCommandService,
        session: ConversationSession,
        message: ConversationMessage,
    ): PluginMessageEvent {
        val method = AppChatPluginCommandService::class.java.getDeclaredMethod(
            "buildAppChatPluginMessageEvent",
            PluginTriggerSource::class.java,
            ConversationSession::class.java,
            ConversationMessage::class.java,
            ProviderProfile::class.java,
            BotProfile::class.java,
            String::class.java,
            ConfigProfile::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            service,
            PluginTriggerSource.BeforeSendMessage,
            session,
            message,
            null,
            null,
            "",
            null,
            false,
        ) as PluginMessageEvent
    }

    private fun unsupportedBindingsProxy(): AppChatRuntimeBindings {
        return Proxy.newProxyInstance(
            AppChatRuntimeBindings::class.java.classLoader,
            arrayOf(AppChatRuntimeBindings::class.java),
        ) { _, method, _ ->
            throw UnsupportedOperationException("Unexpected AppChatRuntimeBindings call: ${method.name}")
        } as AppChatRuntimeBindings
    }
}

private object EmptyAppChatPluginRuntime : AppChatPluginRuntime {
    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return PluginExecutionBatchResult(
            trigger = trigger,
            outcomes = emptyList(),
            skipped = emptyList(),
        )
    }
}

private object ChatNoOpPluginHostCapabilityGateway : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        throw UnsupportedOperationException("executeHostAction should not be called in this test")
    }

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext = context

    override fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext = context

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot = snapshot

    override fun executeHostBuiltinTool(args: PluginToolArgs): PluginToolResult? = null

    override fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean = true
}
