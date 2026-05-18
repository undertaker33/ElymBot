package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.core.runtime.context.RuntimeBotSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMessageType
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.RuntimeStreamingMode
import com.elymbot.android.core.runtime.llm.LlmConversationAttachment
import com.elymbot.android.core.runtime.llm.LlmConversationMessage
import com.elymbot.android.core.runtime.llm.LlmConversationToolCall
import com.elymbot.android.core.runtime.llm.LlmFeatureSupportState
import com.elymbot.android.core.runtime.llm.LlmProviderCapability
import com.elymbot.android.core.runtime.llm.LlmProviderProfile
import com.elymbot.android.core.runtime.llm.LlmProviderType
import com.elymbot.android.core.runtime.llm.LlmRuntimeConfig
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationToolCall
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginV2StreamingMode

internal fun BotProfile.toRuntimeBotSnapshot(): RuntimeBotSnapshot =
    RuntimeBotSnapshot(
        id = id,
        displayName = displayName,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        configProfileId = configProfileId,
    )

internal fun RuntimeProviderSnapshot.toProviderProfile(): ProviderProfile =
    ProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.mapNotNull { it.toProviderCapabilityOrNull() }.toSet()
            .ifEmpty { setOf(ProviderCapability.CHAT) },
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.toFeatureSupportState(),
        multimodalProbeSupport = multimodalProbeSupport.toFeatureSupportState(),
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.toFeatureSupportState(),
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.toFeatureSupportState(),
        sttProbeSupport = sttProbeSupport.toFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )

internal fun ProviderProfile.toLlmProviderProfile(): LlmProviderProfile =
    LlmProviderProfile(
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

internal fun ConfigProfile.toLlmRuntimeConfig(): LlmRuntimeConfig =
    LlmRuntimeConfig(
        id = id,
        imageCaptionTextEnabled = imageCaptionTextEnabled,
        defaultVisionProviderId = defaultVisionProviderId,
    )

internal fun ConversationAttachment.toLlmConversationAttachment(): LlmConversationAttachment =
    LlmConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )

internal fun LlmConversationAttachment.toConversationAttachment(): ConversationAttachment =
    ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )

internal fun ConversationMessage.toLlmConversationMessage(): LlmConversationMessage =
    LlmConversationMessage(
        id = id,
        role = role,
        content = content,
        timestamp = timestamp,
        attachments = attachments.map { it.toLlmConversationAttachment() },
        toolCallId = toolCallId,
        assistantToolCalls = assistantToolCalls.map { it.toLlmConversationToolCall() },
    )

internal fun List<ConversationMessage>.toLlmConversationMessages(): List<LlmConversationMessage> =
    map { it.toLlmConversationMessage() }

internal fun MessageType.toRuntimeMessageType(): RuntimeMessageType =
    when (this) {
        MessageType.FriendMessage -> RuntimeMessageType.FriendMessage
        MessageType.GroupMessage -> RuntimeMessageType.GroupMessage
        MessageType.OtherMessage -> RuntimeMessageType.OtherMessage
    }

internal fun RuntimeMessageType.toMessageType(): MessageType =
    when (this) {
        RuntimeMessageType.FriendMessage -> MessageType.FriendMessage
        RuntimeMessageType.GroupMessage -> MessageType.GroupMessage
        RuntimeMessageType.OtherMessage -> MessageType.OtherMessage
    }

internal fun RuntimeStreamingMode.toPluginStreamingMode(): PluginV2StreamingMode =
    when (this) {
        RuntimeStreamingMode.NON_STREAM -> PluginV2StreamingMode.NON_STREAM
        RuntimeStreamingMode.NATIVE_STREAM -> PluginV2StreamingMode.NATIVE_STREAM
        RuntimeStreamingMode.PSEUDO_STREAM -> PluginV2StreamingMode.PSEUDO_STREAM
    }

private fun ConversationToolCall.toLlmConversationToolCall(): LlmConversationToolCall =
    LlmConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
    )

private fun String.toProviderType(): ProviderType =
    runCatching { ProviderType.valueOf(this) }.getOrDefault(ProviderType.CUSTOM)

private fun String.toProviderCapabilityOrNull(): ProviderCapability? =
    runCatching { ProviderCapability.valueOf(this) }.getOrNull()

private fun String.toFeatureSupportState(): FeatureSupportState =
    runCatching { FeatureSupportState.valueOf(this) }.getOrDefault(FeatureSupportState.UNKNOWN)

private fun FeatureSupportState.toLlmFeatureSupportState(): LlmFeatureSupportState =
    LlmFeatureSupportState.valueOf(name)

private fun ProviderType.toLlmProviderType(): LlmProviderType =
    runCatching { LlmProviderType.valueOf(name) }.getOrDefault(LlmProviderType.CUSTOM)

private fun ProviderCapability.toLlmProviderCapability(): LlmProviderCapability =
    LlmProviderCapability.valueOf(name)
