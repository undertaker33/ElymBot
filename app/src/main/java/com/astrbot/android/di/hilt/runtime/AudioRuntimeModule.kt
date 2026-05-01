package com.astrbot.android.di.hilt.runtime

import android.content.Context
import com.astrbot.android.core.runtime.audio.AudioRuntimePort
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
    fun provideAudioRuntimePort(
        @ApplicationContext appContext: Context,
    ): AudioRuntimePort = CompatChatCompletionAudioRuntimePort(appContext)
}
