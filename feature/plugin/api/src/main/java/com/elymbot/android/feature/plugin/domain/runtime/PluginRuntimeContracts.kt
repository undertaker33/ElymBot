package com.elymbot.android.feature.plugin.domain.runtime

import com.elymbot.android.core.runtime.context.ResolvedRuntimeContext
import com.elymbot.android.core.runtime.context.ToolSourceContext
import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.AppChatLlm
import com.elymbot.android.model.plugin.HostActionRequest
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginExecutionResult
import com.elymbot.android.model.plugin.PluginFailureCategory
import com.elymbot.android.model.plugin.PluginHostAction
import com.elymbot.android.model.plugin.PluginInstallState
import com.elymbot.android.model.plugin.PluginTriggerSource
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import java.util.LinkedHashMap

typealias AllowedValue = Any?
typealias JsonLikeMap = Map<String, AllowedValue>

fun interface PluginRuntimeHandler {
    fun execute(context: PluginExecutionContext): PluginExecutionResult
}

data class PluginRuntimePlugin(
    val pluginId: String,
    val pluginVersion: String,
    val installState: PluginInstallState,
    val supportedTriggers: Set<PluginTriggerSource> = PluginTriggerSource.entries.toSet(),
    val handler: PluginRuntimeHandler,
) {
    init {
        installState.manifestSnapshot?.let { manifest ->
            require(manifest.pluginId == pluginId) {
                "PluginRuntimePlugin pluginId must match the install state manifest."
            }
            require(manifest.version == pluginVersion) {
                "PluginRuntimePlugin pluginVersion must match the install state manifest."
            }
        }
    }
}

enum class PluginDispatchSkipReason {
    NotInstalled,
    Disabled,
    Incompatible,
    FailureSuspended,
    UnsupportedTrigger,
    SchedulerCoolingDown,
    SchedulerRetryBackoff,
    SchedulerSilenced,
}

data class PluginDispatchSkip(
    val plugin: PluginRuntimePlugin,
    val reason: PluginDispatchSkipReason,
)

data class PluginFailureSnapshot(
    val pluginId: String,
    val trigger: PluginTriggerSource? = null,
    val consecutiveFailureCount: Int = 0,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
    val failureCategory: PluginFailureCategory = PluginFailureCategory.Unknown,
    val isSuspended: Boolean = false,
    val suspendedUntilEpochMillis: Long? = null,
)

data class PluginExecutionOutcome(
    val pluginId: String,
    val pluginVersion: String,
    val installState: PluginInstallState,
    val context: PluginExecutionContext,
    val result: PluginExecutionResult,
    val succeeded: Boolean,
    val failureSnapshot: PluginFailureSnapshot,
    val error: Throwable? = null,
)

data class PluginExecutionMergeConflict(
    val pluginId: String,
    val overriddenByPluginId: String,
    val resultType: String,
    val reason: String,
)

data class PluginExecutionMergeSnapshot(
    val orderedPluginIds: List<String> = emptyList(),
    val resultTypeCounts: Map<String, Int> = emptyMap(),
    val primaryInteractivePluginId: String = "",
    val primaryInteractiveResultType: String = "",
    val conflicts: List<PluginExecutionMergeConflict> = emptyList(),
)

data class PluginExecutionBatchResult(
    val trigger: PluginTriggerSource,
    val outcomes: List<PluginExecutionOutcome>,
    val skipped: List<PluginDispatchSkip>,
    val merged: PluginExecutionMergeSnapshot = PluginExecutionMergeSnapshot(),
)

interface AppChatPluginRuntime {
    fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult
}

interface AppChatLlmPipelineRuntime {
    suspend fun runLlmPipeline(input: PluginV2LlmPipelineInput): PluginV2LlmPipelineResult

    suspend fun deliverLlmPipeline(request: PluginV2HostLlmDeliveryRequest): PluginV2HostLlmDeliveryResult

    suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult
}

