package com.elymbot.android.di.hilt.runtime

import com.elymbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.elymbot.android.feature.plugin.runtime.EngineBackedAppChatPluginRuntime
import com.elymbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.elymbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import com.elymbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.elymbot.android.feature.plugin.runtime.PluginFailureGuard
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.elymbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.elymbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.elymbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.elymbot.android.feature.plugin.runtime.toolsource.FutureToolSourceContextResolver
import com.elymbot.android.feature.plugin.runtime.toolsource.FutureToolSourceRegistry
import com.elymbot.android.feature.plugin.runtime.toolsource.PortBackedFutureToolSourceContextResolver
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway as PluginHostCapabilityGatewayContract
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory as PluginHostCapabilityGatewayFactoryContract
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginRuntimeServicesModule {

    @Provides
    @Singleton
    fun providePluginHostCapabilityGateway(
        gatewayFactory: PluginHostCapabilityGatewayFactory,
    ): PluginHostCapabilityGateway = gatewayFactory.create()

    @Provides
    @Singleton
    fun providePluginHostCapabilityGatewayContract(
        gateway: PluginHostCapabilityGateway,
    ): PluginHostCapabilityGatewayContract = gateway

    @Provides
    @Singleton
    fun providePluginHostCapabilityGatewayFactoryContract(
        gatewayFactory: PluginHostCapabilityGatewayFactory,
    ): PluginHostCapabilityGatewayFactoryContract = gatewayFactory

    @Provides
    @Singleton
    fun providePluginExecutionEngine(
        failureGuard: PluginFailureGuard,
        scheduler: PluginRuntimeScheduler,
        logBus: PluginRuntimeLogBus,
    ): PluginExecutionEngine = PluginExecutionEngine(
        dispatcher = PluginRuntimeDispatcher(
            failureGuard = failureGuard,
            scheduler = scheduler,
            logBus = logBus,
        ),
        failureGuard = failureGuard,
        scheduler = scheduler,
        logBus = logBus,
    )

    @Provides
    @Singleton
    fun provideExternalPluginRuntimeCatalog(
        repositoryStatePort: PluginStateRepositoryPort,
    ): ExternalPluginRuntimeCatalog = ExternalPluginRuntimeCatalog(
        repositoryStatePort = repositoryStatePort,
    )

    @Provides
    @Singleton
    fun provideEngineBackedAppChatPluginRuntime(
        pluginCatalog: ExternalPluginRuntimeCatalog,
        engine: PluginExecutionEngine,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        pluginV2DispatchEngine: PluginV2DispatchEngine,
        pluginV2LifecycleManager: PluginV2LifecycleManager,
        logBus: PluginRuntimeLogBus,
        futureToolSourceRegistry: FutureToolSourceRegistry,
    ): EngineBackedAppChatPluginRuntime = EngineBackedAppChatPluginRuntime(
        pluginCatalog,
        engine,
        hostCapabilityGateway,
        activeRuntimeStore,
        pluginV2DispatchEngine,
        logBus,
        pluginV2LifecycleManager,
        futureToolSourceRegistry,
    )

    @Provides
    @Singleton
    fun provideAppChatPluginRuntime(
        runtime: EngineBackedAppChatPluginRuntime,
    ): AppChatPluginRuntime = runtime

    @Provides
    @Singleton
    fun provideAppChatLlmPipelineRuntime(
        runtime: EngineBackedAppChatPluginRuntime,
    ): AppChatLlmPipelineRuntime = runtime

    @Provides
    @Singleton
    fun providePluginV2MessageDispatchPort(
        pluginV2DispatchEngine: PluginV2DispatchEngine,
    ): PluginV2MessageDispatchPort = PluginV2MessageDispatchPort { event ->
        pluginV2DispatchEngine.dispatchMessage(event)
    }

    @Provides
    @Singleton
    fun provideFutureToolSourceContextResolver(
        resolver: PortBackedFutureToolSourceContextResolver,
    ): FutureToolSourceContextResolver = resolver

    @Provides
    @Singleton
    fun provideRuntimeLlmOrchestratorPort(): RuntimeLlmOrchestratorPort = DefaultRuntimeLlmOrchestrator()
}
