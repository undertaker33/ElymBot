package com.elymbot.core.contract

import kotlinx.coroutines.flow.Flow

/** Stable selection of a model within a configured service. */
data class ModelReference(
    val serviceId: String,
    val modelId: String,
)

/** Result shared by the three user-visible probes and the internal tool-call probe. */
enum class CapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

data class ModelCapabilityReport(
    val model: ModelReference,
    val availability: CapabilityStatus,
    val vision: CapabilityStatus,
    val nativeStreaming: CapabilityStatus,
    val structuredToolCalls: CapabilityStatus,
    /** Failure details are keyed independently so successful probes remain visible. */
    val failuresByTarget: Map<ModelProbeTarget, ModelProbeFailure> = emptyMap(),
)

/** The three user-visible probes and the internal structured-tool probe. */
enum class ModelProbeTarget {
    AVAILABILITY,
    VISION,
    NATIVE_STREAMING,
    STRUCTURED_TOOL_CALLS,
}

enum class ModelProbeFailureKind {
    NETWORK,
    AUTHENTICATION,
    HTTP,
    API_REJECTED,
    PROTOCOL,
    TIMED_OUT,
}

/** Sanitized classification only; raw provider messages and credentials stay private. */
data class ModelProbeFailure(
    val kind: ModelProbeFailureKind,
    val httpStatusCode: Int? = null,
    val retryable: Boolean,
)

enum class ModelRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

sealed interface ModelContentPart {
    data class Text(val value: String) : ModelContentPart

    data class Image(
        val bytes: ByteArray,
        val mediaType: String,
    ) : ModelContentPart
}

data class ModelMessage(
    val role: ModelRole,
    val content: List<ModelContentPart>,
    val toolCallId: String? = null,
    val toolCalls: List<ModelToolCall> = emptyList(),
)

data class ModelToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
)

data class ModelToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String,
)

data class ModelRequest(
    val model: ModelReference,
    val messages: List<ModelMessage>,
    val tools: List<ModelToolDefinition> = emptyList(),
    val useNativeStreaming: Boolean,
    val maxOutputTokens: Int? = null,
)

enum class ModelFinishReason {
    STOP,
    TOOL_CALLS,
    OUTPUT_LIMIT,
}

sealed interface ModelStreamEvent {
    data class TextDelta(val text: String) : ModelStreamEvent

    /** Emitted once, after the adapter has validated the response's terminal marker. */
    data class Completed(
        val finishReason: ModelFinishReason,
        val toolCalls: List<ModelToolCall> = emptyList(),
    ) : ModelStreamEvent
}

/**
 * A typed, sanitized failure from a model request. Caller cancellation remains a
 * kotlinx.coroutines CancellationException and must not be converted to this type.
 */
sealed class ModelRequestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(message: String, cause: Throwable? = null) : ModelRequestException(message, cause)

    class HttpStatus(val statusCode: Int, message: String) : ModelRequestException(message)

    class ApiRejected(
        val code: String?,
        message: String,
        val httpStatusCode: Int? = null,
    ) : ModelRequestException(message)

    class Protocol(message: String, cause: Throwable? = null) : ModelRequestException(message, cause)

    class StreamInterrupted(message: String, cause: Throwable? = null) : ModelRequestException(message, cause)

    class ContextLimit(
        message: String,
        val httpStatusCode: Int? = null,
    ) : ModelRequestException(message)

    class TimedOut(message: String, cause: Throwable? = null) : ModelRequestException(message, cause)
}

/** Model discovery, capability probes, and cancellable text/tool responses. */
interface ModelGateway {
    suspend fun listModels(serviceId: String): List<String>

    suspend fun probe(model: ModelReference): ModelCapabilityReport

    /**
     * Emits body deltas as they arrive, then exactly one Completed event when the
     * response is complete and valid. Tool calls are emitted only in that terminal
     * event after the complete batch has been validated. Non-streaming models emit
     * their body as one delta after the complete response arrives.
     */
    fun stream(request: ModelRequest): Flow<ModelStreamEvent>
}
