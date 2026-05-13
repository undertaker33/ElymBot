package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal object VoiceAssetPresentationCompatModule {
    @Provides
    @Named("TtsVoiceAssets")
    fun provideTtsVoiceAssets(
        ttsVoiceAssetPort: TtsVoiceAssetPort,
    ): StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>> = ttsVoiceAssetPort.assets
}
