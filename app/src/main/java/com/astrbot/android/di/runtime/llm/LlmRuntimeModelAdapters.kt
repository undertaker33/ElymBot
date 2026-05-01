package com.astrbot.android.di.runtime.llm

import com.astrbot.android.core.runtime.llm.LlmConversationAttachment
import com.astrbot.android.core.runtime.llm.LlmConversationMessage
import com.astrbot.android.core.runtime.llm.LlmConversationToolCall
import com.astrbot.android.core.runtime.llm.LlmFeatureSupportState
import com.astrbot.android.core.runtime.llm.LlmProviderCapability
import com.astrbot.android.core.runtime.llm.LlmProviderProfile
import com.astrbot.android.core.runtime.llm.LlmProviderType
import com.astrbot.android.core.runtime.llm.LlmRuntimeConfig
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationToolCall

internal fun ProviderProfile.toLlmProviderProfile(): LlmProviderProfile {
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

internal fun LlmProviderProfile.toProviderProfile(): ProviderProfile {
    return ProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.map { it.toProviderCapability() }.toSet(),
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.toFeatureSupportState(),
        multimodalProbeSupport = multimodalProbeSupport.toFeatureSupportState(),
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.toFeatureSupportState(),
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.toFeatureSupportState(),
        sttProbeSupport = sttProbeSupport.toFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

internal fun ConfigProfile.toLlmRuntimeConfig(): LlmRuntimeConfig {
    return LlmRuntimeConfig(
        id = id,
        imageCaptionTextEnabled = imageCaptionTextEnabled,
        defaultVisionProviderId = defaultVisionProviderId,
    )
}

internal fun LlmRuntimeConfig.toConfigProfile(): ConfigProfile {
    return ConfigProfile(
        id = id,
        imageCaptionTextEnabled = imageCaptionTextEnabled,
        defaultVisionProviderId = defaultVisionProviderId,
    )
}

internal fun ConversationMessage.toLlmConversationMessage(): LlmConversationMessage {
    return LlmConversationMessage(
        id = id,
        role = role,
        content = content,
        timestamp = timestamp,
        attachments = attachments.map { it.toLlmConversationAttachment() },
        toolCallId = toolCallId,
        assistantToolCalls = assistantToolCalls.map { it.toLlmConversationToolCall() },
    )
}

internal fun LlmConversationMessage.toConversationMessage(): ConversationMessage {
    return ConversationMessage(
        id = id,
        role = role,
        content = content,
        timestamp = timestamp,
        attachments = attachments.map { it.toConversationAttachment() },
        toolCallId = toolCallId,
        assistantToolCalls = assistantToolCalls.map { it.toConversationToolCall() },
    )
}

internal fun List<ConversationMessage>.toLlmConversationMessages(): List<LlmConversationMessage> =
    map { it.toLlmConversationMessage() }

internal fun List<LlmConversationMessage>.toConversationMessages(): List<ConversationMessage> =
    map { it.toConversationMessage() }

internal fun ConversationAttachment.toLlmConversationAttachment(): LlmConversationAttachment {
    return LlmConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun LlmConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

private fun ConversationToolCall.toLlmConversationToolCall(): LlmConversationToolCall {
    return LlmConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
    )
}

private fun LlmConversationToolCall.toConversationToolCall(): ConversationToolCall {
    return ConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
    )
}

internal fun FeatureSupportState.toLlmFeatureSupportState(): LlmFeatureSupportState =
    LlmFeatureSupportState.valueOf(name)

internal fun LlmFeatureSupportState.toFeatureSupportState(): FeatureSupportState =
    FeatureSupportState.valueOf(name)

internal fun ProviderType.toLlmProviderType(): LlmProviderType =
    runCatching { LlmProviderType.valueOf(name) }.getOrDefault(LlmProviderType.CUSTOM)

internal fun LlmProviderType.toProviderType(): ProviderType =
    runCatching { ProviderType.valueOf(name) }.getOrDefault(ProviderType.CUSTOM)

private fun ProviderCapability.toLlmProviderCapability(): LlmProviderCapability =
    LlmProviderCapability.valueOf(name)

private fun LlmProviderCapability.toProviderCapability(): ProviderCapability =
    ProviderCapability.valueOf(name)
