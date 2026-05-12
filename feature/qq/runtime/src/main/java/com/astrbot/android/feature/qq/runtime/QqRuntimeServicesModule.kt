package com.astrbot.android.feature.qq.runtime

import android.content.Context
import com.astrbot.android.core.runtime.audio.SilkAudioEncoder
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginWorkspacePathPort
import com.astrbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.astrbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.domain.QqPluginExecutionPort
import com.astrbot.android.feature.qq.domain.QqScheduledMessageSender
import com.astrbot.android.feature.qq.domain.QqStartupPort
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
