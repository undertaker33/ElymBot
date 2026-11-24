package com.elymbot.core.contract

import kotlinx.coroutines.flow.Flow

data class PersonaPrompt(
    val personaId: String,
    val name: String,
    val prompt: String,
)

data class ModelSelection(
    val reference: ModelReference,
    val capabilities: ModelCapabilityReport,
)

data class AgentRequest(
    val sessionId: String,
    val executionId: String,
    val attemptId: String,
    val turnSequence: Long,
    /** Excludes the current input and any later turn from history reads. */
    val historyUpperBoundExclusive: Long,
    val persona: PersonaPrompt,
    val primaryModel: ModelSelection,
    val visionModel: ModelSelection? = null,
    val currentInput: ModelMessage,
    val toolScope: ToolScope,
    val toolSources: List<ToolSource> = emptyList(),
    /** Host-normalized routing and media metadata; null for legacy T01 callers. */
    val inputMessage: AgentMessage? = null,
    /** Host-attested registrations supplied by the verified plugin loader. */
    val toolRegistrations: List<ToolRegistration> = emptyList(),
    /** Gate shared by tool advertisement and execution-time authorization. */
    val toolAccessPolicy: ToolAccessPolicy = BuiltInOnlyToolAccessPolicy,
    /** App-owned durable queue boundary; absent until Reply is wired by the host. */
    val replySubmitter: ReplySubmitter? = null,
    /** App-authorized shared context that must not be folded into per-session summaries. */
    val supplementalContext: List<AgentSupplementalContext> = emptyList(),
)

enum class AgentTerminalStatus {
    COMPLETED,
    INTERRUPTED,
    FAILED,
    TIMED_OUT,
    CONTEXT_LIMIT,
    OUTPUT_LIMIT,
}

enum class AgentFailureKind {
    NETWORK,
    AUTHENTICATION,
    MODEL_REJECTED,
    PROTOCOL,
    STREAM_INTERRUPTED,
    VISION_MODEL_MISSING,
    VISION_CAPABILITY_UNKNOWN,
    CONTEXT_LIMIT,
    OUTPUT_LIMIT,
    TOOL_LIMIT,
    EXECUTION_LIMIT,
    INTERNAL,
}

data class AgentFailure(
    val kind: AgentFailureKind,
    val retryable: Boolean,
)

sealed interface AgentEvent {
    val executionId: String
    val attemptId: String

    data class TextDelta(
        override val executionId: String,
        override val attemptId: String,
        val text: String,
    ) : AgentEvent

    /** Ordinary assistant text intended for the chat transcript, not model history. */
    data class AssistantFeedback(
        override val executionId: String,
        override val attemptId: String,
        val text: String,
    ) : AgentEvent

    /** Temporary host-generated tool execution state. It carries no tool-source text. */
    data class ToolProgress(
        override val executionId: String,
        override val attemptId: String,
        val active: Boolean,
    ) : AgentEvent

    /** Emitted once for controlled termination; caller cancellation propagates. */
    data class Finished(
        override val executionId: String,
        override val attemptId: String,
        val status: AgentTerminalStatus,
        val failure: AgentFailure? = null,
        /** True only when an opted-in turn completed permitted empty STOP without a Reply attempt. */
        val successfulSilence: Boolean = false,
    ) : AgentEvent
}

/** Runs one session turn. Returned flow preserves the caller's cancellation. */
fun interface AgentRuntime {
    fun run(request: AgentRequest): Flow<AgentEvent>
}