enum class PluginProviderMessageRole(
    val wireValue: String,
    val canBeWrittenByPlugin: Boolean,
) {
    SYSTEM("system", true),
    USER("user", true),
    ASSISTANT("assistant", true),
    TOOL("tool", false);

    companion object {
        fun fromWireValue(value: String): PluginProviderMessageRole? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

sealed interface PluginProviderMessagePartDto {
    data class TextPart(
        val text: String,
    ) : PluginProviderMessagePartDto

    data class MediaRefPart(
        val uri: String,
        val mimeType: String,
    ) : PluginProviderMessagePartDto {
        init {
            require(uri.isNotBlank()) { "uri must not be blank." }
            require(mimeType.isNotBlank()) { "mimeType must not be blank." }
        }
    }
}

typealias PluginProviderMessageTextPart = PluginProviderMessagePartDto.TextPart
typealias PluginProviderMessageMediaRefPart = PluginProviderMessagePartDto.MediaRefPart

data class PluginProviderAssistantToolCall(
    val id: String,
    val toolName: String,
    val arguments: JsonLikeMap = emptyMap(),
) {
    val normalizedId: String = id.trim()
    val normalizedToolName: String = toolName.trim()
    val normalizedArguments: JsonLikeMap = PluginRuntimeValueSanitizer.requireAllowedMap(arguments)

    init {
        require(normalizedId.isNotBlank()) { "id must not be blank." }
        require(normalizedToolName.isNotBlank()) { "toolName must not be blank." }
    }
}

class PluginProviderMessageDto(
    val role: PluginProviderMessageRole,
    parts: List<PluginProviderMessagePartDto>,
    name: String? = null,
    metadata: JsonLikeMap? = null,
    toolCalls: List<PluginProviderAssistantToolCall> = emptyList(),
) {
    val parts: List<PluginProviderMessagePartDto> = parts.toList()
    val name: String? = name?.trim()?.takeIf { it.isNotBlank() }
    val metadata: JsonLikeMap? = metadata?.let(PluginRuntimeValueSanitizer::requireAllowedMap)
    val toolCalls: List<PluginProviderAssistantToolCall> = toolCalls.toList()

    init {
        require(this.parts.isNotEmpty() || (role == PluginProviderMessageRole.ASSISTANT && this.toolCalls.isNotEmpty())) {
            "parts must not be empty unless role=ASSISTANT with toolCalls."
        }
        require(
            role == PluginProviderMessageRole.TOOL ||
                ((role == PluginProviderMessageRole.ASSISTANT && this.toolCalls.isNotEmpty()) ||
                    this.parts.none { it is PluginProviderMessagePartDto.TextPart && it.text.isBlank() }),
        ) {
            "parts must not contain blank text values unless role=TOOL or assistant toolCalls are present."
        }
        require(this.parts.none { it is PluginProviderMessagePartDto.MediaRefPart && it.uri.isBlank() }) {
            "parts must not contain blank media references."
        }
        require(this.parts.none { it is PluginProviderMessagePartDto.MediaRefPart && it.mimeType.isBlank() }) {
            "parts must not contain blank media mime types."
        }
        require(role == PluginProviderMessageRole.ASSISTANT || this.toolCalls.isEmpty()) {
            "toolCalls are only allowed for role=ASSISTANT."
        }
        if (role == PluginProviderMessageRole.TOOL) {
            require(this.name != null) { "role=TOOL requires name." }
            require(extractHostToolCallId(this.metadata) != null) {
                "role=TOOL requires metadata.__host.toolCallId."
            }
        }
    }
}

data class PluginProviderToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonLikeMap,
)

class PluginProviderRequest(
    val requestId: String,
    availableProviderIds: List<String>,
    availableModelIdsByProvider: Map<String, List<String>>,
    val conversationId: String,
    messageIds: List<String>,
    val llmInputSnapshot: String,
    selectedProviderId: String = "",
    selectedModelId: String = "",
    systemPrompt: String? = null,
    messages: List<PluginProviderMessageDto> = emptyList(),
    temperature: Double? = null,
    topP: Double? = null,
    maxTokens: Int? = null,
    streamingEnabled: Boolean = false,
    metadata: JsonLikeMap? = null,
    private val allowHostToolMessages: Boolean = false,
    tools: List<PluginProviderToolDefinition> = emptyList(),
) {
    val availableProviderIds: List<String> = sanitizeIdList(availableProviderIds, "availableProviderIds")
    val availableModelIdsByProvider: Map<String, List<String>> = sanitizeModelMap(
        availableModelIdsByProvider,
        "availableModelIdsByProvider",
    )
    val messageIds: List<String> = sanitizeIdList(messageIds, "messageIds")
    val tools: List<PluginProviderToolDefinition> = tools.toList()

    var selectedProviderId: String = normalizeSelectedProviderId(selectedProviderId)
        set(value) {
            field = normalizeSelectedProviderId(value)
            selectedModelId = coerceSelectedModelIdForProvider(field, selectedModelId)
        }

    var selectedModelId: String = normalizeSelectedModelId(selectedModelId)
        set(value) {
            field = normalizeSelectedModelId(value)
        }

    var systemPrompt: String? = sanitizeOptionalString(systemPrompt)
        set(value) {
            field = sanitizeOptionalString(value)
        }

    private val preservedHostToolMessages: List<PluginProviderMessageDto> =
        sanitizeInitialHostToolMessages(messages, allowHostToolMessages)

    var messages: List<PluginProviderMessageDto> = sanitizeInitialMessages(
        values = messages,
        preservedHostToolMessages = preservedHostToolMessages,
    )
        set(value) {
            field = sanitizeMessagesPreservingHostToolOrder(
                values = value,
                preservedHostToolMessages = preservedHostToolMessages,
            )
        }

    var temperature: Double? = sanitizeTemperature(temperature)
        set(value) {
            field = sanitizeTemperature(value)
        }

    var topP: Double? = sanitizeTopP(topP)
        set(value) {
            field = sanitizeTopP(value)
        }

    var maxTokens: Int? = sanitizeMaxTokens(maxTokens)
        set(value) {
            field = sanitizeMaxTokens(value)
        }

    var streamingEnabled: Boolean = streamingEnabled
    var metadata: JsonLikeMap? = sanitizeMetadata(metadata)
        set(value) {
            field = sanitizeMetadata(value)
        }

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
        require(llmInputSnapshot.isNotBlank()) { "llmInputSnapshot must not be blank." }
        if (availableProviderIds.isNotEmpty()) {
            require(selectedProviderId in availableProviderIds) {
                "selectedProviderId must be one of availableProviderIds."
            }
        } else {
            require(selectedProviderId.isBlank()) {
                "selectedProviderId must be blank when no providers are available."
            }
        }
        if (selectedModelId.isNotBlank()) {
            require(selectedModelId in availableModelIdsByProvider[selectedProviderId].orEmpty()) {
                "selectedModelId must be one of availableModelIdsByProvider[selectedProviderId]."
            }
        }
    }

    private fun normalizeSelectedProviderId(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) return availableProviderIds.firstOrNull().orEmpty()
        require(normalized in availableProviderIds) {
            "selectedProviderId must be one of availableProviderIds."
        }
        return normalized
    }

    private fun normalizeSelectedModelId(value: String): String {
        val normalized = value.trim()
        val allowedModels = availableModelIdsByProvider[selectedProviderId].orEmpty()
        if (normalized.isBlank()) return allowedModels.firstOrNull().orEmpty()
        require(normalized in allowedModels) {
            "selectedModelId must be one of availableModelIdsByProvider[selectedProviderId]."
        }
        return normalized
    }

    private fun coerceSelectedModelIdForProvider(providerId: String, currentModelId: String): String {
        val allowedModels = availableModelIdsByProvider[providerId].orEmpty()
        return when {
            currentModelId.isNotBlank() && currentModelId in allowedModels -> currentModelId
            allowedModels.isNotEmpty() -> allowedModels.first()
            else -> ""
        }
    }
}

