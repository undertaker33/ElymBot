package com.elymbot.android.data

import androidx.room.withTransaction
import com.elymbot.core.contract.HistoryCursor
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryMessageStatus
import com.elymbot.core.contract.HistoryReadPage
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.HistoryRecord
import com.elymbot.core.contract.HistoryRole
import com.elymbot.core.contract.HistorySummary
import com.elymbot.core.contract.HistorySummaryCandidate
import com.elymbot.core.contract.HistorySummaryStore
import com.elymbot.core.contract.SummaryCommitResult
import java.util.UUID

class RoomHistoryStore(
    private val database: ElymBotDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : HistoryReader, HistorySummaryStore {
    override suspend fun read(request: HistoryReadRequest): HistoryReadPage {
        require(request.pageSize > 0)
        require(request.tokenBudget > 0)
        if (request.upperBoundExclusive <= 0) return HistoryReadPage(emptyList(), null, false)

        val cursor = request.cursor
        val range = request.turnRange
        val limit = if (request.pageSize == Int.MAX_VALUE) Int.MAX_VALUE else request.pageSize + 1
        val queried = when (request.direction) {
            HistoryDirection.OLDEST_TO_NEWEST -> database.messageDao().historyOldestPage(
                sessionId = request.sessionId,
                upperBoundExclusive = request.upperBoundExclusive,
                fromTurnInclusive = range?.fromTurnInclusive,
                toTurnInclusive = range?.toTurnInclusive,
                cursorTurn = cursor?.turnSequence,
                cursorIndex = cursor?.messageIndex,
                limit = limit,
            )
            HistoryDirection.NEWEST_TO_OLDEST -> database.messageDao().historyNewestPage(
                sessionId = request.sessionId,
                upperBoundExclusive = request.upperBoundExclusive,
                fromTurnInclusive = range?.fromTurnInclusive,
                toTurnInclusive = range?.toTurnInclusive,
                cursorTurn = cursor?.turnSequence,
                cursorIndex = cursor?.messageIndex,
                limit = limit,
            )
        }
        if (queried.isEmpty()) return HistoryReadPage(emptyList(), null, false)
        val hasMoreBeyondPage = queried.size > request.pageSize
        val ordered = queried.take(request.pageSize)

        val selected = mutableListOf<HistoryMessageWithTurnStatus>()
        val records = mutableListOf<HistoryRecord>()
        var tokensRemaining = request.tokenBudget
        var nextIndex = 0
        for ((index, row) in ordered.withIndex()) {
            val message = row.message
            if (selected.size >= request.pageSize) break
            val tokens = estimateTokens(message.text)
            if (tokens <= tokensRemaining) {
                selected += row
                records += row.toHistoryRecord()
                tokensRemaining -= tokens
                nextIndex = index + 1
                continue
            }
            if (selected.isEmpty()) {
                val clippedText = message.text.takeCodePoints(tokensRemaining * CHARS_PER_TOKEN)
                selected += row
                records += row.toHistoryRecord().copy(
                    text = clippedText,
                    truncated = true,
                )
                nextIndex = index + 1
            }
            break
        }

        if (selected.isEmpty()) return HistoryReadPage(emptyList(), null, ordered.isNotEmpty())
        val chronological = records.sortedWith(compareBy({ it.turnSequence }, { it.messageIndex }))
        val continuationPosition = selected.last().message
        val continuation = HistoryCursor(continuationPosition.turnSequence, continuationPosition.messageIndex)
        return HistoryReadPage(
            records = chronological,
            continuation = continuation,
            hasMore = nextIndex < ordered.size || hasMoreBeyondPage,
        )
    }

    override suspend fun read(sessionId: String, upperBoundExclusive: Long): List<HistorySummary> =
        database.historySummaryDao().getBefore(sessionId, upperBoundExclusive).map { it.toContract() }

    override suspend fun commit(candidate: HistorySummaryCandidate): SummaryCommitResult = database.withTransaction {
        if (candidate.text.isBlank() || candidate.fromTurnInclusive > candidate.toTurnInclusive) {
            return@withTransaction SummaryCommitResult.RejectedStaleCoverage
        }
        val conversation = database.conversationDao().get(candidate.sessionId)
            ?: return@withTransaction SummaryCommitResult.RejectedStaleCoverage
        if (candidate.toTurnInclusive >= conversation.nextTurnSequence) {
            return@withTransaction SummaryCommitResult.RejectedStaleCoverage
        }
        val turns = database.turnDao().between(
            candidate.sessionId,
            candidate.fromTurnInclusive,
            candidate.toTurnInclusive,
        )
        if (turns.isEmpty() || turns.first().sequence != candidate.fromTurnInclusive ||
            turns.last().sequence != candidate.toTurnInclusive ||
            turns.any { it.status != TURN_STATUS_COMPLETED }
        ) {
            return@withTransaction SummaryCommitResult.RejectedStaleCoverage
        }

        val summaries = database.historySummaryDao().getForSession(candidate.sessionId)
        val replacementIds = candidate.replacesSummaryIds
        val replacements = summaries.filter { it.id in replacementIds }
        if (replacements.size != replacementIds.size || replacements.any {
                it.fromTurnInclusive < candidate.fromTurnInclusive || it.toTurnInclusive > candidate.toTurnInclusive
            }
        ) {
            return@withTransaction SummaryCommitResult.RejectedStaleCoverage
        }
        if (summaries.any { summary ->
                summary.id !in replacementIds &&
                    summary.fromTurnInclusive <= candidate.toTurnInclusive &&
                    summary.toTurnInclusive >= candidate.fromTurnInclusive
            }
        ) {
            return@withTransaction SummaryCommitResult.RejectedOverlap
        }

        if (replacementIds.isNotEmpty()) database.historySummaryDao().deleteIds(replacementIds.toList())
        val summary = HistorySummaryEntity(
            id = UUID.randomUUID().toString(),
            sessionId = candidate.sessionId,
            fromTurnInclusive = candidate.fromTurnInclusive,
            toTurnInclusive = candidate.toTurnInclusive,
            text = candidate.text,
            createdAt = now(),
        )
        database.historySummaryDao().insert(summary)
        SummaryCommitResult.Committed(summary.toContract())
    }

    private fun MessageEntity.toHistoryRecord(turnStatus: String?): HistoryRecord {
        val status = when (if (role == MESSAGE_ROLE_USER) null else this.status ?: turnStatus) {
            TURN_STATUS_STOPPED -> HistoryMessageStatus.STOPPED
            TURN_STATUS_INTERRUPTED, TURN_STATUS_FAILED, TURN_STATUS_TIMED_OUT,
            TURN_STATUS_CONTEXT_LIMIT, TURN_STATUS_OUTPUT_LIMIT -> HistoryMessageStatus.INTERRUPTED
            else -> HistoryMessageStatus.COMPLETE
        }
        return HistoryRecord(
            messageId = id,
            turnSequence = turnSequence,
            messageIndex = messageIndex,
            role = if (role == MESSAGE_ROLE_USER) HistoryRole.USER else HistoryRole.ASSISTANT,
            text = text,
            status = status,
        )
    }

    private fun HistoryMessageWithTurnStatus.toHistoryRecord(): HistoryRecord =
        message.toHistoryRecord(turnStatus)

    private fun HistorySummaryEntity.toContract() = HistorySummary(
        id = id,
        sessionId = sessionId,
        fromTurnInclusive = fromTurnInclusive,
        toTurnInclusive = toTurnInclusive,
        text = text,
    )

    private fun estimateTokens(text: String): Int =
        ((text.codePointCount(0, text.length) + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN).coerceAtLeast(1)

    private fun String.takeCodePoints(count: Int): String {
        if (count <= 0) return ""
        val codePoints = codePoints().limit(count.toLong()).toArray()
        return buildString {
            codePoints.forEach { appendCodePoint(it) }
        }
    }

    private companion object {
        const val CHARS_PER_TOKEN = 4
        const val TURN_STATUS_COMPLETED = "COMPLETED"
        const val TURN_STATUS_STOPPED = "STOPPED"
        const val TURN_STATUS_INTERRUPTED = "INTERRUPTED"
        const val TURN_STATUS_FAILED = "FAILED"
        const val TURN_STATUS_TIMED_OUT = "TIMED_OUT"
        const val TURN_STATUS_CONTEXT_LIMIT = "CONTEXT_LIMIT"
        const val TURN_STATUS_OUTPUT_LIMIT = "OUTPUT_LIMIT"
        const val MESSAGE_ROLE_USER = "USER"
    }
}
