package com.astrbot.android.core.runtime.context

data class ContextPolicy(
    val strategy: String,
    val maxTurns: Int,
    val dequeueTurns: Int,
    val compressInstruction: String,
    val compressKeepRecent: Int,
    val compressProviderId: String,
)

data class RuntimeBotSnapshot(
    val id: String,
    val displayName: String = "",
    val defaultProviderId: String = "",
    val defaultPersonaId: String = "",
    val configProfileId: String = "default",
)

data class RuntimeMcpServerSnapshot(
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    val transport: String = "streamable_http",
    val command: String = "",
    val args: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 30,
    val active: Boolean = true,
)

data class RuntimeLegacySkillSnapshot(
    val skillId: String = "",
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val priority: Int = 0,
    val active: Boolean = true,
)

data class RuntimeConfigSnapshot(
    val id: String = "",
    val name: String = "",
    val defaultChatProviderId: String = "",
    val defaultVisionProviderId: String = "",
    val defaultSttProviderId: String = "",
    val defaultTtsProviderId: String = "",
    val sttEnabled: Boolean = false,
    val ttsEnabled: Boolean = false,
    val alwaysTtsEnabled: Boolean = false,
    val ttsReadBracketedContent: Boolean = true,
    val textStreamingEnabled: Boolean = false,
    val voiceStreamingEnabled: Boolean = false,
    val streamingMessageIntervalMs: Int = 120,
    val realWorldTimeAwarenessEnabled: Boolean = false,
    val imageCaptionTextEnabled: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val proactiveEnabled: Boolean = false,
    val includeScheduledTaskConversationContext: Boolean = false,
    val ttsVoiceId: String = "",
    val imageCaptionPrompt: String = "Describe the image in detail before sending it to the chat model.",
    val adminUids: List<String> = emptyList(),
    val sessionIsolationEnabled: Boolean = false,
    val wakeWords: List<String> = emptyList(),
    val wakeWordsAdminOnlyEnabled: Boolean = false,
    val privateChatRequiresWakeWord: Boolean = false,
    val replyTextPrefix: String = "",
    val quoteSenderMessageEnabled: Boolean = false,
    val mentionSenderEnabled: Boolean = false,
    val replyOnAtOnlyEnabled: Boolean = true,
    val whitelistEnabled: Boolean = false,
    val whitelistEntries: List<String> = emptyList(),
    val logOnWhitelistMiss: Boolean = false,
    val adminGroupBypassWhitelistEnabled: Boolean = true,
    val adminPrivateBypassWhitelistEnabled: Boolean = true,
    val ignoreSelfMessageEnabled: Boolean = true,
    val ignoreAtAllEventEnabled: Boolean = true,
    val replyWhenPermissionDenied: Boolean = false,
    val rateLimitWindowSeconds: Int = 0,
    val rateLimitMaxCount: Int = 0,
    val rateLimitStrategy: String = "drop",
    val keywordDetectionEnabled: Boolean = false,
    val keywordPatterns: List<String> = emptyList(),
    val contextLimitStrategy: String = "truncate_by_turns",
    val maxContextTurns: Int = -1,
    val dequeueContextTurns: Int = 1,
    val llmCompressInstruction: String = "",
    val llmCompressKeepRecent: Int = 6,
    val llmCompressProviderId: String = "",
    val mcpServers: List<RuntimeMcpServerSnapshot> = emptyList(),
    val skills: List<RuntimeLegacySkillSnapshot> = emptyList(),
)

data class RuntimePersonaSnapshot(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val enabledTools: Set<String>,
    val defaultProviderId: String = "",
    val enabled: Boolean = true,
)

data class RuntimePersonaToolEnablementSnapshot(
    val personaId: String,
    val enabled: Boolean,
    val enabledTools: Set<String>,
)

data class RuntimeProviderSnapshot(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: String,
    val apiKey: String,
    val capabilities: Set<String>,
    val enabled: Boolean = true,
    val multimodalRuleSupport: String = "UNKNOWN",
    val multimodalProbeSupport: String = "UNKNOWN",
    val nativeStreamingRuleSupport: String = "UNKNOWN",
    val nativeStreamingProbeSupport: String = "UNKNOWN",
    val sttProbeSupport: String = "UNKNOWN",
    val ttsProbeSupport: String = "UNKNOWN",
    val ttsVoiceOptions: List<String> = emptyList(),
    val supportsToolCalling: Boolean = false,
    val supportsStreaming: Boolean = false,
    val supportsMultimodal: Boolean = false,
) {
    val chatCapable: Boolean get() = "CHAT" in capabilities
}

data class RuntimeConversationToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class RuntimeConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachments: List<RuntimeConversationAttachment> = emptyList(),
    val toolCallId: String = "",
    val assistantToolCalls: List<RuntimeConversationToolCall> = emptyList(),
)

data class RuntimeConversationSessionSnapshot(
    val id: String,
    val title: String = "",
    val botId: String = "",
    val personaId: String = "",
    val providerId: String = "",
    val maxContextMessages: Int = 0,
    val messages: List<RuntimeConversationMessage> = emptyList(),
)

data class ProviderCapabilitySnapshot(
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
    val supportsMultimodal: Boolean,
)

data class DeliveryPolicy(
    val platform: RuntimePlatform,
    val streamingEnabled: Boolean,
    val quoteSenderMessage: Boolean,
    val mentionSender: Boolean,
    val replyTextPrefix: String,
    val ttsEnabled: Boolean,
    val alwaysTts: Boolean,
)

data class ResolvedRuntimeContext(
    val requestId: String,
    val ingressEvent: RuntimeIngressEvent,
    val bot: RuntimeBotSnapshot,
    val config: RuntimeConfigSnapshot,
    val persona: RuntimePersonaSnapshot?,
    val provider: RuntimeProviderSnapshot,
    val availableProviders: List<RuntimeProviderSnapshot>,
    val conversationId: String,
    val messageWindow: List<RuntimeConversationMessage>,
    val scheduledTaskContextWindow: List<RuntimeConversationMessage> = emptyList(),
    val contextPolicy: ContextPolicy,
    val personaToolSnapshot: RuntimePersonaToolEnablementSnapshot?,
    val providerCapabilities: ProviderCapabilitySnapshot,
    val webSearchEnabled: Boolean,
    val proactiveEnabled: Boolean,
    val mcpServers: List<RuntimeMcpServerSnapshot>,
    val skills: List<RuntimeLegacySkillSnapshot>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
    val toolSourceContext: ToolSourceContext,
    val deliveryPolicy: DeliveryPolicy,
    val realWorldTimeAwarenessEnabled: Boolean,
)
