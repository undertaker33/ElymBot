package com.elymbot.android.feature.voiceasset.data

import com.elymbot.android.feature.voiceasset.api.RuntimeAssetPort
import com.elymbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.elymbot.android.feature.voiceasset.api.VoiceCloneRuntimePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceAssetDataModule {
    @Binds
    @Singleton
    abstract fun bindRuntimeAssetPort(owner: RuntimeAssetStateOwner): RuntimeAssetPort

    @Binds
    @Singleton
    abstract fun bindTtsVoiceAssetPort(repository: TtsVoiceAssetRepository): TtsVoiceAssetPort

    @Binds
    @Singleton
    abstract fun bindVoiceCloneRuntimePort(service: VoiceCloneService): VoiceCloneRuntimePort
}
