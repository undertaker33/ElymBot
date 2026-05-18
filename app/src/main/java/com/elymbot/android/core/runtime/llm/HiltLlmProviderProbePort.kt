
package com.elymbot.android.core.runtime.llm

import android.content.Context
import com.elymbot.android.core.runtime.audio.AudioRuntimePort
import com.elymbot.android.di.runtime.audio.toAudioConversationAttachment
import com.elymbot.android.di.runtime.audio.toAudioProviderProfile
import com.elymbot.android.di.runtime.audio.toLlmConversationAttachment
import com.elymbot.android.di.runtime.audio.toLlmFeatureSupportState
import com.elymbot.android.di.runtime.llm.toFeatureSupportState
import com.elymbot.android.di.runtime.llm.toLlmFeatureSupportState
import com.elymbot.android.di.runtime.llm.toProviderProfile
import com.elymbot.android.di.runtime.llm.toProviderType

/**
 * Hilt-owned production probe implementation for LLM/media capability checks.
 */
internal class HiltLlmProviderProbePort(
    appContext: Context,
    private val chatCompletionService: ChatCompletionService,
    private val audioRuntimePort: AudioRuntimePort,
) : LlmProviderProbePort {
    private val appContext = appContext.applicationContext

    override fun fetchModels(baseUrl: String, apiKey: String, providerType: LlmProviderType): List<String> {
        return chatCompletionService.fetchModels(baseUrl, apiKey, providerType.toProviderType())
    }

    override fun detectMultimodalRule(provider: LlmProviderProfile): LlmFeatureSupportState {
        return chatCompletionService.detectMultimodalRule(provider.toProviderProfile()).toLlmFeatureSupportState()
    }

    override fun probeMultimodalSupport(provider: LlmProviderProfile): LlmFeatureSupportState {
        return chatCompletionService.probeMultimodalSupport(
            provider.toProviderProfile(),
            appContext,
        ).toLlmFeatureSupportState()
    }

    override fun detectNativeStreamingRule(provider: LlmProviderProfile): LlmFeatureSupportState {
        return chatCompletionService.detectNativeStreamingRule(provider.toProviderProfile()).toLlmFeatureSupportState()
    }

    override fun probeNativeStreamingSupport(provider: LlmProviderProfile): LlmFeatureSupportState {
        return chatCompletionService.probeNativeStreamingSupport(provider.toProviderProfile()).toLlmFeatureSupportState()
    }

    override fun probeSttSupport(provider: LlmProviderProfile): SttProbeResult {
        val result = audioRuntimePort.probeSttSupport(provider.toAudioProviderProfile())
        return SttProbeResult(
            state = result.state.toLlmFeatureSupportState(),
            transcript = result.transcript,
        )
    }

    override fun probeTtsSupport(provider: LlmProviderProfile): LlmFeatureSupportState {
        return audioRuntimePort.probeTtsSupport(provider.toAudioProviderProfile()).toLlmFeatureSupportState()
    }

    override fun transcribeAudio(
        provider: LlmProviderProfile,
        attachment: LlmConversationAttachment,
    ): String {
        return audioRuntimePort.transcribeAudio(
            provider = provider.toAudioProviderProfile(),
            attachment = attachment.toAudioConversationAttachment(),
        )
    }

    override fun synthesizeSpeech(
        provider: LlmProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): LlmConversationAttachment {
        return audioRuntimePort.synthesizeSpeech(
            provider = provider.toAudioProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toLlmConversationAttachment()
    }
}
