package com.astrbot.android.di.runtime.audio

import com.astrbot.android.core.runtime.audio.AudioConversationAttachment
import com.astrbot.android.core.runtime.audio.AudioFeatureSupportState
import com.astrbot.android.core.runtime.audio.AudioProviderCapability
import com.astrbot.android.core.runtime.audio.AudioProviderProfile
import com.astrbot.android.core.runtime.audio.AudioProviderType
import com.astrbot.android.core.runtime.llm.LlmConversationAttachment
import com.astrbot.android.core.runtime.llm.LlmFeatureSupportState
import com.astrbot.android.core.runtime.llm.LlmProviderCapability
import com.astrbot.android.core.runtime.llm.LlmProviderProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment

internal fun ProviderProfile.toAudioProviderProfile(): AudioProviderProfile {
    return AudioProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toAudioProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.mapNotNull { it.toAudioProviderCapabilityOrNull() }.toSet(),
        enabled = enabled,
        sttProbeSupport = sttProbeSupport.toAudioFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toAudioFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

internal fun AudioProviderProfile.toProviderProfile(): ProviderProfile {
    return ProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.map { it.toProviderCapability() }.toSet(),
        enabled = enabled,
        sttProbeSupport = sttProbeSupport.toFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

internal fun LlmProviderProfile.toAudioProviderProfile(): AudioProviderProfile {
    return AudioProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = runCatching { AudioProviderType.valueOf(providerType.name) }.getOrDefault(AudioProviderType.CUSTOM),
        apiKey = apiKey,
        capabilities = capabilities.mapNotNull { it.toAudioProviderCapabilityOrNull() }.toSet(),
        enabled = enabled,
        sttProbeSupport = sttProbeSupport.toAudioFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toAudioFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

internal fun ConversationAttachment.toAudioConversationAttachment(): AudioConversationAttachment {
    return AudioConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun AudioConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun LlmConversationAttachment.toAudioConversationAttachment(): AudioConversationAttachment {
    return AudioConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun AudioConversationAttachment.toLlmConversationAttachment(): LlmConversationAttachment {
    return LlmConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun AudioFeatureSupportState.toLlmFeatureSupportState(): LlmFeatureSupportState =
    LlmFeatureSupportState.valueOf(name)

private fun FeatureSupportState.toAudioFeatureSupportState(): AudioFeatureSupportState =
    AudioFeatureSupportState.valueOf(name)

private fun AudioFeatureSupportState.toFeatureSupportState(): FeatureSupportState =
    FeatureSupportState.valueOf(name)

private fun LlmFeatureSupportState.toAudioFeatureSupportState(): AudioFeatureSupportState =
    AudioFeatureSupportState.valueOf(name)

private fun ProviderType.toAudioProviderType(): AudioProviderType =
    runCatching { AudioProviderType.valueOf(name) }.getOrDefault(AudioProviderType.CUSTOM)

private fun AudioProviderType.toProviderType(): ProviderType =
    runCatching { ProviderType.valueOf(name) }.getOrDefault(ProviderType.CUSTOM)

private fun ProviderCapability.toAudioProviderCapabilityOrNull(): AudioProviderCapability? =
    runCatching { AudioProviderCapability.valueOf(name) }.getOrNull()

private fun LlmProviderCapability.toAudioProviderCapabilityOrNull(): AudioProviderCapability? =
    runCatching { AudioProviderCapability.valueOf(name) }.getOrNull()

private fun AudioProviderCapability.toProviderCapability(): ProviderCapability =
    ProviderCapability.valueOf(name)
