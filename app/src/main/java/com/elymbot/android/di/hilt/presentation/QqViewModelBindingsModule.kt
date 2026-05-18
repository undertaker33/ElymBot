package com.elymbot.android.di.hilt.presentation

import com.elymbot.android.di.hilt.BridgeConfig
import com.elymbot.android.di.hilt.BridgeConfigSaver
import com.elymbot.android.di.hilt.BridgeRuntimeState
import com.elymbot.android.feature.qq.domain.QqBridgeStatePort
import com.elymbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.elymbot.android.feature.qq.presentation.QqLoginState
import com.elymbot.android.feature.qq.presentation.QqPresentationIoDispatcher
import com.elymbot.android.model.NapCatBridgeConfig
import com.elymbot.android.model.NapCatLoginState
import com.elymbot.android.model.NapCatRuntimeState
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
