package com.elymbot.core.contract

/**
 * Stable host coordinates for one Reply tool call. Ordinals are zero-based; the
 * queue's idempotency identity includes both ordinals because provider call IDs
 * may repeat in later tool batches. `toolCallId` remains for protocol correlation.
 */
data class ReplySubmissionContext(
    val sessionId: String,
    val executionId: String,
    val attemptId: String,
    val turnSequence: Long,
    /** Position of the model tool-call batch in this execution. */
    val toolStepOrdinal: Int,
    /** Position of this call within its model-provided batch. */
    val toolCallOrdinal: Int,
    /** Provider call ID retained for tool-result correlation. */
    val toolCallId: String,
)

data class ReplyMessage(
    val text: String,
    val suggestedDelayMillis: Long,
)

data class ReplySubmission(
    val context: ReplySubmissionContext,
    /** Items are persisted and later delivered in this order. */
    val messages: List<ReplyMessage>,
)

enum class ReplyRejectionCode {
    INVALID_REQUEST,
    STALE_ATTEMPT,
    SESSION_NOT_AVAILABLE,
    NOT_AUTHORIZED,
}

enum class ReplyFailureCode {
    PERSISTENCE,
    INTERNAL,
}

/**
 * Accepted means the App durably queued the complete ordered submission. It does
 * not mean any delivery target has acknowledged receipt.
 */
sealed interface ReplySubmissionResult {
    data class Accepted(val messageIds: List<String>) : ReplySubmissionResult

    data class Rejected(val code: ReplyRejectionCode) : ReplySubmissionResult

    data class Failed(val code: ReplyFailureCode) : ReplySubmissionResult
}

/** App-owned durable queue boundary used by the host Reply tool. */
fun interface ReplySubmitter {
    suspend fun submit(submission: ReplySubmission): ReplySubmissionResult
}
