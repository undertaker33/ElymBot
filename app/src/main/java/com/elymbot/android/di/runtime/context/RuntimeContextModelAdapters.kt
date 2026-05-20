package com.elymbot.android.di.runtime.context

import com.elymbot.android.core.runtime.context.RuntimeBotSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConfigResourceProjectionSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConversationAttachment
import com.elymbot.android.core.runtime.context.RuntimeConversationMessage
import com.elymbot.android.core.runtime.context.RuntimeConversationSessionSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConversationToolCall
import com.elymbot.android.core.runtime.context.RuntimeLegacySkillSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMcpServerSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMessageType
import com.elymbot.android.core.runtime.context.RuntimePersonaSnapshot
import com.elymbot.android.core.runtime.context.RuntimePersonaToolEnablementSnapshot
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.RuntimeResourceCenterCompatibilitySnapshot
import com.elymbot.android.core.runtime.context.RuntimeResourceCenterKind
import com.elymbot.android.core.runtime.context.RuntimeResourceItemSnapshot
import com.elymbot.android.core.runtime.context.RuntimeSkillResourceKind
import com.elymbot.android.core.runtime.context.RuntimeStreamingMode
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.model.BotProfile
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.McpServerEntry
import com.elymbot.android.model.PersonaProfile
import com.elymbot.android.model.PersonaToolEnablementSnapshot
import com.elymbot.android.model.ProviderCapability
import com.elymbot.android.model.ProviderProfile
import com.elymbot.android.model.ProviderType
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.SkillEntry
import com.elymbot.android.model.SkillResourceKind
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.ConversationToolCall
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.hasNativeStreamingSupport
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import com.elymbot.android.model.usesOpenAiStyleChatApi

internal fun BotProfile.toRuntimeBotSnapshot(): RuntimeBotSnapshot {
    return RuntimeBotSnapshot(
        id = id,
        displayName = displayName,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        configProfileId = configProfileId,
    )
}

internal fun ConfigProfile.toRuntimeConfigSnapshot(): RuntimeConfigSnapshot {
    return RuntimeConfigSnapshot(
        id = id,
        name = name,
        defaultChatProviderId = defaultChatProviderId,
        defaultVisionProviderId = defaultVisionProviderId,
        defaultSttProviderId = defaultSttProviderId,
        defaultTtsProviderId = defaultTtsProviderId,
        sttEnabled = sttEnabled,
        ttsEnabled = ttsEnabled,
        alwaysTtsEnabled = alwaysTtsEnabled,
        ttsReadBracketedContent = ttsReadBracketedContent,
        textStreamingEnabled = textStreamingEnabled,
        voiceStreamingEnabled = voiceStreamingEnabled,
        streamingMessageIntervalMs = streamingMessageIntervalMs,
        realWorldTimeAwarenessEnabled = realWorldTimeAwarenessEnabled,
        imageCaptionTextEnabled = imageCaptionTextEnabled,
        webSearchEnabled = webSearchEnabled,
        proactiveEnabled = proactiveEnabled,
        includeScheduledTaskConversationContext = includeScheduledTaskConversationContext,
        ttsVoiceId = ttsVoiceId,
        imageCaptionPrompt = imageCaptionPrompt,
        adminUids = adminUids,
        pluginCommandsAdminOnlyEnabled = pluginCommandsAdminOnlyEnabled,
        sessionIsolationEnabled = sessionIsolationEnabled,
        wakeWords = wakeWords,
        wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
        privateChatRequiresWakeWord = privateChatRequiresWakeWord,
        replyTextPrefix = replyTextPrefix,
        quoteSenderMessageEnabled = quoteSenderMessageEnabled,
        mentionSenderEnabled = mentionSenderEnabled,
        replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
        whitelistEnabled = whitelistEnabled,
        whitelistEntries = whitelistEntries,
        logOnWhitelistMiss = logOnWhitelistMiss,
        adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
        adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
        ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
        ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
        replyWhenPermissionDenied = replyWhenPermissionDenied,
        rateLimitWindowSeconds = rateLimitWindowSeconds,
        rateLimitMaxCount = rateLimitMaxCount,
        rateLimitStrategy = rateLimitStrategy,
        keywordDetectionEnabled = keywordDetectionEnabled,
        keywordPatterns = keywordPatterns,
        contextLimitStrategy = contextLimitStrategy,
        maxContextTurns = maxContextTurns,
        dequeueContextTurns = dequeueContextTurns,
        llmCompressInstruction = llmCompressInstruction,
        llmCompressKeepRecent = llmCompressKeepRecent,
        llmCompressProviderId = llmCompressProviderId,
        mcpServers = mcpServers.map { it.toRuntimeMcpServerSnapshot() },
        skills = skills.map { it.toRuntimeLegacySkillSnapshot() },
    )
}