data class PluginLlmUsageSnapshot(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val inputCostMicros: Long? = null,
    val outputCostMicros: Long? = null,
    val currencyCode: String? = null,
) {
    init {
        require(promptTokens == null || promptTokens >= 0) { "promptTokens must not be negative." }
        require(completionTokens == null || completionTokens >= 0) { "completionTokens must not be negative." }
        require(totalTokens == null || totalTokens >= 0) { "totalTokens must not be negative." }
        require(inputCostMicros == null || inputCostMicros >= 0L) { "inputCostMicros must not be negative." }
        require(outputCostMicros == null || outputCostMicros >= 0L) { "outputCostMicros must not be negative." }
    }

    val normalizedCurrencyCode: String?
        get() = currencyCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
}

class PluginLlmResponse(
    val requestId: String,
    val providerId: String,
    val modelId: String,
    val usage: PluginLlmUsageSnapshot? = null,
    val finishReason: String? = null,
    text: String = "",
    markdown: Boolean = false,
    toolCalls: List<PluginLlmToolCall> = emptyList(),
    metadata: JsonLikeMap? = null,
) {
    var text: String = text
    var markdown: Boolean = markdown
    var metadata: JsonLikeMap? = sanitizeMetadata(metadata)
        set(value) {
            field = sanitizeMetadata(value)
        }
    var toolCalls: List<PluginLlmToolCall> = sanitizeToolCalls(toolCalls)
        set(value) {
            field = sanitizeToolCalls(value)
        }

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(providerId.isNotBlank()) { "providerId must not be blank." }
        require(modelId.isNotBlank()) { "modelId must not be blank." }
    }
}

data class PluginLlmToolCall(
    val toolCallId: String? = null,
    val toolName: String,
    val arguments: JsonLikeMap = emptyMap(),
    val metadata: JsonLikeMap? = null,
) {
    val normalizedToolCallId: String? = toolCallId?.trim()?.takeIf { it.isNotBlank() }
    val normalizedToolName: String = toolName.trim()
    val normalizedArguments: JsonLikeMap = PluginRuntimeValueSanitizer.requireAllowedMap(arguments)
    val normalizedMetadata: JsonLikeMap? = metadata?.let(PluginRuntimeValueSanitizer::requireAllowedMap)

    init {
        require(normalizedToolName.isNotBlank()) { "toolName must not be blank." }
    }
}

