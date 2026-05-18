
package com.elymbot.android.di.hilt

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmInvocationRequest
import com.elymbot.android.core.runtime.llm.LlmInvocationResult
import com.elymbot.android.core.runtime.llm.LlmProviderProbePort
import com.elymbot.android.core.runtime.llm.LlmStreamEvent
import com.elymbot.android.core.runtime.llm.LlmToolDefinition
import com.elymbot.android.core.runtime.session.SessionLockCoordinator
import com.elymbot.android.app.integration.chat.ChatIntegrationPluginCommandPort
import com.elymbot.android.app.integration.chat.ChatIntegrationPluginTrigger
import com.elymbot.android.app.integration.chat.ChatRuntimeBindingsPort
import com.elymbot.android.app.integration.chat.ChatViewModelRuntimeBindingsAdapter
import com.elymbot.android.di.runtime.llm.toConversationAttachment
import com.elymbot.android.di.runtime.llm.toLlmConversationAttachment
import com.elymbot.android.di.runtime.llm.toLlmConversationMessages
import com.elymbot.android.di.runtime.llm.toLlmProviderProfile
import com.elymbot.android.di.runtime.llm.toLlmRuntimeConfig
import com.elymbot.android.feature.chat.domain.AppChatRuntimePort
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.chat.domain.SendAppMessageUseCase
import com.elymbot.android.feature.chat.domain.SendAppMessageUseCaseFactory
import com.elymbot.android.feature.chat.presentation.AppChatSendHandlerFactory
import com.elymbot.android.feature.chat.presentation.AppChatSendHandler
import com.elymbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.elymbot.android.model.BotProfile
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.PersonaProfile
import com.elymbot.android.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import com.elymbot.android.ui.viewmodel.ChatPluginCommandPort
import com.elymbot.android.ui.viewmodel.ChatPluginTrigger
import com.elymbot.android.ui.viewmodel.ChatSessionController
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