internal fun RuntimeConfigSnapshot.toConfigProfile(): ConfigProfile {
    return ConfigProfile(
        id = id,
        name = name,
        defaultChatProviderId = defaultChatProviderId,
        defaultVisionProviderId = defaultVisionProviderId,
        defaultSttProviderId = defaultSttProviderId,
        defaultTtsProviderId = defaultTtsProviderId,
        sttEnabled = sttEnabled,
        ttsEnabled = ttsEnabled,
        alwaysTtsEnabled = alwaysTtsEnabled,
        ttsReadBracketedContent = ttsReadBracketedContent,
        textStreamingEnabled = textStreamingEnabled,
        voiceStreamingEnabled = voiceStreamingEnabled,
        streamingMessageIntervalMs = streamingMessageIntervalMs,
        realWorldTimeAwarenessEnabled = realWorldTimeAwarenessEnabled,
        imageCaptionTextEnabled = imageCaptionTextEnabled,
        webSearchEnabled = webSearchEnabled,
        proactiveEnabled = proactiveEnabled,
        includeScheduledTaskConversationContext = includeScheduledTaskConversationContext,
        ttsVoiceId = ttsVoiceId,
        imageCaptionPrompt = imageCaptionPrompt,
        adminUids = adminUids,
        pluginCommandsAdminOnlyEnabled = pluginCommandsAdminOnlyEnabled,
        sessionIsolationEnabled = sessionIsolationEnabled,
        wakeWords = wakeWords,
        wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
        privateChatRequiresWakeWord = privateChatRequiresWakeWord,
        replyTextPrefix = replyTextPrefix,
        quoteSenderMessageEnabled = quoteSenderMessageEnabled,
        mentionSenderEnabled = mentionSenderEnabled,
        replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
        whitelistEnabled = whitelistEnabled,
        whitelistEntries = whitelistEntries,
        logOnWhitelistMiss = logOnWhitelistMiss,
        adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
        adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
        ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
        ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
        replyWhenPermissionDenied = replyWhenPermissionDenied,
        rateLimitWindowSeconds = rateLimitWindowSeconds,
        rateLimitMaxCount = rateLimitMaxCount,
        rateLimitStrategy = rateLimitStrategy,
        keywordDetectionEnabled = keywordDetectionEnabled,
        keywordPatterns = keywordPatterns,
        contextLimitStrategy = contextLimitStrategy,
        maxContextTurns = maxContextTurns,
        dequeueContextTurns = dequeueContextTurns,
        llmCompressInstruction = llmCompressInstruction,
        llmCompressKeepRecent = llmCompressKeepRecent,
        llmCompressProviderId = llmCompressProviderId,
        mcpServers = mcpServers.map { it.toMcpServerEntry() },
        skills = skills.map { it.toSkillEntry() },
    )
}

internal fun RuntimeConfigSnapshot.toResourceConfigSnapshot(): ResourceConfigSnapshot {
    return ResourceConfigSnapshot(
        id = id,
        mcpServers = mcpServers.map { it.toMcpServerEntry() },
        skills = skills.map { it.toSkillEntry() },
    )
}