data class PluginLlmToolCallDelta(
    val index: Int,
    val toolCallId: String? = null,
    val toolName: String,
    val arguments: JsonLikeMap = emptyMap(),
) {
    init {
        require(index >= 0) { "index must not be negative." }
        require(toolName.isNotBlank()) { "toolName must not be blank." }
    }
}

sealed interface PluginV2ProviderInvocationResult {
    data class NonStreaming(
        val response: PluginLlmResponse,
    ) : PluginV2ProviderInvocationResult

    data class Streaming(
        val events: List<PluginV2ProviderStreamChunk>,
    ) : PluginV2ProviderInvocationResult
}

data class PluginV2ProviderStreamChunk(
    val deltaText: String = "",
    val toolCallDeltas: List<PluginLlmToolCallDelta> = emptyList(),
    val isCompletion: Boolean = false,
    val finishReason: String? = null,
    val usage: PluginLlmUsageSnapshot? = null,
    val metadata: JsonLikeMap? = null,
)

class PluginMessageEventResult(
    val requestId: String,
    val conversationId: String,
    text: String = "",
    markdown: Boolean = false,
    attachments: List<Attachment> = emptyList(),
    shouldSend: Boolean = true,
    attachmentMutationIntent: AttachmentMutationIntent? = null,
) {
    enum class AttachmentMutationIntent {
        UNTOUCHED,
        REPLACED,
        REPLACED_EMPTY,
        APPENDED,
        CLEARED,
    }

    data class Attachment(
        val uri: String,
        val mimeType: String = "",
    ) {
        init {
            require(uri.isNotBlank()) { "uri must not be blank." }
        }
    }

    private var textState: String = text
    private var markdownState: Boolean = markdown
    private var attachmentsState: List<Attachment> = attachments.toList()
    private var attachmentMutationIntentState: AttachmentMutationIntent =
        attachmentMutationIntent ?: if (attachmentsState.isEmpty()) {
            AttachmentMutationIntent.UNTOUCHED
        } else {
            AttachmentMutationIntent.REPLACED
        }

    var shouldSend: Boolean = shouldSend
        private set

    private var stopped: Boolean = false

    val text: String
        get() = textState

    var markdown: Boolean
        get() = markdownState
        set(value) {
            markdownState = value
        }

    val attachments: List<Attachment>
        get() = attachmentsState.toList()

    val attachmentMutationIntent: AttachmentMutationIntent
        get() = attachmentMutationIntentState

    val isStopped: Boolean
        get() = stopped

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
    }

    fun replaceText(value: String) {
        textState = value
    }

    fun appendText(value: String) {
        textState += value
    }

    fun clearText() {
        textState = ""
    }

    fun replaceAttachments(value: List<Attachment>) {
        attachmentsState = value.toList()
        attachmentMutationIntentState = if (attachmentsState.isEmpty()) {
            AttachmentMutationIntent.REPLACED_EMPTY
        } else {
            AttachmentMutationIntent.REPLACED
        }
    }

    fun appendAttachment(value: Attachment) {
        attachmentsState = attachmentsState + value
        attachmentMutationIntentState = AttachmentMutationIntent.APPENDED
    }

    fun clearAttachments() {
        attachmentsState = emptyList()
        attachmentMutationIntentState = AttachmentMutationIntent.CLEARED
    }

    fun setShouldSend(value: Boolean) {
        shouldSend = value
    }

    fun stop() {
        stopped = true
    }
}

typealias PluginMessageEventResultAttachment = PluginMessageEventResult.Attachment
typealias PluginMessageEventAttachmentMutationIntent = PluginMessageEventResult.AttachmentMutationIntent

enum class PluginMessageStage {
    AdapterMessage,
    Command,
    Regex,
}

interface PluginErrorEventPayload

data class PluginRawPayloadRef(
    val refId: String,
)

class MessagePropagationState {
    var stopped: Boolean = false
        private set

    fun stop() {
        stopped = true
    }
}

class PluginMessageEvent(
    val eventId: String,
    val platformAdapterType: String,
    val messageType: MessageType,
    val conversationId: String,
    val senderId: String,
    val timestampEpochMillis: Long,
    val rawText: String,
    rawMentions: List<String> = emptyList(),
    val rawPayloadRef: PluginRawPayloadRef? = null,
    initialWorkingText: String = rawText,
    normalizedMentions: List<String> = emptyList(),
    extras: Map<String, AllowedValue> = emptyMap(),
) : PluginErrorEventPayload {
    val stage: PluginMessageStage = PluginMessageStage.AdapterMessage
    val propagationStopped: MessagePropagationState = MessagePropagationState()
    val rawMentions: List<String> = rawMentions.toList()
    var workingText: String = initialWorkingText
    var normalizedMentions: List<String> = normalizedMentions.toList()
        set(value) {
            field = value.toList()
        }
    var extras: Map<String, AllowedValue> = PluginRuntimeValueSanitizer.requireAllowedMap(extras)
        set(value) {
            field = PluginRuntimeValueSanitizer.requireAllowedMap(value)
        }
    val isPropagationStopped: Boolean
        get() = propagationStopped.stopped

    fun stopPropagation() {
        propagationStopped.stop()
    }
}

