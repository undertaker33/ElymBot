package com.astrbot.android.core.runtime.llm

data class SttProbeResult(
    val state: LlmFeatureSupportState,
    val transcript: String,
)

interface LlmProviderProbePort {
    fun fetchModels(baseUrl: String, apiKey: String, providerType: LlmProviderType): List<String>

    fun detectMultimodalRule(provider: LlmProviderProfile): LlmFeatureSupportState

    fun probeMultimodalSupport(provider: LlmProviderProfile): LlmFeatureSupportState

    fun detectNativeStreamingRule(provider: LlmProviderProfile): LlmFeatureSupportState

    fun probeNativeStreamingSupport(provider: LlmProviderProfile): LlmFeatureSupportState

    fun probeSttSupport(provider: LlmProviderProfile): SttProbeResult

    fun probeTtsSupport(provider: LlmProviderProfile): LlmFeatureSupportState

    fun transcribeAudio(
        provider: LlmProviderProfile,
        attachment: LlmConversationAttachment,
    ): String

    fun synthesizeSpeech(
        provider: LlmProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): LlmConversationAttachment
}
