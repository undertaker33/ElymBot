package com.astrbot.android.feature.provider.data

import com.astrbot.android.feature.provider.domain.model.FeatureSupportState
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderType

internal fun ProviderType.defaultCapability(): ProviderCapability {
    return when (this) {
        ProviderType.WHISPER_API,
        ProviderType.XINFERENCE_STT,
        ProviderType.BAILIAN_STT,
        ProviderType.SHERPA_ONNX_STT,
        -> ProviderCapability.STT

        ProviderType.OPENAI_TTS,
        ProviderType.BAILIAN_TTS,
        ProviderType.MINIMAX_TTS,
        ProviderType.SHERPA_ONNX_TTS,
        -> ProviderCapability.TTS

        ProviderType.DIFY,
        ProviderType.BAILIAN_APP,
        -> ProviderCapability.AGENT_EXECUTOR

        ProviderType.TAVILY_SEARCH,
        ProviderType.BRAVE_SEARCH,
        ProviderType.BOCHA_SEARCH,
        ProviderType.BAIDU_AI_SEARCH,
        -> ProviderCapability.SEARCH

        else -> ProviderCapability.CHAT
    }
}

internal fun inferNativeStreamingRuleSupport(
    providerType: ProviderType,
    model: String,
): FeatureSupportState {
    val normalizedModel = model.trim().lowercase()
    if (providerType.defaultCapability() != ProviderCapability.CHAT) return FeatureSupportState.UNSUPPORTED
    return when (providerType) {
        ProviderType.GEMINI -> FeatureSupportState.SUPPORTED
        ProviderType.OLLAMA -> if (normalizedModel.isBlank()) FeatureSupportState.UNKNOWN else FeatureSupportState.SUPPORTED
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.ZHIPU,
        ProviderType.XAI,
        -> if (normalizedModel.isBlank()) FeatureSupportState.UNKNOWN else FeatureSupportState.SUPPORTED

        else -> FeatureSupportState.UNSUPPORTED
    }
}

internal fun inferMultimodalRuleSupport(
    providerType: ProviderType,
    model: String,
): FeatureSupportState {
    val normalizedModel = model.trim().lowercase()
    if (providerType.defaultCapability() != ProviderCapability.CHAT) return FeatureSupportState.UNSUPPORTED

    return when (providerType) {
        ProviderType.GEMINI -> FeatureSupportState.SUPPORTED
        ProviderType.OLLAMA -> when {
            normalizedModel.contains("vision") ||
                normalizedModel.contains("vl") ||
                normalizedModel.contains("omni") -> FeatureSupportState.SUPPORTED
            normalizedModel.isBlank() -> FeatureSupportState.UNKNOWN
            else -> FeatureSupportState.UNSUPPORTED
        }

        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.ZHIPU,
        ProviderType.XAI,
        -> when {
            normalizedModel.isBlank() -> FeatureSupportState.UNKNOWN
            normalizedModel.contains("gpt-4o") ||
                normalizedModel.contains("vision") ||
                normalizedModel.contains("vl") ||
                normalizedModel.contains("omni") ||
                normalizedModel.contains("4v") ||
                normalizedModel.contains("janus") ||
                normalizedModel.contains("gemma-3") -> FeatureSupportState.SUPPORTED
            providerType == ProviderType.XAI && normalizedModel.contains("grok") -> FeatureSupportState.SUPPORTED
            else -> FeatureSupportState.UNSUPPORTED
        }

        else -> FeatureSupportState.UNSUPPORTED
    }
}