data class PluginV2CommandResponseAttachment(
    val source: String,
    val mimeType: String = "",
    val label: String = "",
)

data class PluginV2CommandResponse(
    val pluginId: String,
    val extractedDir: String,
    val text: String = "",
    val attachments: List<PluginV2CommandResponseAttachment> = emptyList(),
)

data class PluginV2MessageDispatchResult(
    val propagationStopped: Boolean = false,
    val terminatedByCustomFilterFailure: Boolean = false,
    val userVisibleFailureMessage: String? = null,
    val commandResponse: PluginV2CommandResponse? = null,
)

enum class PluginV2InternalStage {
    AdapterMessage,
    Command,
    Regex,
    Lifecycle,
    LlmWaiting,
    LlmRequest,
    LlmResponse,
    UsingLlmTool,
    ToolExecution,
    LlmToolRespond,
    ResultDecorating,
    AfterMessageSent,
}

enum class PluginV2DispatchObservationKind {
    Skip,
    Missing,
    Inactive,
}

data class PluginV2DispatchObservation(
    val pluginId: String,
    val stage: PluginV2InternalStage,
    val kind: PluginV2DispatchObservationKind,
    val reason: String,
    val handlerId: String? = null,
)

data class PluginV2LlmStageDispatchResult(
    val stage: PluginV2InternalStage,
    val invokedHandlerIds: List<String>,
    val observations: List<PluginV2DispatchObservation> = emptyList(),
)

enum class PluginToolVisibility {
    LLM_VISIBLE,
    HOST_INTERNAL,
}

enum class PluginToolSourceKind(
    val reservedOnly: Boolean,
) {
    PLUGIN_V2(reservedOnly = false),
    HOST_BUILTIN(reservedOnly = false),
    MCP(reservedOnly = true),
    SKILL(reservedOnly = true),
    ACTIVE_CAPABILITY(reservedOnly = true),
    CONTEXT_STRATEGY(reservedOnly = true),
    WEB_SEARCH(reservedOnly = true),
}

enum class PluginToolResultStatus {
    SUCCESS,
    ERROR,
}

class PluginToolDescriptor(
    pluginId: String,
    name: String,
    description: String = "",
    val visibility: PluginToolVisibility = PluginToolVisibility.LLM_VISIBLE,
    val sourceKind: PluginToolSourceKind = PluginToolSourceKind.PLUGIN_V2,
    inputSchema: JsonLikeMap,
    metadata: JsonLikeMap? = null,
) {
    val pluginId: String = pluginId.trim()
    val name: String = name.trim()
    val description: String = description.trim()
    val toolId: String = "${this.pluginId}:${this.name}"
    val inputSchema: JsonLikeMap = PluginRuntimeValueSanitizer.requireAllowedMap(inputSchema)
    val metadata: JsonLikeMap? = metadata?.let(PluginRuntimeValueSanitizer::requireAllowedMap)
}

class PluginToolArgs(
    val toolCallId: String,
    val requestId: String,
    val toolId: String,
    val attemptIndex: Int = 0,
    payload: JsonLikeMap,
    metadata: JsonLikeMap? = null,
) {
    val payload: JsonLikeMap = PluginRuntimeValueSanitizer.requireAllowedMap(payload)
    val metadata: JsonLikeMap? = metadata?.let(PluginRuntimeValueSanitizer::requireAllowedMap)

    init {
        require(toolCallId.isNotBlank()) { "toolCallId must not be blank." }
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(toolId.isNotBlank()) { "toolId must not be blank." }
        require(attemptIndex >= 0) { "attemptIndex must not be negative." }
    }
}

class PluginToolResult(
    val toolCallId: String,
    val requestId: String,
    val toolId: String,
    val status: PluginToolResultStatus,
    errorCode: String? = null,
    text: String? = null,
    structuredContent: JsonLikeMap? = null,
    metadata: JsonLikeMap? = null,
) {
    val errorCode: String? = errorCode?.trim()?.takeIf { it.isNotBlank() }
    val text: String? = text?.trim()
    val structuredContent: JsonLikeMap? = structuredContent?.let(PluginRuntimeValueSanitizer::requireAllowedMap)
    val metadata: JsonLikeMap? = metadata?.let(PluginRuntimeValueSanitizer::requireAllowedMap)

    init {
        require(toolCallId.isNotBlank()) { "toolCallId must not be blank." }
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(toolId.isNotBlank()) { "toolId must not be blank." }
        require(status != PluginToolResultStatus.ERROR || this.errorCode != null) {
            "errorCode must not be blank when status=ERROR."
        }
    }
}

