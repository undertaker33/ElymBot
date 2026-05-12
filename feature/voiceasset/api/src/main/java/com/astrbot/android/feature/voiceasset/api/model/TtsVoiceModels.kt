package com.astrbot.android.feature.voiceasset.api.model

enum class VoiceAssetProviderType {
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
    ;

    companion object {
        fun fromName(
            value: String,
            fallback: VoiceAssetProviderType = OPENAI_TTS,
        ): VoiceAssetProviderType {
            return runCatching { valueOf(value) }.getOrDefault(fallback)
        }
    }
}

fun VoiceAssetProviderType.displayLabel(): String {
    return when (this) {
        VoiceAssetProviderType.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        VoiceAssetProviderType.DEEPSEEK -> "DeepSeek"
        VoiceAssetProviderType.GEMINI -> "Gemini"
        VoiceAssetProviderType.OLLAMA -> "Ollama"
        VoiceAssetProviderType.QWEN -> "Qwen"
        VoiceAssetProviderType.ZHIPU -> "Zhipu"
        VoiceAssetProviderType.XAI -> "xAI"
        VoiceAssetProviderType.WHISPER_API -> "Whisper API"
        VoiceAssetProviderType.XINFERENCE_STT -> "Xinference STT"
        VoiceAssetProviderType.BAILIAN_STT -> "Bailian STT"
        VoiceAssetProviderType.SHERPA_ONNX_STT -> "Sherpa ONNX STT"
        VoiceAssetProviderType.OPENAI_TTS -> "OpenAI TTS"
        VoiceAssetProviderType.BAILIAN_TTS -> "Bailian TTS"
        VoiceAssetProviderType.MINIMAX_TTS -> "MiniMax TTS"
        VoiceAssetProviderType.SHERPA_ONNX_TTS -> "Sherpa ONNX TTS"
        VoiceAssetProviderType.DIFY -> "Dify"
        VoiceAssetProviderType.BAILIAN_APP -> "Bailian App"
        VoiceAssetProviderType.TAVILY_SEARCH -> "Tavily Search"
        VoiceAssetProviderType.BRAVE_SEARCH -> "Brave Search"
        VoiceAssetProviderType.BOCHA_SEARCH -> "BoCha Search"
        VoiceAssetProviderType.BAIDU_AI_SEARCH -> "Baidu AI Search"
        VoiceAssetProviderType.ANTHROPIC -> "Anthropic"
        VoiceAssetProviderType.CUSTOM -> "Custom"
    }
}

data class ClonedVoiceBinding(
    val id: String,
    val providerId: String,
    val providerType: VoiceAssetProviderType,
    val model: String,
    val voiceId: String,
    val displayName: String,
    val createdAt: Long,
    val lastVerifiedAt: Long = 0L,
    val status: String = "ready",
)

data class TtsVoiceReferenceClip(
    val id: String,
    val localPath: String,
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TtsVoiceReferenceAsset(
    val id: String,
    val name: String,
    val source: String = "",
    val localPath: String = "",
    val remoteUrl: String = "",
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val clips: List<TtsVoiceReferenceClip> = emptyList(),
    val providerBindings: List<ClonedVoiceBinding> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
