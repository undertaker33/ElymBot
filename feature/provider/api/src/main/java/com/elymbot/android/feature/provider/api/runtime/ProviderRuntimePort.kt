package com.elymbot.android.feature.provider.api.runtime

import android.content.Context
import android.net.Uri
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EmptyProviderProfilesStateFlow = MutableStateFlow<List<ProviderProfile>>(emptyList())
private val EmptyVoiceAssetsStateFlow = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

data class VoiceAssetImportResult(
    val asset: TtsVoiceReferenceAsset,
    val warning: String?,
)

data class ProviderRuntimeSttProbeResult(
    val state: FeatureSupportState,
    val transcript: String,
)

data class ProviderRuntimeSubAssetState(
    val installed: Boolean,
    val details: String,
)

data class ProviderRuntimeTtsAssetState(
    val framework: ProviderRuntimeSubAssetState,
    val kokoro: ProviderRuntimeSubAssetState,
)

interface ProviderRuntimePort {
    val providers: StateFlow<List<ProviderProfile>>
        get() = EmptyProviderProfilesStateFlow
    val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>>
        get() = EmptyVoiceAssetsStateFlow

    fun fetchModels(provider: ProviderProfile): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): ProviderRuntimeSttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>>

    fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ): VoiceAssetImportResult

    fun saveVoiceBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    )

    fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String)

    fun clearReferenceAudio(assetId: String)

    fun deleteReferenceClip(assetId: String, clipId: String)

    fun deleteVoiceBinding(assetId: String, bindingId: String)

    fun ttsAssetState(context: Context): ProviderRuntimeTtsAssetState

    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}
