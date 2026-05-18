package com.elymbot.android.di.hilt.runtime

import android.content.Context
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.audio.AudioRuntimePort
import com.elymbot.android.core.runtime.audio.SherpaOnnxBridge
import com.elymbot.android.core.runtime.network.OkHttpRuntimeNetworkTransport
import com.elymbot.android.core.runtime.llm.ChatCompletionService
import com.elymbot.android.core.runtime.llm.ChatCompletionServiceLlmClient
import com.elymbot.android.core.runtime.llm.HiltLlmProviderProbePort
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmProviderProbePort
import com.elymbot.android.data.http.OkHttpElymBotHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object LlmRuntimeModule {

    @Provides
    @Singleton
    fun provideChatCompletionService(
        @ApplicationContext appContext: Context,
        sherpaOnnxBridge: SherpaOnnxBridge,
        runtimeLogger: RuntimeLogger,
    ): ChatCompletionService = ChatCompletionService(
        context = appContext,
        sherpaOnnxBridge = sherpaOnnxBridge,
        runtimeLogger = runtimeLogger,
        httpClient = OkHttpElymBotHttpClient(
            transport = OkHttpRuntimeNetworkTransport(),
            logger = runtimeLogger::append,
        ),
    )

    @Provides
    @Singleton
    fun provideLlmClientPort(
        chatCompletionService: ChatCompletionService,
    ): LlmClientPort = ChatCompletionServiceLlmClient(chatCompletionService = chatCompletionService)

    @Provides
    @Singleton
    fun provideLlmProviderProbePort(
        @ApplicationContext appContext: Context,
        chatCompletionService: ChatCompletionService,
        audioRuntimePort: AudioRuntimePort,
    ): LlmProviderProbePort = HiltLlmProviderProbePort(appContext, chatCompletionService, audioRuntimePort)

}
