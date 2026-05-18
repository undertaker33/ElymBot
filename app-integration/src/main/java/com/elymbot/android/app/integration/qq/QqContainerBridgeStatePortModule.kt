package com.elymbot.android.app.integration.qq

import com.elymbot.android.core.runtime.container.ContainerBridgeStatePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object QqContainerBridgeStatePortModule {
    @Provides
    @Singleton
    fun provideContainerBridgeStatePort(
        bridgeStatePort: ProductionContainerBridgeStatePort,
    ): ContainerBridgeStatePort = bridgeStatePort
}
