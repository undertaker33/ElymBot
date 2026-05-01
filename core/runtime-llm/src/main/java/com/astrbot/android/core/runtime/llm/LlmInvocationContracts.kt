package com.astrbot.android.core.runtime.llm

data class LlmProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: LlmProviderType,
    val apiKey: String,
    val capabilities: Set<LlmProviderCapability>,
    val enabled: Boolean = true,
    val multimodalRuleSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val multimodalProbeSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val nativeStreamingRuleSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val nativeStreamingProbeSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val sttProbeSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val ttsProbeSupport: LlmFeatureSupportState = LlmFeatureSupportState.UNKNOWN,
    val ttsVoiceOptions: List<String> = emptyList(),
)

enum class LlmProviderType {
    OPENAI_COMPATIBLE,
    DEEPSEEK,
    GEMINI,
    OLLAMA,
    QWEN,
    ZHIPU,
    XAI,
    WHISPER_API,
    XINFERENCE_STT,
    BAILIAN_STT,
    SHERPA_ONNX_STT,
    OPENAI_TTS,
    BAILIAN_TTS,
    MINIMAX_TTS,
    SHERPA_ONNX_TTS,
    DIFY,
    BAILIAN_APP,
    TAVILY_SEARCH,
    BRAVE_SEARCH,
    BOCHA_SEARCH,
    BAIDU_AI_SEARCH,
    ANTHROPIC,
    CUSTOM,
}

enum class LlmProviderCapability {
    CHAT,
    STT,
    TTS,
    AGENT_EXECUTOR,
    SEARCH,
}

enum class LlmFeatureSupportState {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

data class LlmRuntimeConfig(
    val id: String = "",
    val imageCaptionTextEnabled: Boolean = false,
    val defaultVisionProviderId: String = "",
)

data class LlmConversationAttachment(
    val id: String,
    val type: String = "image",
    val mimeType: String = "image/jpeg",
    val fileName: String = "",
    val base64Data: String = "",
    val remoteUrl: String = "",
)

data class LlmConversationToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachments: List<LlmConversationAttachment> = emptyList(),
    val toolCallId: String = "",
    val assistantToolCalls: List<LlmConversationToolCall> = emptyList(),
)

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class LlmToolCall(
    val id: String? = null,
    val name: String,
    val arguments: String,
)

data class LlmInvocationRequest(
    val provider: LlmProviderProfile,
    val messages: List<LlmConversationMessage>,
    val systemPrompt: String? = null,
    val config: LlmRuntimeConfig? = null,
    val availableProviders: List<LlmProviderProfile> = emptyList(),
    val tools: List<LlmToolDefinition> = emptyList(),
)

data class LlmInvocationResult(
    val text: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val finishReason: String? = null,
)

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class ToolCallDelta(
        val index: Int,
        val name: String?,
        val argumentsFragment: String,
    ) : LlmStreamEvent
    data class Completed(val result: LlmInvocationResult) : LlmStreamEvent
    data class Failed(val throwable: Throwable) : LlmStreamEvent
}
