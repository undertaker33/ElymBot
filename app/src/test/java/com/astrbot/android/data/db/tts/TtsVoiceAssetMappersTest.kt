package com.astrbot.android.data.db.tts

import com.astrbot.android.data.db.TtsVoiceAssetAggregate
import com.astrbot.android.data.db.TtsVoiceAssetEntity
import com.astrbot.android.data.db.TtsVoiceClipEntity
import com.astrbot.android.data.db.TtsVoiceProviderBindingEntity
import com.astrbot.android.feature.voiceasset.data.toModel
import com.astrbot.android.feature.voiceasset.data.toWriteModel
import com.astrbot.android.feature.voiceasset.api.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderType
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceClip
import com.astrbot.android.feature.voiceasset.api.model.VoiceAssetProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsVoiceAssetMappersTest {
    @Test
    fun assetAggregate_toModel_restoresClipAndBindingLists() {
        val aggregate = TtsVoiceAssetAggregate(
            asset = TtsVoiceAssetEntity(
                id = "asset-1",
                name = "Voice",
                source = "imported",
                localPath = "voice.wav",
                remoteUrl = "",
                durationMs = 1000L,
                sampleRateHz = 22050,
                createdAt = 1L,
                updatedAt = 2L,
            ),
            clips = listOf(
                TtsVoiceClipEntity("clip-1", "asset-1", "1.wav", 100L, 22050, 1L, 0),
                TtsVoiceClipEntity("clip-2", "asset-1", "2.wav", 200L, 22050, 2L, 1),
            ),
            providerBindings = listOf(
                TtsVoiceProviderBindingEntity(
                    id = "binding-1",
                    assetId = "asset-1",
                    providerId = "provider-1",
                    providerType = ProviderType.OPENAI_TTS.name,
                    model = "tts-1",
                    voiceId = "voice-1",
                    displayName = "Voice 1",
                    createdAt = 3L,
                    lastVerifiedAt = 4L,
                    status = "ready",
                    sortIndex = 0,
                ),
            ),
        )

        val model = aggregate.toModel()

        assertEquals(2, model.clips.size)
        assertEquals(1, model.providerBindings.size)
    }

    @Test
    fun asset_toWriteModel_flattensClipsAndBindings() {
        val asset = TtsVoiceReferenceAsset(
            id = "asset-1",
            name = "Voice",
            clips = listOf(TtsVoiceReferenceClip(id = "clip-1", localPath = "1.wav")),
            providerBindings = listOf(
                ClonedVoiceBinding(
                    id = "binding-1",
                    providerId = "provider-1",
                    providerType = VoiceAssetProviderType.OPENAI_TTS,
                    model = "tts-1",
                    voiceId = "voice-1",
                    displayName = "Voice 1",
                    createdAt = 3L,
                ),
            ),
        )

        val writeModel = asset.toWriteModel()

        assertEquals(1, writeModel.clips.size)
        assertEquals(1, writeModel.providerBindings.size)
        assertEquals("asset-1", writeModel.clips.single().assetId)
    }
}
