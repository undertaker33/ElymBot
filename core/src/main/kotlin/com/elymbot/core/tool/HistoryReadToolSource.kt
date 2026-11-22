package com.elymbot.core.tool

import com.elymbot.core.contract.HistoryCursor
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class HistoryReadToolSource(
    private val historyReader: HistoryReader,
    private val maxPageSize: Int = DEFAULT_MAX_PAGE_SIZE,
    private val tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
) : ToolSource {
    init {
        require(maxPageSize > 0)
        require(tokenBudget > 0)
    }

    override val definition = ToolDefinition(
        id = "core.history-read",
        name = "HistoryRead",
        description = "Read retained user and assistant messages from this session. Use turn bounds to retrieve older history.",
        parametersJsonSchema = PARAMETERS_SCHEMA,
        scopes = setOf(ToolScope.MAIN_SESSION, ToolScope.PERSONA_SESSION),
    )

    override suspend fun execute(
        call: com.elymbot.core.contract.ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (ToolProgress) -> Unit,
    ): ToolExecutionResult {
        val args = Json.parseToJsonElement(call.argumentsJson) as JsonObject
        val fromTurn = args.longOrNull("fromTurn")
        val toTurn = args.longOrNull("toTurn")
        if ((fromTurn == null) != (toTurn == null)) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "Provide both fromTurn and toTurn, or neither.")
        }
        if ((fromTurn != null && fromTurn < 0) || (toTurn != null && toTurn < 0)) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "Turn bounds must be non-negative.")
        }
        if (fromTurn != null && toTurn != null && fromTurn > toTurn) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "fromTurn must not exceed toTurn.")
        }
        val cursorTurn = args.longOrNull("cursorTurn")
        val cursorMessageIndex = args.intOrNull("cursorMessageIndex")
        if ((cursorTurn == null) != (cursorMessageIndex == null) ||
            (cursorTurn != null && cursorTurn < 0) ||
            (cursorMessageIndex != null && cursorMessageIndex < 0)
        ) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "A valid cursor requires cursorTurn and cursorMessageIndex.")
        }
        val requestedLimit = args.intOrNull("limit") ?: DEFAULT_PAGE_SIZE
        if (requestedLimit !in 1..maxPageSize) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "limit must be between 1 and $maxPageSize.")
        }
        val direction = when (args.stringOrNull("direction")) {
            null -> if (fromTurn == null) HistoryDirection.NEWEST_TO_OLDEST else HistoryDirection.OLDEST_TO_NEWEST
            "OLDEST_TO_NEWEST" -> HistoryDirection.OLDEST_TO_NEWEST
            "NEWEST_TO_OLDEST" -> HistoryDirection.NEWEST_TO_OLDEST
            else -> return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "Invalid history direction.")
        }
        onProgress(ToolProgress("Reading retained conversation history"))
        val page = historyReader.read(
            HistoryReadRequest(
                sessionId = context.sessionId,
                upperBoundExclusive = context.historyUpperBoundExclusive,
                turnRange = if (fromTurn != null && toTurn != null) {
                    com.elymbot.core.contract.HistoryTurnRange(fromTurn, toTurn)
                } else {
                    null
                },
                direction = direction,
                cursor = if (cursorTurn != null && cursorMessageIndex != null) {
                    HistoryCursor(cursorTurn, cursorMessageIndex)
                } else {
                    null
                },
                pageSize = requestedLimit,
                tokenBudget = tokenBudget,
            ),
        )
        val visibleRecords = page.records.filter { record ->
            record.turnSequence < context.historyUpperBoundExclusive &&
                (fromTurn == null || record.turnSequence >= fromTurn) &&
                (toTurn == null || record.turnSequence <= toTurn)
        }
        val records = JsonArray(visibleRecords.map { record ->
            buildJsonObject {
                put("messageId", record.messageId)
                put("turnSequence", record.turnSequence)
                put("messageIndex", record.messageIndex)
                put("role", record.role.name)
                put("status", record.status.name)
                put("text", record.text)
                put("truncated", record.truncated)
            }
        })
        val continuation: JsonElement = page.continuation?.let { cursor ->
            buildJsonObject {
                put("turnSequence", cursor.turnSequence)
                put("messageIndex", cursor.messageIndex)
            }
        } ?: JsonNull
        val result = buildJsonObject {
            put("sessionScope", "current")
            put("direction", direction.name)
            put("upperBoundExclusive", context.historyUpperBoundExclusive)
            visibleRecords.minOfOrNull { it.turnSequence }?.let { put("fromTurn", it) } ?: put("fromTurn", JsonNull)
            visibleRecords.maxOfOrNull { it.turnSequence }?.let { put("toTurn", it) } ?: put("toTurn", JsonNull)
            put("records", records)
            put("continuation", continuation)
            put("hasMore", page.hasMore && (visibleRecords.isNotEmpty() || page.continuation != null))
        }
        return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, result.toString())
    }

    private fun JsonObject.longOrNull(name: String): Long? = (this[name] as? JsonPrimitive)
        ?.contentOrNull
        ?.toLongOrNull()

    private fun JsonObject.intOrNull(name: String): Int? = (this[name] as? JsonPrimitive)
        ?.contentOrNull
        ?.toIntOrNull()

    private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull

    companion object {
        const val DEFAULT_PAGE_SIZE = 64
        const val DEFAULT_MAX_PAGE_SIZE = 128
        const val DEFAULT_TOKEN_BUDGET = 4_000

        private const val PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "fromTurn": {"type": "integer", "minimum": 0},
                "toTurn": {"type": "integer", "minimum": 0},
                "limit": {"type": "integer", "minimum": 1, "maximum": 128},
                "direction": {"type": "string", "enum": ["OLDEST_TO_NEWEST", "NEWEST_TO_OLDEST"]},
                "cursorTurn": {"type": "integer", "minimum": 0},
                "cursorMessageIndex": {"type": "integer", "minimum": 0}
              },
              "additionalProperties": false
            }
        """
    }
}
