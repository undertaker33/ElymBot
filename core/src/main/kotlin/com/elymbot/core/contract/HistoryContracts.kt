package com.elymbot.core.contract

enum class HistoryDirection {
    OLDEST_TO_NEWEST,
    NEWEST_TO_OLDEST,
}

enum class HistoryMessageStatus {
    COMPLETE,
    STOPPED,
    INTERRUPTED,
}

enum class HistoryRole {
    USER,
    ASSISTANT,
}

data class HistoryTurnRange(
    val fromTurnInclusive: Long,
    val toTurnInclusive: Long,
) {
    init {
        require(fromTurnInclusive <= toTurnInclusive)
    }
}

/** A message position used to continue a page without requiring contiguous turn numbers. */
data class HistoryCursor(
    val turnSequence: Long,
    val messageIndex: Int,
)

data class HistoryReadRequest(
    val sessionId: String,
    val upperBoundExclusive: Long,
    val turnRange: HistoryTurnRange? = null,
    val direction: HistoryDirection = HistoryDirection.NEWEST_TO_OLDEST,
    val cursor: HistoryCursor? = null,
    val pageSize: Int,
    val tokenBudget: Int,
)

data class HistoryRecord(
    val messageId: String,
    val turnSequence: Long,
    val messageIndex: Int,
    val role: HistoryRole,
    val text: String,
    val status: HistoryMessageStatus,
    val truncated: Boolean = false,
)

data class HistoryReadPage(
    /** Records are returned in chronological order within each page. */
    val records: List<HistoryRecord>,
    /** Exclusive continuation position in the requested direction. */
    val continuation: HistoryCursor?,
    val hasMore: Boolean,
)

/** Reads only retained messages from the supplied session and turn window. */
fun interface HistoryReader {
    suspend fun read(request: HistoryReadRequest): HistoryReadPage
}

data class HistorySummary(
    val id: String,
    val sessionId: String,
    val fromTurnInclusive: Long,
    val toTurnInclusive: Long,
    val text: String,
)

data class HistorySummaryCandidate(
    val sessionId: String,
    val fromTurnInclusive: Long,
    val toTurnInclusive: Long,
    val text: String,
    /** Existing summaries wholly covered by this candidate and atomically replaced. */
    val replacesSummaryIds: Set<String> = emptySet(),
)

sealed interface SummaryCommitResult {
    data class Committed(val summary: HistorySummary) : SummaryCommitResult

    data object RejectedStaleCoverage : SummaryCommitResult

    data object RejectedOverlap : SummaryCommitResult
}

/**
 * Stores summaries independently of raw messages. Commit must verify the candidate
 * range against retained history, replace only the explicitly listed summaries
 * wholly covered by the candidate, and reject overlap with any unlisted summary.
 * The raw history is never modified or replaced.
 */
interface HistorySummaryStore {
    suspend fun read(sessionId: String, upperBoundExclusive: Long): List<HistorySummary>

    suspend fun commit(candidate: HistorySummaryCandidate): SummaryCommitResult
}
