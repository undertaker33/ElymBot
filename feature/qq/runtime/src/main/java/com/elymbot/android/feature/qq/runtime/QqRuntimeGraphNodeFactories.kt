package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.chat.runtime.botcommand.AndroidBotCommandStringResolver
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.cron.runtime.ScheduledTaskIntentFallbackResponder
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqPlatformConfigPort
import com.elymbot.android.feature.qq.domain.QqPluginExecutionPort
import com.elymbot.android.core.runtime.session.SessionLockCoordinator
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationAttachment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class QqBotCommandRuntimeServiceFactory @Inject constructor(
    private val strings: AndroidBotCommandStringResolver,
) {
    fun create(
        dependencies: QqOneBotRuntimeDependencies,
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        currentLanguageTag: () -> String,
        log: (String) -> Unit,
    ): QqBotCommandRuntimeService {
        return QqBotCommandRuntimeService(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            providerPort = dependencies.providerPort,
            conversationPort = dependencies.conversationPort,
            replySender = replySender,
            profileResolver = profileResolver,
            currentLanguageTag = currentLanguageTag,
            strings = strings,
            log = log,
        )
    }
}

@Singleton
internal class QqStreamingReplyServiceFactory @Inject constructor() {
    fun create(
        replySender: QqReplySender,
        synthesizeSpeech: (ProviderProfile, String, String, Boolean) -> ConversationAttachment,
        log: (String) -> Unit,
    ): QqStreamingReplyService {
        return QqStreamingReplyService(
            replySender = replySender,
            synthesizeSpeech = synthesizeSpeech,
            log = log,
        )
    }
}

@Singleton
internal class QqPluginDispatchServiceFactory @Inject constructor(
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val messageDispatchPort: PluginV2MessageDispatchPort,
    private val executionService: QqPluginExecutionPort,
) {
    fun create(
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        resolvePluginPrivateRootPath: (String) -> String,
        log: (String) -> Unit,
    ): QqPluginDispatchService {
        return QqPluginDispatchService(
            replySender = replySender,
            profileResolver = profileResolver,
            resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
            hostCapabilityGateway = hostCapabilityGateway,
            gatewayFactory = gatewayFactory,
            messageDispatchPort = messageDispatchPort,
            executionService = executionService,
            log = log,
        )
    }
}

@Singleton
internal class QqMessageRuntimeServiceFactory @Inject constructor(
    private val configPort: ConfigRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val platformConfigPort: QqPlatformConfigPort,
    private val orchestrator: RuntimeLlmOrchestratorPort,
    private val runtimeContextResolverPort: com.elymbot.android.core.runtime.context.RuntimeContextResolverPort,
    private val providerInvoker: QqProviderInvoker,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val scheduledTaskFallbackResponder: ScheduledTaskIntentFallbackResponder,
    private val sessionLockCoordinator: SessionLockCoordinator,
    private val strings: AndroidBotCommandStringResolver,
) {
    fun create(
        replySender: QqReplySender,
        llmRuntime: AppChatLlmPipelineRuntime,
        rateLimiter: QqRateLimiter,
        markMessageId: (String) -> Boolean,
        scheduleStashReplay: (BotProfile, ConfigProfile, String) -> Unit,
        currentLanguageTag: () -> String,
        transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
        profileResolver: QqRuntimeProfileResolver,
        botCommandRuntimeService: QqBotCommandRuntimeService,
        pluginDispatchService: QqPluginDispatchService,
        streamingReplyService: QqStreamingReplyService,
        executeLegacyPluginsDuringLlmDispatch: Boolean,
        log: (String) -> Unit,
    ): QqMessageRuntimeService {
        return QqMessageRuntimeService(
            configPort = configPort,
            conversationPort = conversationPort,
            platformConfigPort = platformConfigPort,
            orchestrator = orchestrator,
            runtimeContextResolverPort = runtimeContextResolverPort,
            replySender = replySender,
            llmRuntime = llmRuntime,
            providerInvoker = providerInvoker,
            rateLimiter = rateLimiter,
            markMessageId = markMessageId,
            scheduleStashReplay = scheduleStashReplay,
            currentLanguageTag = currentLanguageTag,
            strings = strings,
            transcribeAudio = transcribeAudio,
            profileResolver = profileResolver,
            botCommandRuntimeService = botCommandRuntimeService,
            pluginDispatchService = pluginDispatchService,
            streamingReplyService = streamingReplyService,
            gatewayFactory = gatewayFactory,
            scheduledTaskFallbackResponder = scheduledTaskFallbackResponder,
            sessionLockCoordinator = sessionLockCoordinator,
            executeLegacyPluginsDuringLlmDispatch = executeLegacyPluginsDuringLlmDispatch,
            log = log,
        )
    }
}
