package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.di.hilt.BridgeConfig
import com.astrbot.android.di.hilt.BridgeConfigSaver
import com.astrbot.android.di.hilt.BridgeRuntimeState
import com.astrbot.android.feature.qq.domain.QqBridgeStatePort
import com.astrbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.astrbot.android.feature.qq.presentation.QqLoginState
import com.astrbot.android.feature.qq.presentation.QqPresentationIoDispatcher
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal object QqViewModelBindingsModule {

    @Provides
    @BridgeConfig
    fun provideBridgeConfig(
        bridgeStatePort: QqBridgeStatePort,
    ): StateFlow<NapCatBridgeConfig> = bridgeStatePort.config

    @Provides
    @BridgeRuntimeState
    fun provideBridgeRuntimeState(
        bridgeStatePort: QqBridgeStatePort,
    ): StateFlow<NapCatRuntimeState> = bridgeStatePort.runtimeState

    @Provides
    fun provideBridgeConfigSaver(
        bridgeStatePort: QqBridgeStatePort,
    ): BridgeConfigSaver = BridgeConfigSaver(bridgeStatePort::updateConfig)

    @Provides
    @Named("BotLoginState")
    fun provideBotLoginState(
        loginRepository: QqLoginRepositoryPort,
    ): StateFlow<NapCatLoginState> = loginRepository.loginState

    @Provides
    @QqLoginState
    fun provideQqLoginState(
        loginRepository: QqLoginRepositoryPort,
    ): StateFlow<NapCatLoginState> = loginRepository.loginState

    @Provides
    @QqPresentationIoDispatcher
    fun provideQqPresentationIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