data class LlmPipelineAdmission(
    val requestId: String,
    val conversationId: String,
    val messageIds: List<String>,
    val llmInputSnapshot: String,
    val routingTarget: AppChatLlm,
    val streamingMode: PluginV2StreamingMode,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
        require(messageIds.isNotEmpty()) { "messageIds must not be empty." }
        require(messageIds.none(String::isBlank)) { "messageIds must not contain blank values." }
        require(llmInputSnapshot.isNotBlank()) { "llmInputSnapshot must not be blank." }
    }
}

data class PluginV2LlmPipelineInput(
    val event: PluginMessageEvent,
    val messageIds: List<String>,
    val routingTarget: AppChatLlm = AppChatLlm.AppChat,
    val streamingMode: PluginV2StreamingMode,
    val availableProviderIds: List<String>,
    val availableModelIdsByProvider: Map<String, List<String>>,
    val selectedProviderId: String = "",
    val selectedModelId: String = "",
    val systemPrompt: String? = null,
    val messages: List<PluginProviderMessageDto> = emptyList(),
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val streamingEnabled: Boolean = streamingMode == PluginV2StreamingMode.NATIVE_STREAM,
    val metadata: JsonLikeMap? = null,
    val personaToolEnablementSnapshot: PersonaToolEnablementSnapshot? = null,
    val configProfileId: String? = null,
    val toolSourceContext: ToolSourceContext? = null,
    val supportsToolCalling: Boolean = true,
    val invokeProvider: suspend (
        request: PluginProviderRequest,
        streamingMode: PluginV2StreamingMode,
    ) -> PluginV2ProviderInvocationResult,
)

interface PluginV2DecoratingRunSnapshot {
    val finalResult: PluginMessageEventResult
    val appliedHandlerIds: List<String>
    val stoppedByHandlerId: String?
    val mutationTrace: List<*>
}

data class PluginV2LlmPipelineResult(
    val admission: LlmPipelineAdmission,
    val finalRequest: PluginProviderRequest,
    val finalResponse: PluginLlmResponse,
    val sendableResult: PluginMessageEventResult,
    val hookInvocationTrace: List<String>,
    val decoratingRunResult: PluginV2DecoratingRunSnapshot,
    val executedToolNames: List<String> = emptyList(),
)

