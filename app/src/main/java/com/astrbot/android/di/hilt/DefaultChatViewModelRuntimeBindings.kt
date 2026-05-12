
package com.astrbot.android.di.hilt

import com.astrbot.android.core.logging.SharedRuntimeLogStore
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.core.runtime.session.SessionLockCoordinator
import com.astrbot.android.di.runtime.llm.toConversationAttachment
import com.astrbot.android.di.runtime.llm.toLlmConversationAttachment
import com.astrbot.android.di.runtime.llm.toLlmConversationMessages
import com.astrbot.android.di.runtime.llm.toLlmProviderProfile
import com.astrbot.android.di.runtime.llm.toLlmRuntimeConfig
import com.astrbot.android.feature.bot.data.FeatureBotRepositoryStore
import com.astrbot.android.feature.conversation.data.FeatureConversationRepositoryStore
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCaseFactory
import com.astrbot.android.feature.chat.presentation.AppChatSendHandlerFactory
import com.astrbot.android.feature.chat.presentation.AppChatSendHandler
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandServiceFactory
import com.astrbot.android.feature.chat.runtime.AppChatRuntimeServiceFactory
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryStore
import com.astrbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryStore
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import com.astrbot.android.ui.viewmodel.ChatSessionController
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
    private val appChatRuntimeServiceFactory: AppChatRuntimeServiceFactory,
    private val sendAppMessageUseCaseFactory: SendAppMessageUseCaseFactory,
    private val appChatSendHandlerFactory: AppChatSendHandlerFactory,
    private val appChatPluginCommandServiceFactory: AppChatPluginCommandServiceFactory,
    private val sessionLockCoordinator: SessionLockCoordinator,
    private val botStore: FeatureBotRepositoryStore,
    private val conversationStore: FeatureConversationRepositoryStore,
    private val configStore: FeatureConfigRepositoryStore,
    private val personaStore: FeaturePersonaRepositoryStore,
    private val providerStore: FeatureProviderRepositoryStore,
) : ChatViewModelRuntimeBindings {

    override val defaultSessionId: String = "chat-main"
    override val defaultSessionTitle: String = "\u65B0\u5BF9\u8BDD"
    override val defaultAppChatPluginRuntime: AppChatPluginRuntime
        get() = injectedAppChatPluginRuntime
    override val bots: StateFlow<List<BotProfile>> = botStore.botProfiles
    override val selectedBotId: StateFlow<String> = botStore.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = providerStore.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = configStore.profiles
    override val sessions: StateFlow<List<ConversationSession>> = conversationStore.sessions
    override val personas: StateFlow<List<PersonaProfile>> = personaStore.personas

    override fun session(sessionId: String): ConversationSession = conversationStore.session(sessionId)

    override fun createSession(botId: String): ConversationSession = conversationStore.createSession(botId = botId)

    override fun deleteSession(sessionId: String) {
        conversationStore.deleteSession(sessionId)
    }

    override fun renameSession(sessionId: String, title: String) {
        conversationStore.renameSession(sessionId, title)
    }

    override fun toggleSessionPinned(sessionId: String) {
        conversationStore.toggleSessionPinned(sessionId)
    }

    override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) {
        conversationStore.updateSessionServiceFlags(sessionId, sessionSttEnabled, sessionTtsEnabled)
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
        conversationStore.updateSessionBindings(sessionId, providerId, personaId, botId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = conversationStore.appendMessage(sessionId, role, content, attachments)

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        conversationStore.replaceMessages(sessionId, messages)
    }

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        conversationStore.updateMessage(sessionId, messageId, content, attachments)
    }

    override fun syncSystemSessionTitle(sessionId: String, title: String) {
        conversationStore.syncSystemSessionTitle(sessionId, title)
    }

    override fun resolveConfig(profileId: String): ConfigProfile = configStore.resolve(profileId)

    override fun saveConfig(profile: ConfigProfile) {
        configStore.save(profile)
    }

    override fun saveBot(profile: BotProfile) {
        botStore.save(profile)
    }

    override fun saveProvider(profile: ProviderProfile) {
        providerStore.save(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            model = profile.model,
            providerType = profile.providerType,
            apiKey = profile.apiKey,
            capabilities = profile.capabilities,
            enabled = profile.enabled,
            multimodalRuleSupport = profile.multimodalRuleSupport,
            multimodalProbeSupport = profile.multimodalProbeSupport,
            nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
            nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
            sttProbeSupport = profile.sttProbeSupport,
            ttsProbeSupport = profile.ttsProbeSupport,
            ttsVoiceOptions = profile.ttsVoiceOptions,
        )
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
        SharedRuntimeLogStore.append(message)
    }

    private fun createAppChatRuntimePort(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ): AppChatRuntimePort {
        return appChatRuntimeServiceFactory.create(
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
    ): AppChatPluginCommandService {
        return appChatPluginCommandServiceFactory.create(
            dependencies = this,
            appChatPluginRuntime = appChatPluginRuntime,
        )
    }
}

