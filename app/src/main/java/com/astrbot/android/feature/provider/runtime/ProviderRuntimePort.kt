package com.astrbot.android.feature.provider.runtime

import android.content.Context
import com.astrbot.android.core.runtime.audio.SherpaOnnxAssetManager
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject

interface ProviderRuntimePort {
    fun fetchModels(provider: ProviderProfile): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>>

    fun ttsAssetState(context: Context): SherpaOnnxAssetManager.TtsAssetState

    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}

@Suppress("DEPRECATION")
internal class DefaultProviderRuntimePort @Inject constructor() : ProviderRuntimePort {
    override fun fetchModels(provider: ProviderProfile): List<String> {
        return ChatCompletionService.fetchModels(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            providerType = provider.providerType,
        )
    }

    override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.detectMultimodalRule(provider)
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeMultimodalSupport(provider)
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.detectNativeStreamingRule(provider)
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeNativeStreamingSupport(provider)
    }

    override fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult {
        return ChatCompletionService.probeSttSupport(provider)
    }

    override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeTtsSupport(provider)
    }

    override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return TtsVoiceAssetRepository.listVoiceChoicesFor(provider)
    }

    override fun ttsAssetState(context: Context): SherpaOnnxAssetManager.TtsAssetState {
        return RuntimeAssetRepository.ttsAssetState(context)
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
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }
}