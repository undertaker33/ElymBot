package com.astrbot.android.ui.viewmodel

import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import com.astrbot.android.feature.chat.presentation.AppChatSendHandler
import com.astrbot.android.feature.chat.runtime.AppChatRuntimeBindings
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.StateFlow

interface ChatViewModelRuntimeBindings : AppChatRuntimeBindings {
    val defaultSessionId: String
    val defaultSessionTitle: String
    val defaultAppChatPluginRuntime: AppChatPluginRuntime
    val selectedBotId: StateFlow<String>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val sessions: StateFlow<List<ConversationSession>>
    val personas: StateFlow<List<PersonaProfile>>

    fun createSession(botId: String): ConversationSession

    fun deleteSession(sessionId: String)

    fun renameSession(sessionId: String, title: String)

    fun toggleSessionPinned(sessionId: String)

    fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean? = null, sessionTtsEnabled: Boolean? = null)

    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String)

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun syncSystemSessionTitle(sessionId: String, title: String)

    fun saveConfig(profile: ConfigProfile)

    fun saveBot(profile: BotProfile)

    fun saveProvider(profile: ProviderProfile)

    suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String

    suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String

    suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String

    suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T

    val conversationRepositoryPort: ConversationRepositoryPort
    val appChatRuntimePort: AppChatRuntimePort
    val chatSessionController: ChatSessionController
    val sendAppMessageUseCase: SendAppMessageUseCase

    fun createChatSendHandler(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatSendHandler

    fun createAppChatPluginCommandService(
        appChatPluginRuntime: AppChatPluginRuntime,
    ): AppChatPluginCommandService
}