class PluginV2AfterSentView(
    val requestId: String,
    val conversationId: String,
    val sendAttemptId: String,
    val platformAdapterType: String,
    val platformInstanceKey: String,
    val sentAtEpochMs: Long,
    val deliveryStatus: DeliveryStatus,
    receiptIds: List<String> = emptyList(),
    deliveredEntries: List<DeliveredEntry> = emptyList(),
    val usage: PluginLlmUsageSnapshot? = null,
    val deliveredEntryCount: Int = deliveredEntries.size,
) {
    enum class DeliveryStatus(
        val wireValue: String,
    ) {
        SUCCESS("success"),
        FAILED("failed"),
        SKIPPED("skipped");

        companion object {
            fun fromWireValue(value: String): DeliveryStatus? {
                return entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class DeliveredEntry(
        val entryId: String,
        val entryType: String,
        val textPreview: String,
        val attachmentCount: Int,
    )

    val receiptIds: List<String> = receiptIds.toList()
    val deliveredEntries: List<DeliveredEntry> = deliveredEntries.toList()

    init {
        require(deliveredEntryCount == this.deliveredEntries.size) {
            "deliveredEntryCount must match deliveredEntries size."
        }
    }
}

typealias PluginV2AfterSentDeliveredEntry = PluginV2AfterSentView.DeliveredEntry

data class PluginV2HostPreparedReply(
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val deliveredEntries: List<PluginV2AfterSentView.DeliveredEntry> = emptyList(),
    val deliveryTags: Set<String> = emptySet(),
)

fun interface PluginV2FollowupSender {
    fun send(text: String, attachments: List<ConversationAttachment>): PluginV2HostSendResult
}

data class PluginV2ToolResultDeliveryRequest(
    val event: PluginMessageEvent,
    val descriptor: PluginToolDescriptor,
    val args: PluginToolArgs,
    val result: PluginToolResult,
)

fun interface PluginV2ToolResultDeliveryHandler {
    suspend fun handle(request: PluginV2ToolResultDeliveryRequest): PluginToolResult
}

data class PluginV2HostSendResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorSummary: String = "",
)

data class PluginV2HostLlmDeliveryRequest(
    val pipelineInput: PluginV2LlmPipelineInput,
    val conversationId: String,
    val platformAdapterType: String,
    val platformInstanceKey: String,
    val hostCapabilityGateway: PluginHostCapabilityGateway,
    val followupSender: PluginV2FollowupSender? = null,
    val toolResultDeliveryHandler: PluginV2ToolResultDeliveryHandler? = null,
    val prepareReply: suspend (PluginV2LlmPipelineResult) -> PluginV2HostPreparedReply,
    val sendReply: suspend (PluginV2HostPreparedReply) -> PluginV2HostSendResult,
    val persistDeliveredReply: suspend (
        PluginV2HostPreparedReply,
        PluginV2HostSendResult,
        PluginV2LlmPipelineResult,
    ) -> Unit,
)

sealed interface PluginV2HostLlmDeliveryResult {
    val pipelineResult: PluginV2LlmPipelineResult

    data class Suppressed(
        override val pipelineResult: PluginV2LlmPipelineResult,
    ) : PluginV2HostLlmDeliveryResult

    data class SendFailed(
        override val pipelineResult: PluginV2LlmPipelineResult,
        val sendResult: PluginV2HostSendResult,
    ) : PluginV2HostLlmDeliveryResult

    data class Sent(
        override val pipelineResult: PluginV2LlmPipelineResult,
        val preparedReply: PluginV2HostPreparedReply,
        val sendResult: PluginV2HostSendResult,
        val afterSentView: PluginV2AfterSentView,
    ) : PluginV2HostLlmDeliveryResult
}

typealias PluginV2HostLlmDeliverySuppressed = PluginV2HostLlmDeliveryResult.Suppressed
typealias PluginV2HostLlmDeliverySendFailed = PluginV2HostLlmDeliveryResult.SendFailed
typealias PluginV2HostLlmDeliverySent = PluginV2HostLlmDeliveryResult.Sent

data class ExternalPluginHostActionHandlers(
    val sendMessage: (String) -> Unit = {},
    val sendNotification: (String, String) -> Unit = { _, _ -> },
    val openHostPage: (String) -> Unit = {},
)

data class ExternalPluginHostActionExecutionResult(
    val action: PluginHostAction,
    val succeeded: Boolean,
    val code: String = "",
    val message: String = "",
    val failureSnapshot: PluginFailureSnapshot,
)

interface PluginHostCapabilityGateway {
    fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult

    fun injectContext(context: PluginExecutionContext): PluginExecutionContext
}

interface PluginHostCapabilityGatewayFactory {
    fun create(
        sendMessageHandler: (String) -> Unit = {},
        sendNotificationHandler: (String, String) -> Unit = { _, _ -> },
        openHostPageHandler: (String) -> Unit = {},
    ): PluginHostCapabilityGateway
}

fun interface PluginV2MessageDispatchPort {
    suspend fun dispatchMessage(event: PluginMessageEvent): PluginV2MessageDispatchResult
}

interface PlatformLlmCallbacks {
    val platformInstanceKey: String
    val hostCapabilityGateway: PluginHostCapabilityGateway
    val followupSender: PluginV2FollowupSender?

    suspend fun prepareReply(result: PluginV2LlmPipelineResult): PluginV2HostPreparedReply

    suspend fun sendReply(prepared: PluginV2HostPreparedReply): PluginV2HostSendResult

    suspend fun persistDeliveredReply(
        prepared: PluginV2HostPreparedReply,
        sendResult: PluginV2HostSendResult,
        pipelineResult: PluginV2LlmPipelineResult,
    )

    suspend fun handleToolResult(request: PluginV2ToolResultDeliveryRequest): PluginToolResult = request.result

    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult
}

interface RuntimeLlmOrchestratorPort {
    suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent? = null,
    ): PluginV2HostLlmDeliveryResult
}

private object PluginRuntimeValueSanitizer {
    fun requireAllowed(value: AllowedValue, path: String = "value"): AllowedValue {
        return sanitizeValue(value, path)
    }

    fun requireAllowedMap(
        values: Map<String, AllowedValue>,
        path: String = "extras",
    ): Map<String, AllowedValue> {
        val sanitized = LinkedHashMap<String, AllowedValue>(values.size)
        values.forEach { (key, value) ->
            sanitized[key] = sanitizeValue(value, "$path['$key']")
        }
        return sanitized
    }

    private fun sanitizeValue(value: AllowedValue, path: String): AllowedValue {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is List<*> -> value.mapIndexed { index, item ->
                sanitizeValue(item, "$path[$index]")
            }

            is Map<*, *> -> {
                val sanitized = LinkedHashMap<String, AllowedValue>(value.size)
                value.forEach { (key, item) ->
                    require(key is String) {
                        "$path contains a non-string key: ${key?.javaClass?.name ?: "null"}"
                    }
                    sanitized[key] = sanitizeValue(item, "$path['$key']")
                }
                sanitized
            }

            else -> throw IllegalArgumentException(
                "$path contains unsupported value type: ${value::class.java.name}",
            )
        }
    }
}

