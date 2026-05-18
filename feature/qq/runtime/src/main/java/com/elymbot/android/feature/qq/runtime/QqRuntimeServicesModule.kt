package com.elymbot.android.feature.qq.runtime

import android.content.Context
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.audio.SilkAudioEncoder
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmProviderProbePort
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.plugin.domain.PluginWorkspacePathPort
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqPlatformConfigPort
import com.elymbot.android.feature.qq.domain.QqPluginExecutionPort
import com.elymbot.android.feature.qq.domain.QqScheduledMessageSender
import com.elymbot.android.feature.qq.domain.QqStartupPort
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object QqRuntimeServicesModule {

    @Provides
    @Singleton
    fun provideQqProviderInvoker(
        llmClientPort: LlmClientPort,
    ): QqProviderInvoker = DefaultQqProviderInvoker(llmClientPort)

    @Provides
    @Singleton
    fun provideQqOneBotRuntimeDependencies(
        @ApplicationContext appContext: Context,
        botPort: BotRepositoryPort,
        configPort: ConfigRepositoryPort,
        personaPort: PersonaRepositoryPort,
        providerPort: ProviderRepositoryPort,
        conversationPort: QqConversationPort,
        platformConfigPort: QqPlatformConfigPort,
        orchestrator: RuntimeLlmOrchestratorPort,
        runtimeContextResolverPort: RuntimeContextResolverPort,
        appChatLlmPipelineRuntime: AppChatLlmPipelineRuntime,
        pluginMessageDispatchPort: PluginV2MessageDispatchPort,
        providerInvoker: QqProviderInvoker,
        gatewayFactory: PluginHostCapabilityGatewayFactory,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        pluginExecutionService: QqPluginExecutionPort,
        llmProviderProbePort: LlmProviderProbePort,
        runtimeLogger: RuntimeLogger,
        silkAudioEncoder: SilkAudioEncoder,
        pluginWorkspacePathPort: PluginWorkspacePathPort,
    ): QqOneBotRuntimeDependencies {
        return QqOneBotRuntimeDependencies(
            botPort = botPort,
            configPort = configPort,
            personaPort = personaPort,
            providerPort = providerPort,
            conversationPort = conversationPort,
            platformConfigPort = platformConfigPort,
            orchestrator = orchestrator,
            runtimeContextResolverPort = runtimeContextResolverPort,
            appChatPluginRuntime = appChatLlmPipelineRuntime,
            pluginMessageDispatchPort = pluginMessageDispatchPort,
            providerInvoker = providerInvoker,
            gatewayFactory = gatewayFactory,
            hostCapabilityGateway = hostCapabilityGateway,
            pluginExecutionService = pluginExecutionService,
            llmProviderProbePort = llmProviderProbePort,
            runtimeLogger = runtimeLogger,
            silkAudioEncoder = silkAudioEncoder::encode,
            pluginWorkspacePathPort = pluginWorkspacePathPort,
            filesDirProvider = { appContext.filesDir },
        )
    }

    @Provides
    @Singleton
    fun provideQqRuntimeGraphFactory(
        factory: HiltQqRuntimeGraphFactory,
    ): QqRuntimeGraphFactory = factory

    @Provides
    @Singleton
    fun provideQqBridgeRuntime(
        runtime: HiltQqOneBotBridgeRuntime,
    ): QqBridgeRuntime = runtime

    @Provides
    @Singleton
    fun provideQqStartupPort(
        runtime: HiltQqOneBotBridgeRuntime,
    ): QqStartupPort = runtime

    @Provides
    @Singleton
    fun provideQqScheduledMessageSender(
        runtime: HiltQqOneBotBridgeRuntime,
    ): QqScheduledMessageSender = runtime
}
