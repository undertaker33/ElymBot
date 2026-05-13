package com.astrbot.android.app.integration.chat

import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.feature.bot.data.FeatureBotRepositoryStore
import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandServiceFactory
import com.astrbot.android.feature.chat.runtime.AppChatRuntimeBindings
import com.astrbot.android.feature.chat.runtime.AppChatRuntimeServiceFactory
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.feature.conversation.data.FeatureConversationRepositoryStore
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryStore
import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryStore
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.plugin.PluginTriggerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.StateFlow

enum class ChatIntegrationPluginTrigger {
    BeforeSendMessage,
    AfterModelResponse,
}

interface ChatIntegrationPluginCommandPort {
    fun isUnsupportedPluginCommand(content: String): Boolean

    fun handlePluginCommand(
        session: ConversationSession,
        bot: BotProfile,
        content: String,
        provider: ProviderProfile?,
        personaId: String,
        languageTag: String,
    ): Boolean

    fun dispatchPlugins(
        trigger: ChatIntegrationPluginTrigger,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressV2CommandStage: Boolean = false,
    ): Boolean
}

interface ChatRuntimeBindingsPort {
    val bots: StateFlow<List<BotProfile>>
    val providers: StateFlow<List<ProviderProfile>>
    val runtimeContextResolverPort: RuntimeContextResolverPort

    fun session(sessionId: String): ConversationSession
    fun resolveConfig(profileId: String): ConfigProfile
    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String

    suspend fun sendConfiguredChatWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
    ): LlmInvocationResult

    suspend fun sendConfiguredChatStreamWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
        onDelta: suspend (String) -> Unit,
        onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
    ): LlmInvocationResult

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment

    fun log(message: String)
}

interface ChatViewModelRuntimeBindingsAdapter {
    val bots: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val sessions: StateFlow<List<ConversationSession>>
    val personas: StateFlow<List<PersonaProfile>>

    fun session(sessionId: String): ConversationSession
    fun createSession(botId: String): ConversationSession
    fun deleteSession(sessionId: String)
    fun renameSession(sessionId: String, title: String)
    fun toggleSessionPinned(sessionId: String)
    fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?)
    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String)
    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String
    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)
    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    )
    fun syncSystemSessionTitle(sessionId: String, title: String)
    fun resolveConfig(profileId: String): ConfigProfile
    fun saveConfig(profile: ConfigProfile)
    fun saveBot(profile: BotProfile)
    fun saveProvider(profile: ProviderProfile)
    fun createAppChatRuntimePort(
        chatDependencies: ChatRuntimeBindingsPort,
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatRuntimePort
    fun createAppChatPluginCommandService(
        dependencies: ChatRuntimeBindingsPort,
        appChatPluginRuntime: AppChatPluginRuntime,
    ): ChatIntegrationPluginCommandPort
}