internal class DefaultChatViewModelRuntimeBindings @Inject constructor(
    private val llmClientPort: LlmClientPort,
    private val llmProviderProbePort: LlmProviderProbePort,
    override val runtimeContextResolverPort: RuntimeContextResolverPort,
    private val injectedAppChatPluginRuntime: AppChatPluginRuntime,
    override val conversationRepositoryPort: ConversationRepositoryPort,
    private val runtimeBindingsAdapter: ChatViewModelRuntimeBindingsAdapter,
    private val sendAppMessageUseCaseFactory: SendAppMessageUseCaseFactory,
    private val appChatSendHandlerFactory: AppChatSendHandlerFactory,
    private val sessionLockCoordinator: SessionLockCoordinator,
    private val runtimeLogger: RuntimeLogger,
) : ChatViewModelRuntimeBindings, ChatRuntimeBindingsPort {

    override val defaultSessionId: String = "chat-main"
    override val defaultSessionTitle: String = "\u65B0\u5BF9\u8BDD"
    override val defaultAppChatPluginRuntime: AppChatPluginRuntime
        get() = injectedAppChatPluginRuntime
    override val bots: StateFlow<List<BotProfile>> = runtimeBindingsAdapter.bots
    override val selectedBotId: StateFlow<String> = runtimeBindingsAdapter.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = runtimeBindingsAdapter.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = runtimeBindingsAdapter.configProfiles
    override val sessions: StateFlow<List<ConversationSession>> = runtimeBindingsAdapter.sessions
    override val personas: StateFlow<List<PersonaProfile>> = runtimeBindingsAdapter.personas

    override fun session(sessionId: String): ConversationSession = runtimeBindingsAdapter.session(sessionId)

    override fun createSession(botId: String): ConversationSession = runtimeBindingsAdapter.createSession(botId = botId)

    override fun deleteSession(sessionId: String) {
        runtimeBindingsAdapter.deleteSession(sessionId)
    }

    override fun renameSession(sessionId: String, title: String) {
        runtimeBindingsAdapter.renameSession(sessionId, title)
    }

    override fun toggleSessionPinned(sessionId: String) {
        runtimeBindingsAdapter.toggleSessionPinned(sessionId)
    }

    override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) {
        runtimeBindingsAdapter.updateSessionServiceFlags(sessionId, sessionSttEnabled, sessionTtsEnabled)
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
        runtimeBindingsAdapter.updateSessionBindings(sessionId, providerId, personaId, botId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = runtimeBindingsAdapter.appendMessage(sessionId, role, content, attachments)

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        runtimeBindingsAdapter.replaceMessages(sessionId, messages)
    }

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        runtimeBindingsAdapter.updateMessage(sessionId, messageId, content, attachments)
    }

    override fun syncSystemSessionTitle(sessionId: String, title: String) {
        runtimeBindingsAdapter.syncSystemSessionTitle(sessionId, title)
    }

    override fun resolveConfig(profileId: String): ConfigProfile = runtimeBindingsAdapter.resolveConfig(profileId)

    override fun saveConfig(profile: ConfigProfile) {
        runtimeBindingsAdapter.saveConfig(profile)
    }

    override fun saveBot(profile: BotProfile) {
        runtimeBindingsAdapter.saveBot(profile)
    }

    override fun saveProvider(profile: ProviderProfile) {
        runtimeBindingsAdapter.saveProvider(profile)
    }

    override suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String {
        return llmProviderProbePort.transcribeAudio(
            provider = provider.toLlmProviderProfile(),
            attachment = attachment.toLlmConversationAttachment(),
        )
    }

    override suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String {
        return sendConfiguredChatWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = emptyList(),
        ).text
    }

    override suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String {
        return sendConfiguredChatStreamWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = emptyList(),
            onDelta = onDelta,
            onToolCallDelta = { _, _, _ -> },
        ).text
    }

    override suspend fun sendConfiguredChatWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
    ): LlmInvocationResult {
        return llmClientPort.sendWithTools(
            LlmInvocationRequest(
                provider = provider.toLlmProviderProfile(),
                messages = messages.toLlmConversationMessages(),
                systemPrompt = systemPrompt,
                config = config?.toLlmRuntimeConfig(),
                availableProviders = availableProviders.map { it.toLlmProviderProfile() },
                tools = tools,
            ),
        )
    }

    override suspend fun sendConfiguredChatStreamWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
        onDelta: suspend (String) -> Unit,
        onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
    ): LlmInvocationResult {
        var completed: LlmInvocationResult? = null
        var failure: Throwable? = null
        val accumulatedText = StringBuilder()
        llmClientPort.streamWithTools(
            LlmInvocationRequest(
                provider = provider.toLlmProviderProfile(),
                messages = messages.toLlmConversationMessages(),
                systemPrompt = systemPrompt,
                config = config?.toLlmRuntimeConfig(),
                availableProviders = availableProviders.map { it.toLlmProviderProfile() },
                tools = tools,
            ),
        ).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    if (event.text.isNotBlank()) {
                        accumulatedText.append(event.text)
                        onDelta(event.text)
                    }
                }
                is LlmStreamEvent.ToolCallDelta -> {
                    onToolCallDelta(event.index, event.name.orEmpty(), event.argumentsFragment)
                }
                is LlmStreamEvent.Completed -> {
                    completed = event.result
                }
                is LlmStreamEvent.Failed -> {
                    failure = event.throwable
                }
            }
        }
        failure?.let { throw it }
        return completed ?: LlmInvocationResult(text = accumulatedText.toString())
    }

    override suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return llmProviderProbePort.synthesizeSpeech(
            provider = provider.toLlmProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toConversationAttachment()
    }

    override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
        return sessionLockCoordinator.withLock(sessionId, block)
    }

    override fun log(message: String) {
        runtimeLogger.append(message)
    }

    private fun createAppChatRuntimePort(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ): AppChatRuntimePort {
        return runtimeBindingsAdapter.createAppChatRuntimePort(
            chatDependencies = this,
            appChatPluginRuntime = appChatPluginRuntime,
            ioDispatcher = ioDispatcher,
        )
    }

    override val appChatRuntimePort: AppChatRuntimePort by lazy {
        createAppChatRuntimePort(injectedAppChatPluginRuntime)
    }

    override val sendAppMessageUseCase: SendAppMessageUseCase by lazy {
        sendAppMessageUseCaseFactory.create(runtime = appChatRuntimePort)
    }

    override val chatSessionController: ChatSessionController by lazy {
        ChatSessionController(this)
    }

    override fun createChatSendHandler(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatSendHandler {
        return appChatSendHandlerFactory.create(
            sendAppMessageUseCaseFactory.create(
                runtime = createAppChatRuntimePort(
                    appChatPluginRuntime = appChatPluginRuntime,
                    ioDispatcher = ioDispatcher,
                ),
            ),
        )
    }

    override fun createAppChatPluginCommandService(
        appChatPluginRuntime: AppChatPluginRuntime,
    ): ChatPluginCommandPort {
        val port = runtimeBindingsAdapter.createAppChatPluginCommandService(
            dependencies = this,
            appChatPluginRuntime = appChatPluginRuntime,
        )
        return port.toPresentationPort()
    }

    private fun ChatIntegrationPluginCommandPort.toPresentationPort(): ChatPluginCommandPort {
        return object : ChatPluginCommandPort {
            override fun isUnsupportedPluginCommand(content: String): Boolean =
                this@toPresentationPort.isUnsupportedPluginCommand(content)

            override fun handlePluginCommand(
                session: ConversationSession,
                bot: BotProfile,
                content: String,
                provider: ProviderProfile?,
                personaId: String,
                languageTag: String,
            ): Boolean = this@toPresentationPort.handlePluginCommand(
                session = session,
                bot = bot,
                content = content,
                provider = provider,
                personaId = personaId,
                languageTag = languageTag,
            )

            override fun dispatchPlugins(
                trigger: ChatPluginTrigger,
                session: ConversationSession,
                message: ConversationMessage,
                provider: ProviderProfile,
                bot: BotProfile?,
                personaId: String,
                config: ConfigProfile?,
                suppressV2CommandStage: Boolean,
            ): Boolean = this@toPresentationPort.dispatchPlugins(
                trigger = trigger.toIntegrationTrigger(),
                session = session,
                message = message,
                provider = provider,
                bot = bot,
                personaId = personaId,
                config = config,
                suppressV2CommandStage = suppressV2CommandStage,
            )
        }
    }

    private fun ChatPluginTrigger.toIntegrationTrigger(): ChatIntegrationPluginTrigger {
        return when (this) {
            ChatPluginTrigger.BeforeSendMessage -> ChatIntegrationPluginTrigger.BeforeSendMessage
            ChatPluginTrigger.AfterModelResponse -> ChatIntegrationPluginTrigger.AfterModelResponse
        }
    }
}

