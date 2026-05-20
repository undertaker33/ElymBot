package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.core.runtime.context.RuntimeConfigResourceProjectionSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConversationAttachment
import com.elymbot.android.core.runtime.context.RuntimeConversationMessage
import com.elymbot.android.core.runtime.context.RuntimeConversationToolCall
import com.elymbot.android.core.runtime.context.RuntimeLegacySkillSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMcpServerSnapshot
import com.elymbot.android.core.runtime.context.RuntimeMessageType
import com.elymbot.android.core.runtime.context.RuntimePersonaToolEnablementSnapshot
import com.elymbot.android.core.runtime.context.RuntimeResourceCenterCompatibilitySnapshot
import com.elymbot.android.core.runtime.context.RuntimeResourceCenterKind
import com.elymbot.android.core.runtime.context.RuntimeResourceItemSnapshot
import com.elymbot.android.core.runtime.context.RuntimeSkillResourceKind
import com.elymbot.android.core.runtime.context.RuntimeStreamingMode
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.McpServerEntry
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.SkillEntry
import com.elymbot.android.model.SkillResourceKind
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationToolCall
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginV2StreamingMode

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

internal fun RuntimeConfigSnapshot.toResourceConfigSnapshot(): ResourceConfigSnapshot {
    return ResourceConfigSnapshot(
        id = id,
        mcpServers = mcpServers.map { it.toMcpServerEntry() },
        skills = skills.map { it.toSkillEntry() },
    )
}

internal fun List<RuntimeConversationMessage>.toConversationMessages(): List<ConversationMessage> =
    map { it.toConversationMessage() }

private fun RuntimeConversationMessage.toConversationMessage(): ConversationMessage {
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

private fun RuntimeConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

private fun RuntimeConversationToolCall.toConversationToolCall(): ConversationToolCall {
    return ConversationToolCall(
        id = id,
        name = name,
        arguments = arguments,
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

private fun McpServerEntry.toRuntimeMcpServerSnapshot(): RuntimeMcpServerSnapshot {
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

private fun SkillEntry.toRuntimeLegacySkillSnapshot(): RuntimeLegacySkillSnapshot {
    return RuntimeLegacySkillSnapshot(
        skillId = skillId,
        name = name,
        description = description,
        content = content,
        priority = priority,
        active = active,
    )
}

private fun RuntimeLegacySkillSnapshot.toSkillEntry(): SkillEntry {
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

internal fun RuntimeMessageType.toMessageType(): MessageType {
    return when (this) {
        RuntimeMessageType.FriendMessage -> MessageType.FriendMessage
        RuntimeMessageType.GroupMessage -> MessageType.GroupMessage
        RuntimeMessageType.OtherMessage -> MessageType.OtherMessage
    }
}

internal fun RuntimePersonaToolEnablementSnapshot.toPersonaToolEnablementSnapshot(): PersonaToolEnablementSnapshot {
    return PersonaToolEnablementSnapshot(
        personaId = personaId,
        enabled = enabled,
        enabledTools = enabledTools,
    )
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