internal fun PersonaProfile.toRuntimePersonaSnapshot(): RuntimePersonaSnapshot {
    return RuntimePersonaSnapshot(
        id = id,
        name = name,
        systemPrompt = systemPrompt,
        enabledTools = enabledTools,
        defaultProviderId = defaultProviderId,
        enabled = enabled,
    )
}

internal fun RuntimePersonaToolEnablementSnapshot.toPersonaToolEnablementSnapshot(): PersonaToolEnablementSnapshot {
    return PersonaToolEnablementSnapshot(
        personaId = personaId,
        enabled = enabled,
        enabledTools = enabledTools,
    )
}

internal fun ProviderProfile.toRuntimeProviderSnapshot(): RuntimeProviderSnapshot {
    return RuntimeProviderSnapshot(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.name,
        apiKey = apiKey,
        capabilities = capabilities.map { it.name }.toSet(),
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.name,
        multimodalProbeSupport = multimodalProbeSupport.name,
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.name,
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.name,
        sttProbeSupport = sttProbeSupport.name,
        ttsProbeSupport = ttsProbeSupport.name,
        ttsVoiceOptions = ttsVoiceOptions,
        supportsToolCalling = providerType.usesOpenAiStyleChatApi(),
        supportsStreaming = hasNativeStreamingSupport(),
        supportsMultimodal = multimodalRuleSupport == FeatureSupportState.SUPPORTED ||
            multimodalProbeSupport == FeatureSupportState.SUPPORTED,
    )
}

