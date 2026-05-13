package com.astrbot.android.feature.provider.runtime

import android.content.Context
import android.net.Uri
import com.astrbot.android.core.runtime.audio.AudioRuntimePort
import com.astrbot.android.core.runtime.llm.LlmConversationAttachment
import com.astrbot.android.core.runtime.llm.LlmFeatureSupportState
import com.astrbot.android.core.runtime.llm.LlmProviderCapability
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.LlmProviderProfile
import com.astrbot.android.core.runtime.llm.LlmProviderType
import com.astrbot.android.feature.provider.api.runtime.ProviderRuntimePort
import com.astrbot.android.feature.provider.api.runtime.ProviderRuntimeSttProbeResult
import com.astrbot.android.feature.provider.api.runtime.ProviderRuntimeSubAssetState
import com.astrbot.android.feature.provider.api.runtime.ProviderRuntimeTtsAssetState
import com.astrbot.android.feature.provider.api.runtime.VoiceAssetImportResult
import com.astrbot.android.feature.provider.domain.model.FeatureSupportState
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.feature.provider.domain.model.ProviderType
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetPort
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetSubState
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetTtsState
import com.astrbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.astrbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

internal class DefaultProviderRuntimePort @Inject constructor(
    private val providerRepositoryPort: ProviderRepositoryPort,
    private val probePort: LlmProviderProbePort,
    private val audioRuntimePort: AudioRuntimePort,
    private val runtimeAssetPort: RuntimeAssetPort,
    private val ttsVoiceAssetPort: TtsVoiceAssetPort,
) : ProviderRuntimePort {
    override val providers: StateFlow<List<ProviderProfile>> = providerRepositoryPort.providers
    override val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = ttsVoiceAssetPort.assets

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
        return ttsVoiceAssetPort.listVoiceChoicesFor(provider?.id)
    }

    override fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String,
        assetId: String?,
    ): VoiceAssetImportResult {
        val result = ttsVoiceAssetPort.importReferenceAudio(
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
        ttsVoiceAssetPort.saveProviderBinding(
            assetId = assetId,
            providerId = providerId,
            providerTypeName = providerType.name,
            model = model,
            voiceId = voiceId,
            displayName = displayName,
        )
    }

    override fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String) {
        ttsVoiceAssetPort.renameBinding(
            assetId = assetId,
            bindingId = bindingId,
            displayName = displayName,
        )
    }

    override fun clearReferenceAudio(assetId: String) {
        ttsVoiceAssetPort.clearReferenceAudio(assetId)
    }

    override fun deleteReferenceClip(assetId: String, clipId: String) {
        ttsVoiceAssetPort.deleteReferenceClip(assetId, clipId)
    }

    override fun deleteVoiceBinding(assetId: String, bindingId: String) {
        ttsVoiceAssetPort.deleteBinding(assetId, bindingId)
    }

    override fun ttsAssetState(context: Context): ProviderRuntimeTtsAssetState {
        return runtimeAssetPort.ttsAssetState(context).toProviderRuntimeState()
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return audioRuntimePort.isSherpaFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return audioRuntimePort.isSherpaSttReady()
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

private fun RuntimeAssetTtsState.toProviderRuntimeState(): ProviderRuntimeTtsAssetState {
    return ProviderRuntimeTtsAssetState(
        framework = framework.toProviderRuntimeState(),
        kokoro = kokoro.toProviderRuntimeState(),
    )
}

private fun RuntimeAssetSubState.toProviderRuntimeState(): ProviderRuntimeSubAssetState {
    return ProviderRuntimeSubAssetState(
        installed = installed,
        details = details,
    )
}

private fun ProviderProfile.toLlmProviderProfile(): LlmProviderProfile {
    return LlmProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toLlmProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.map { it.toLlmProviderCapability() }.toSet(),
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.toLlmFeatureSupportState(),
        multimodalProbeSupport = multimodalProbeSupport.toLlmFeatureSupportState(),
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.toLlmFeatureSupportState(),
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.toLlmFeatureSupportState(),
        sttProbeSupport = sttProbeSupport.toLlmFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toLlmFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

private fun FeatureSupportState.toLlmFeatureSupportState(): LlmFeatureSupportState =
    LlmFeatureSupportState.valueOf(name)

private fun LlmFeatureSupportState.toFeatureSupportState(): FeatureSupportState =
    FeatureSupportState.valueOf(name)

private fun ProviderType.toLlmProviderType(): LlmProviderType =
    runCatching { LlmProviderType.valueOf(name) }.getOrDefault(LlmProviderType.CUSTOM)

private fun ProviderCapability.toLlmProviderCapability(): LlmProviderCapability =
    LlmProviderCapability.valueOf(name)

private fun LlmConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}