internal class DefaultChatViewModelRuntimeBindingsAdapter @Inject constructor(
    private val botStore: FeatureBotRepositoryStore,
    private val conversationStore: FeatureConversationRepositoryStore,
    private val configStore: FeatureConfigRepositoryStore,
    private val personaStore: FeaturePersonaRepositoryStore,
    private val providerStore: FeatureProviderRepositoryStore,
    private val appChatRuntimeServiceFactory: AppChatRuntimeServiceFactory,
    private val appChatPluginCommandServiceFactory: AppChatPluginCommandServiceFactory,
) : ChatViewModelRuntimeBindingsAdapter {
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

    override fun createAppChatRuntimePort(
        chatDependencies: ChatRuntimeBindingsPort,
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatRuntimePort {
        return appChatRuntimeServiceFactory.create(
            chatDependencies = chatDependencies.toRuntimeBindings(),
            appChatPluginRuntime = appChatPluginRuntime,
            ioDispatcher = ioDispatcher,
        )
    }

    override fun createAppChatPluginCommandService(
        dependencies: ChatRuntimeBindingsPort,
        appChatPluginRuntime: AppChatPluginRuntime,
    ): ChatIntegrationPluginCommandPort {
        val service = appChatPluginCommandServiceFactory.create(
            dependencies = dependencies.toRuntimeBindings(),
            appChatPluginRuntime = appChatPluginRuntime,
        )
        return RuntimeChatIntegrationPluginCommandPort(service)
    }
}

private fun ChatRuntimeBindingsPort.toRuntimeBindings(): AppChatRuntimeBindings {
    return object : AppChatRuntimeBindings {
        override val bots: StateFlow<List<BotProfile>> = this@toRuntimeBindings.bots
        override val providers: StateFlow<List<ProviderProfile>> = this@toRuntimeBindings.providers
        override val runtimeContextResolverPort: RuntimeContextResolverPort =
            this@toRuntimeBindings.runtimeContextResolverPort

        override fun session(sessionId: String): ConversationSession =
            this@toRuntimeBindings.session(sessionId)

        override fun resolveConfig(profileId: String): ConfigProfile =
            this@toRuntimeBindings.resolveConfig(profileId)

        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String = this@toRuntimeBindings.appendMessage(sessionId, role, content, attachments)

        override suspend fun sendConfiguredChatWithTools(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile?,
            availableProviders: List<ProviderProfile>,
            tools: List<LlmToolDefinition>,
        ): LlmInvocationResult = this@toRuntimeBindings.sendConfiguredChatWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = tools,
        )

        override suspend fun sendConfiguredChatStreamWithTools(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile?,
            availableProviders: List<ProviderProfile>,
            tools: List<LlmToolDefinition>,
            onDelta: suspend (String) -> Unit,
            onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
        ): LlmInvocationResult = this@toRuntimeBindings.sendConfiguredChatStreamWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = tools,
            onDelta = onDelta,
            onToolCallDelta = onToolCallDelta,
        )

        override suspend fun synthesizeSpeech(
            provider: ProviderProfile,
            text: String,
            voiceId: String,
            readBracketedContent: Boolean,
        ): ConversationAttachment = this@toRuntimeBindings.synthesizeSpeech(
            provider = provider,
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        )

        override fun log(message: String) {
            this@toRuntimeBindings.log(message)
        }
    }
}

private class RuntimeChatIntegrationPluginCommandPort(
    private val service: AppChatPluginCommandService,
) : ChatIntegrationPluginCommandPort {
    override fun isUnsupportedPluginCommand(content: String): Boolean =
        service.isUnsupportedPluginCommand(content)

    override fun handlePluginCommand(
        session: ConversationSession,
        bot: BotProfile,
        content: String,
        provider: ProviderProfile?,
        personaId: String,
        languageTag: String,
    ): Boolean = service.handlePluginCommand(
        session = session,
        bot = bot,
        content = content,
        provider = provider,
        personaId = personaId,
        languageTag = languageTag,
    )

    override fun dispatchPlugins(
        trigger: ChatIntegrationPluginTrigger,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressV2CommandStage: Boolean,
    ): Boolean = service.dispatchPlugins(
        trigger = trigger.toPluginTriggerSource(),
        session = session,
        message = message,
        provider = provider,
        bot = bot,
        personaId = personaId,
        config = config,
        suppressV2CommandStage = suppressV2CommandStage,
    )

    private fun ChatIntegrationPluginTrigger.toPluginTriggerSource(): PluginTriggerSource {
        return when (this) {
            ChatIntegrationPluginTrigger.BeforeSendMessage -> PluginTriggerSource.BeforeSendMessage
            ChatIntegrationPluginTrigger.AfterModelResponse -> PluginTriggerSource.AfterModelResponse
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatViewModelRuntimeBindingsAdapterModule {

    @Binds
    @Singleton
    abstract fun bindChatViewModelRuntimeBindingsAdapter(
        adapter: DefaultChatViewModelRuntimeBindingsAdapter,
    ): ChatViewModelRuntimeBindingsAdapter
}