private fun sanitizeIdList(values: List<String>, path: String): List<String> {
    return values.mapIndexed { index, value ->
        val normalized = value.trim()
        require(normalized.isNotBlank()) {
            "$path[$index] must not be blank."
        }
        normalized
    }
}

private fun sanitizeModelMap(values: Map<String, List<String>>, path: String): Map<String, List<String>> {
    val sanitized = linkedMapOf<String, List<String>>()
    values.forEach { (providerId, modelIds) ->
        val normalizedProviderId = providerId.trim()
        require(normalizedProviderId.isNotBlank()) {
            "$path contains a blank provider id."
        }
        sanitized[normalizedProviderId] = sanitizeIdList(modelIds, "$path['$normalizedProviderId']")
    }
    return sanitized
}

private fun sanitizeOptionalString(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotBlank() }
}

private fun sanitizeMessages(values: List<PluginProviderMessageDto>): List<PluginProviderMessageDto> {
    return values.toList()
}

private fun sanitizeInitialMessages(
    values: List<PluginProviderMessageDto>,
    preservedHostToolMessages: List<PluginProviderMessageDto>,
): List<PluginProviderMessageDto> {
    return sanitizeMessagesPreservingHostToolOrder(
        values = values,
        preservedHostToolMessages = preservedHostToolMessages,
    )
}

private fun sanitizeInitialHostToolMessages(
    values: List<PluginProviderMessageDto>,
    allowHostToolMessages: Boolean,
): List<PluginProviderMessageDto> {
    val sanitized = sanitizeMessages(values)
    val toolMessages = sanitized.filter { it.role == PluginProviderMessageRole.TOOL }
    if (!allowHostToolMessages) return emptyList()
    toolMessages.forEach { message ->
        require(extractHostToolCallId(message.metadata) != null) {
            "host-preserved role=TOOL messages require metadata.__host.toolCallId."
        }
    }
    return toolMessages
}

private fun extractHostToolCallId(metadata: JsonLikeMap?): String? {
    val hostMetadata = metadata?.get("__host") as? Map<*, *> ?: return null
    return (hostMetadata["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
}

private fun sanitizeMessagesPreservingHostToolOrder(
    values: List<PluginProviderMessageDto>,
    preservedHostToolMessages: List<PluginProviderMessageDto>,
): List<PluginProviderMessageDto> {
    val sanitized = sanitizeMessages(values)
    if (preservedHostToolMessages.isEmpty()) return sanitized.filter { it.role != PluginProviderMessageRole.TOOL }

    val preservedByToolCallId = preservedHostToolMessages.associateBy { message ->
        requireNotNull(extractHostToolCallId(message.metadata)) {
            "host-preserved role=TOOL messages require metadata.__host.toolCallId."
        }
    }
    val merged = mutableListOf<PluginProviderMessageDto>()
    val seenToolCallIds = linkedSetOf<String>()

    sanitized.forEach { message ->
        if (message.role != PluginProviderMessageRole.TOOL) {
            merged += message
            return@forEach
        }
        val toolCallId = extractHostToolCallId(message.metadata) ?: return@forEach
        val preserved = preservedByToolCallId[toolCallId] ?: return@forEach
        merged += preserved
        seenToolCallIds += toolCallId
    }

    preservedHostToolMessages.forEach { message ->
        val toolCallId = requireNotNull(extractHostToolCallId(message.metadata)) {
            "host-preserved role=TOOL messages require metadata.__host.toolCallId."
        }
        if (toolCallId !in seenToolCallIds) merged += message
    }

    return merged
}

private fun sanitizeToolCalls(values: List<PluginLlmToolCall>): List<PluginLlmToolCall> {
    return values.map { call ->
        PluginLlmToolCall(
            toolCallId = call.normalizedToolCallId,
            toolName = call.normalizedToolName,
            arguments = call.normalizedArguments,
            metadata = call.normalizedMetadata,
        )
    }
}

private fun sanitizeTemperature(value: Double?): Double? {
    if (value == null) return null
    require(value.isFinite()) { "temperature must be finite." }
    require(value in 0.0..2.0) { "temperature must be between 0.0 and 2.0." }
    return value
}

private fun sanitizeTopP(value: Double?): Double? {
    if (value == null) return null
    require(value.isFinite()) { "topP must be finite." }
    require(value in 0.0..1.0) { "topP must be between 0.0 and 1.0." }
    return value
}

private fun sanitizeMaxTokens(value: Int?): Int? {
    if (value == null) return null
    require(value > 0) { "maxTokens must be positive." }
    return value
}

private fun sanitizeMetadata(value: JsonLikeMap?): JsonLikeMap? {
    return value?.let(PluginRuntimeValueSanitizer::requireAllowedMap)
}
