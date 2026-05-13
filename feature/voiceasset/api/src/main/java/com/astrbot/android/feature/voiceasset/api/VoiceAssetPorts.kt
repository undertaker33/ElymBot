package com.astrbot.android.feature.voiceasset.api

import android.content.Context
import android.net.Uri
import com.astrbot.android.feature.voiceasset.api.model.RuntimeAssetState
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import kotlinx.coroutines.flow.StateFlow

data class VoiceAssetImportResult(
    val asset: TtsVoiceReferenceAsset,
    val warning: String?,
)

data class RuntimeAssetSubState(
    val installed: Boolean,
    val details: String,
)

data class RuntimeAssetTtsState(
    val framework: RuntimeAssetSubState,
    val kokoro: RuntimeAssetSubState,
)

data class VoiceCloneProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerTypeName: String,
    val apiKey: String,
)

interface RuntimeAssetPort {
    val state: StateFlow<RuntimeAssetState>

    fun refresh(context: Context)

    suspend fun downloadAsset(context: Context, assetId: String)

    suspend fun clearAsset(context: Context, assetId: String)

    suspend fun downloadOnDeviceTtsModel(context: Context, modelId: String)

    suspend fun clearOnDeviceTtsModel(context: Context, modelId: String)

    fun ttsAssetState(context: Context): RuntimeAssetTtsState
}

interface TtsVoiceAssetPort {
    val assets: StateFlow<List<TtsVoiceReferenceAsset>>

    fun listVoiceChoicesFor(providerId: String?): List<Pair<String, String>>

    fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ): VoiceAssetImportResult

    fun saveProviderBinding(
        assetId: String,
        providerId: String,
        providerTypeName: String,
        model: String,
        voiceId: String,
        displayName: String,
    )

    fun renameBinding(assetId: String, bindingId: String, displayName: String)

    fun clearReferenceAudio(assetId: String)

    fun deleteReferenceClip(assetId: String, clipId: String)

    fun deleteBinding(assetId: String, bindingId: String)

    fun snapshotAssets(): List<TtsVoiceReferenceAsset>

    fun restoreAssets(assets: List<TtsVoiceReferenceAsset>)
}

interface VoiceCloneRuntimePort {
    fun cloneVoice(
        provider: VoiceCloneProviderProfile,
        asset: TtsVoiceReferenceAsset,
        displayName: String,
    ): String
}