internal fun RuntimeProviderSnapshot.toProviderProfile(): ProviderProfile {
    val resolvedType = providerType.toProviderType()
    return ProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = resolvedType,
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

internal fun ConversationSession.toRuntimeConversationSessionSnapshot(): RuntimeConversationSessionSnapshot {
    return RuntimeConversationSessionSnapshot(
        id = id,
        title = title,
        botId = botId,
        personaId = personaId,
        providerId = providerId,
        maxContextMessages = maxContextMessages,
        messages = messages.map { it.toRuntimeConversationMessage() },
    )
}

internal fun ConversationMessage.toRuntimeConversationMessage(): RuntimeConversationMessage {
    return RuntimeConversationMessage(
        id = id,
        role = role,
        content = content,
        timestamp = timestamp,
        attachments = attachments.map { it.toRuntimeConversationAttachment() },
        toolCallId = toolCallId,
        assistantToolCalls = assistantToolCalls.map { it.toRuntimeConversationToolCall() },
    )
}

internal fun RuntimeConversationMessage.toConversationMessage(): ConversationMessage {
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

internal fun List<RuntimeConversationMessage>.toConversationMessages(): List<ConversationMessage> =
    map { it.toConversationMessage() }

internal fun ConversationAttachment.toRuntimeConversationAttachment(): RuntimeConversationAttachment {
    return RuntimeConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun RuntimeConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

internal fun ConversationToolCall.toRuntimeConversationToolCall(): RuntimeConversationToolCall {
    return RuntimeConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
    )
}

internal fun RuntimeConversationToolCall.toConversationToolCall(): ConversationToolCall {
    return ConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
    )
}

internal fun McpServerEntry.toRuntimeMcpServerSnapshot(): RuntimeMcpServerSnapshot {
    return RuntimeMcpServerSnapshot(
        serverId = serverId,
        name = name,
        url = url,
        transport = transport,
        command = command,
        args = args,
        headers = headers,
        timeoutSeconds = timeoutSeconds,
        active = active,
    )
}

internal fun RuntimeMcpServerSnapshot.toMcpServerEntry(): McpServerEntry {
    return McpServerEntry(
        serverId = serverId,
        name = name,
        url = url,
        transport = transport,
        command = command,
        args = args,
        headers = headers,
        timeoutSeconds = timeoutSeconds,
        active = active,
    )
}

internal fun SkillEntry.toRuntimeLegacySkillSnapshot(): RuntimeLegacySkillSnapshot {
    return RuntimeLegacySkillSnapshot(
        skillId = skillId,
        name = name,
        description = description,
        content = content,
        priority = priority,
        active = active,
    )
}

internal fun RuntimeLegacySkillSnapshot.toSkillEntry(): SkillEntry {
    return SkillEntry(
        skillId = skillId,
        name = name,
        description = description,
        content = content,
        priority = priority,
        active = active,
    )
}

internal fun ResourceCenterCompatibilitySnapshot.toRuntimeResourceCenterCompatibilitySnapshot(): RuntimeResourceCenterCompatibilitySnapshot {
    return RuntimeResourceCenterCompatibilitySnapshot(
        resources = resources.map { it.toRuntimeResourceItemSnapshot() },
        projections = projections.map { it.toRuntimeConfigResourceProjectionSnapshot() },
    )
}

private fun ResourceCenterItem.toRuntimeResourceItemSnapshot(): RuntimeResourceItemSnapshot {
    return RuntimeResourceItemSnapshot(
        resourceId = resourceId,
        kind = kind.toRuntimeResourceCenterKind(),
        skillKind = skillKind?.toRuntimeSkillResourceKind(),
        name = name,
        description = description,
        content = content,
        payloadJson = payloadJson,
        source = source,
        enabled = enabled,
    )
}

private fun ConfigResourceProjection.toRuntimeConfigResourceProjectionSnapshot(): RuntimeConfigResourceProjectionSnapshot {
    return RuntimeConfigResourceProjectionSnapshot(
        configId = configId,
        resourceId = resourceId,
        kind = kind.toRuntimeResourceCenterKind(),
        active = active,
        priority = priority,
        sortIndex = sortIndex,
        configJson = configJson,
    )
}

internal fun MessageType.toRuntimeMessageType(): RuntimeMessageType {
    return when (this) {
        MessageType.FriendMessage -> RuntimeMessageType.FriendMessage
        MessageType.GroupMessage -> RuntimeMessageType.GroupMessage
        MessageType.OtherMessage -> RuntimeMessageType.OtherMessage
    }
}

internal fun RuntimeMessageType.toMessageType(): MessageType {
    return when (this) {
        RuntimeMessageType.FriendMessage -> MessageType.FriendMessage
        RuntimeMessageType.GroupMessage -> MessageType.GroupMessage
        RuntimeMessageType.OtherMessage -> MessageType.OtherMessage
    }
}

internal fun RuntimeStreamingMode.toPluginStreamingMode(): PluginV2StreamingMode {
    return when (this) {
        RuntimeStreamingMode.NON_STREAM -> PluginV2StreamingMode.NON_STREAM
        RuntimeStreamingMode.NATIVE_STREAM -> PluginV2StreamingMode.NATIVE_STREAM
        RuntimeStreamingMode.PSEUDO_STREAM -> PluginV2StreamingMode.PSEUDO_STREAM
    }
}

private fun ResourceCenterKind.toRuntimeResourceCenterKind(): RuntimeResourceCenterKind {
    return when (this) {
        ResourceCenterKind.MCP_SERVER -> RuntimeResourceCenterKind.MCP_SERVER
        ResourceCenterKind.SKILL -> RuntimeResourceCenterKind.SKILL
        ResourceCenterKind.TOOL -> RuntimeResourceCenterKind.TOOL
    }
}

private fun SkillResourceKind.toRuntimeSkillResourceKind(): RuntimeSkillResourceKind {
    return when (this) {
        SkillResourceKind.PROMPT -> RuntimeSkillResourceKind.PROMPT
        SkillResourceKind.TOOL -> RuntimeSkillResourceKind.TOOL
    }
}

private fun String.toProviderType(): ProviderType =
    runCatching { ProviderType.valueOf(this) }.getOrDefault(ProviderType.CUSTOM)

private fun String.toProviderCapabilityOrNull(): ProviderCapability? =
    runCatching { ProviderCapability.valueOf(this) }.getOrNull()

private fun String.toFeatureSupportState(): FeatureSupportState =
    runCatching { FeatureSupportState.valueOf(this) }.getOrDefault(FeatureSupportState.UNKNOWN)
