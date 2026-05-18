package com.elymbot.android.di.hilt

import com.elymbot.android.feature.plugin.runtime.DefaultPluginExecutionHostResolver
import com.elymbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.elymbot.android.feature.plugin.runtime.PluginFailureGuard
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.runtime.DefaultPluginExecutionHostOperations
import com.elymbot.android.feature.plugin.runtime.PluginExecutionHostOperations
import com.elymbot.android.feature.plugin.runtime.PluginExecutionHostResolver
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginHostCapabilityModule {

    @Binds
    @Singleton
    abstract fun bindPluginExecutionHostResolver(
        impl: DefaultPluginExecutionHostResolver,
    ): PluginExecutionHostResolver

    companion object {
        @Provides
        @Singleton
        @JvmStatic
        fun provideDefaultPluginExecutionHostOperations(
            factory: PluginDataWiringFactory,
        ): DefaultPluginExecutionHostOperations = factory.createDefaultPluginExecutionHostOperations()

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginExecutionHostOperations(
            operations: DefaultPluginExecutionHostOperations,
        ): PluginExecutionHostOperations = operations

        @Provides
        @Singleton
        @JvmStatic
        fun provideExternalPluginHostActionExecutor(
            failureGuard: PluginFailureGuard,
            logBus: PluginRuntimeLogBus,
        ): ExternalPluginHostActionExecutor = ExternalPluginHostActionExecutor(
            failureGuard = failureGuard,
            logBus = logBus,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginHostCapabilityGatewayFactory(
            resolver: PluginExecutionHostResolver,
            hostActionExecutor: ExternalPluginHostActionExecutor,
        ): PluginHostCapabilityGatewayFactory = PluginHostCapabilityGatewayFactory(
            resolver = resolver,
            hostActionExecutor = hostActionExecutor,
        )
    }
}
