package com.elymbot.android.feature.chat.runtime

import com.elymbot.android.feature.chat.domain.AppChatRuntimePort
import com.elymbot.android.feature.plugin.domain.PluginWorkspacePathPort
import com.elymbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.elymbot.android.feature.cron.runtime.ScheduledTaskIntentFallbackResponder
import com.elymbot.android.feature.chat.runtime.botcommand.AndroidBotCommandStringResolver
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class AppChatProviderInvocationServiceFactory @Inject constructor() {
    fun create(
        chatDependencies: AppChatRuntimeBindings,
        ioDispatcher: CoroutineContext,
    ): AppChatProviderInvocationService {
        return AppChatProviderInvocationService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        )
    }
}

class AppChatPreparedReplyServiceFactory @Inject constructor() {
    fun create(
        chatDependencies: AppChatRuntimeBindings,
        ioDispatcher: CoroutineContext,
    ): AppChatPreparedReplyService {
        return AppChatPreparedReplyService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        )
    }
}

class AppChatRuntimeServiceFactory @Inject constructor(
    private val llmOrchestrator: RuntimeLlmOrchestratorPort,
    private val providerInvocationServiceFactory: AppChatProviderInvocationServiceFactory,
    private val preparedReplyServiceFactory: AppChatPreparedReplyServiceFactory,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val scheduledTaskFallbackResponder: ScheduledTaskIntentFallbackResponder,
) {
    fun create(
        chatDependencies: AppChatRuntimeBindings,
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatRuntimePort {
        return AppChatRuntimeService(
            chatDependencies = chatDependencies,
            appChatPluginRuntime = appChatPluginRuntime,
            llmOrchestrator = llmOrchestrator,
            providerInvocationService = providerInvocationServiceFactory.create(
                chatDependencies = chatDependencies,
                ioDispatcher = ioDispatcher,
            ),
            preparedReplyService = preparedReplyServiceFactory.create(
                chatDependencies = chatDependencies,
                ioDispatcher = ioDispatcher,
            ),
            gatewayFactory = gatewayFactory,
            scheduledTaskFallbackResponder = scheduledTaskFallbackResponder,
        )
    }
}

class AppChatPluginCommandServiceFactory @Inject constructor(
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
    private val messageDispatchPort: PluginV2MessageDispatchPort,
    private val pluginWorkspacePathPort: PluginWorkspacePathPort,
    private val strings: AndroidBotCommandStringResolver,
) {
    fun create(
        dependencies: AppChatRuntimeBindings,
        appChatPluginRuntime: AppChatPluginRuntime,
    ): AppChatPluginCommandService {
        return AppChatPluginCommandService(
            dependencies = dependencies,
            appChatPluginRuntime = appChatPluginRuntime,
            hostCapabilityGateway = hostCapabilityGateway,
            hostActionExecutor = hostActionExecutor,
            messageDispatchPort = messageDispatchPort,
            strings = strings,
            privateRootPathResolver = AppChatPluginPrivateRootPathResolver { pluginId ->
                pluginWorkspacePathPort.privateRootPath(pluginId)
            },
        )
    }
}

