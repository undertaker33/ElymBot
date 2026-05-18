package com.elymbot.android.feature.chat.runtime

import com.elymbot.android.core.runtime.context.RuntimeBotSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMessageType
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.RuntimeStreamingMode
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginV2StreamingMode

internal fun BotProfile.toRuntimeBotSnapshot(): RuntimeBotSnapshot {
    return RuntimeBotSnapshot(
        id = id,
        displayName = displayName,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        configProfileId = configProfileId,
    )
}

internal fun MessageType.toRuntimeMessageType(): RuntimeMessageType {
    return when (this) {
        MessageType.FriendMessage -> RuntimeMessageType.FriendMessage
        MessageType.GroupMessage -> RuntimeMessageType.GroupMessage
        MessageType.OtherMessage -> RuntimeMessageType.OtherMessage
    }
}

internal fun RuntimeStreamingMode.toPluginStreamingMode(): PluginV2StreamingMode {
    return when (this) {
        RuntimeStreamingMode.NON_STREAM -> PluginV2StreamingMode.NON_STREAM
        RuntimeStreamingMode.NATIVE_STREAM -> PluginV2StreamingMode.NATIVE_STREAM
        RuntimeStreamingMode.PSEUDO_STREAM -> PluginV2StreamingMode.PSEUDO_STREAM
    }
}

internal fun RuntimeProviderSnapshot.toProviderProfile(): ProviderProfile {
    return ProviderProfile(
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
}

private fun String.toProviderType(): ProviderType =
    runCatching { ProviderType.valueOf(this) }.getOrDefault(ProviderType.CUSTOM)

private fun String.toProviderCapabilityOrNull(): ProviderCapability? =
    runCatching { ProviderCapability.valueOf(this) }.getOrNull()

private fun String.toFeatureSupportState(): FeatureSupportState =
    runCatching { FeatureSupportState.valueOf(this) }.getOrDefault(FeatureSupportState.UNKNOWN)
