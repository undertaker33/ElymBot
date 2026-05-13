package com.astrbot.android.di.hilt.runtime

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.audio.AudioRuntimePort
import com.astrbot.android.core.runtime.audio.DefaultSherpaOnnxAssetService
import com.astrbot.android.core.runtime.audio.SherpaOnnxAssetService
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.di.runtime.audio.CompatChatCompletionAudioRuntimePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AudioRuntimeModule {

    @Provides
    @Singleton
    fun provideSherpaOnnxAssetService(
        runtimeLogger: RuntimeLogger,
    ): SherpaOnnxAssetService = DefaultSherpaOnnxAssetService(runtimeLogger)

    @Provides
    @Singleton
    fun provideSherpaOnnxBridge(
        @ApplicationContext appContext: Context,
        sherpaOnnxAssetService: SherpaOnnxAssetService,
        runtimeLogger: RuntimeLogger,
    ): SherpaOnnxBridge = SherpaOnnxBridge(
        context = appContext,
        assetService = sherpaOnnxAssetService,
        runtimeLogger = runtimeLogger,
    )

    @Provides
    @Singleton
    fun provideAudioRuntimePort(
        compatPort: CompatChatCompletionAudioRuntimePort,
    ): AudioRuntimePort = compatPort
}
