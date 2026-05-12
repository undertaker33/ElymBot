package com.astrbot.android.feature.provider.runtime

import android.content.Context
import android.net.Uri
import com.astrbot.android.core.runtime.audio.SherpaOnnxAssetManager
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.data.RuntimeAssetStateOwner
import com.astrbot.android.di.hilt.TtsVoiceAssetStateOwner
import com.astrbot.android.di.runtime.llm.toConversationAttachment
import com.astrbot.android.di.runtime.llm.toFeatureSupportState
import com.astrbot.android.di.runtime.llm.toLlmProviderProfile
import com.astrbot.android.di.runtime.llm.toLlmProviderType
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.provider.runtime.ProviderRuntimeSubAssetState
import com.astrbot.android.feature.provider.runtime.ProviderRuntimeTtsAssetState
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

internal class DefaultProviderRuntimePort @Inject constructor(
    private val providerRepositoryPort: ProviderRepositoryPort,
    private val probePort: LlmProviderProbePort,
    private val runtimeAssetStateOwner: RuntimeAssetStateOwner,
    private val ttsVoiceAssetStateOwner: TtsVoiceAssetStateOwner,
) : ProviderRuntimePort {
    override val providers: StateFlow<List<ProviderProfile>> = providerRepositoryPort.providers
    override val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = ttsVoiceAssetStateOwner.assets

    override fun fetchModels(provider: ProviderProfile): List<String> {
        return probePort.fetchModels(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            providerType = provider.providerType.toLlmProviderType(),
        )
    }

    override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return probePort.detectMultimodalRule(provider.toLlmProviderProfile()).toFeatureSupportState()
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeMultimodalSupport(provider.toLlmProviderProfile()).toFeatureSupportState()
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return probePort.detectNativeStreamingRule(provider.toLlmProviderProfile()).toFeatureSupportState()
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeNativeStreamingSupport(provider.toLlmProviderProfile()).toFeatureSupportState()
    }

    override fun probeSttSupport(provider: ProviderProfile): ProviderRuntimeSttProbeResult {
        val result = probePort.probeSttSupport(provider.toLlmProviderProfile())
        return ProviderRuntimeSttProbeResult(
            state = result.state.toFeatureSupportState(),
            transcript = result.transcript,
        )
    }

    override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeTtsSupport(provider.toLlmProviderProfile()).toFeatureSupportState()
    }

    override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return ttsVoiceAssetStateOwner.listVoiceChoicesFor(provider)
    }

    override fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String,
        assetId: String?,
    ): VoiceAssetImportResult {
        val result = ttsVoiceAssetStateOwner.importReferenceAudio(
            context = context,
            sourceUri = sourceUri,
            name = name,
            assetId = assetId,
        )
        return VoiceAssetImportResult(
            asset = result.asset,
            warning = result.warning,
        )
    }

    override fun saveVoiceBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        ttsVoiceAssetStateOwner.saveProviderBinding(
            assetId = assetId,
            providerId = providerId,
            providerType = providerType,
            model = model,
            voiceId = voiceId,
            displayName = displayName,
        )
    }

    override fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String) {
        ttsVoiceAssetStateOwner.renameBinding(
            assetId = assetId,
            bindingId = bindingId,
            displayName = displayName,
        )
    }

    override fun clearReferenceAudio(assetId: String) {
        ttsVoiceAssetStateOwner.clearReferenceAudio(assetId)
    }

    override fun deleteReferenceClip(assetId: String, clipId: String) {
        ttsVoiceAssetStateOwner.deleteReferenceClip(assetId, clipId)
    }

    override fun deleteVoiceBinding(assetId: String, bindingId: String) {
        ttsVoiceAssetStateOwner.deleteBinding(assetId, bindingId)
    }

    override fun ttsAssetState(context: Context): ProviderRuntimeTtsAssetState {
        return runtimeAssetStateOwner.ttsAssetState(context).toProviderRuntimeState()
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return SherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return SherpaOnnxBridge.isSttReady()
    }

    override fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return probePort.synthesizeSpeech(
            provider = provider.toLlmProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toConversationAttachment()
    }
}

private fun SherpaOnnxAssetManager.TtsAssetState.toProviderRuntimeState(): ProviderRuntimeTtsAssetState {
    return ProviderRuntimeTtsAssetState(
        framework = framework.toProviderRuntimeState(),
        kokoro = kokoro.toProviderRuntimeState(),
    )
}

private fun SherpaOnnxAssetManager.SubAssetState.toProviderRuntimeState(): ProviderRuntimeSubAssetState {
    return ProviderRuntimeSubAssetState(
        installed = installed,
        details = details,
    )
}
