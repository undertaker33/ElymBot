package com.elymbot.core.tool

import com.elymbot.core.contract.HistoryCursor
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryMessageStatus
import com.elymbot.core.contract.HistoryReadPage
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.HistoryRecord
import com.elymbot.core.contract.HistoryRole
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun rejectsDuplicateNamesAndOutOfScopeOrInvalidArguments() = runTest {
        val source = EchoTool("echo", setOf(ToolScope.MAIN_SESSION))
        try {
            ToolRegistry(listOf(source, EchoTool("echo", setOf(ToolScope.PERSONA_SESSION), "duplicate")))
            throw AssertionError("Duplicate tool name must be rejected")
        } catch (_: IllegalArgumentException) {
            // Duplicate names are rejected at registration time.
        }

        val registry = ToolRegistry(listOf(source))
        assertTrue(registry.definitionsFor(ToolScope.PERSONA_SESSION).isEmpty())
        val wrongScope = registry.execute(
            ModelToolCall("call-1", "echo", "{\"text\":\"ok\"}"),
            context(ToolScope.PERSONA_SESSION),
        )
        val invalidArguments = registry.execute(
            ModelToolCall("call-2", "echo", "{\"text\":\"ok\",\"extra\":1}"),
            context(ToolScope.MAIN_SESSION),
        )

        assertEquals(ToolResultStatus.REJECTED, wrongScope.status)
        assertEquals(ToolResultStatus.REJECTED, invalidArguments.status)
        assertEquals("call-1", wrongScope.callId)
    }

    @Test
    fun sourceProgressTextIsDiscardedAndHostStateHasOnlyExecutionBounds() = runTest {
        val secret = "api_key=tool-private-value"
        val source = object : ToolSource {
            override val definition = registryTestDefinition("progress", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                onProgress(ToolProgress(secret))
                onProgress(ToolProgress(secret.repeat(20)))
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
            }
        }
        val states = mutableListOf<Boolean>()
        val result = ToolRegistry(listOf(source)).execute(
            ModelToolCall("progress-call", "progress", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
            onExecutionState = states::add,
        )

        assertEquals(listOf(true, false), states)
        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertFalse(result.content.contains(secret))
    }

    @Test
    fun timesOutToolAndKeepsResultBounded() = runTest {
        val slow = object : ToolSource {
            override val definition = registryTestDefinition("slow", setOf(ToolScope.MAIN_SESSION))
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                delay(1_000)
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "late")
            }
        }
        val registry = ToolRegistry(listOf(slow), executionTimeoutMillis = 10, maxResultCharacters = 8)
        val timedOut = registry.execute(
            ModelToolCall("timeout", "slow", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )
        assertEquals(ToolResultStatus.FAILED, timedOut.status)
        assertTrue(timedOut.content.contains("timed out"))

        val longResult = object : ToolSource {
            override val definition = registryTestDefinition("long", setOf(ToolScope.MAIN_SESSION))
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ) = ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "123456789012345")
        }
        val bounded = ToolRegistry(listOf(longResult), maxResultCharacters = 8).execute(
            ModelToolCall("bounded", "long", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )
        assertEquals("12345678…[truncated]", bounded.content)
    }

    @Test
    fun historyReadUsesBoundSessionAndFiltersRecordsPastUpperBound() = runTest {
        val reader = RecordingHistoryReader()
        val source = HistoryReadToolSource(reader, maxPageSize = 10, tokenBudget = 100)
        val registry = ToolRegistry(listOf(source))
        val result = registry.execute(
            ModelToolCall("history-1", "HistoryRead", "{\"fromTurn\":2,\"toTurn\":5,\"limit\":4}"),
            context(ToolScope.PERSONA_SESSION).copy(sessionId = "private-session", historyUpperBoundExclusive = 5),
        )
        val json = Json.parseToJsonElement(result.content).jsonObject
        val records = json["records"]!!.jsonArray

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertEquals("private-session", reader.lastRequest!!.sessionId)
        assertEquals(5L, reader.lastRequest!!.upperBoundExclusive)
        assertEquals(HistoryDirection.OLDEST_TO_NEWEST, reader.lastRequest!!.direction)
        assertEquals(2, records.size)
        assertEquals(2L, records.first().jsonObject["turnSequence"]!!.jsonPrimitive.content.toLong())
        assertFalse(result.content.contains("secret-after-bound"))
        assertEquals("current", json["sessionScope"]!!.jsonPrimitive.content)
    }

    private fun context(scope: ToolScope) = ToolExecutionContext(
        sessionId = "session-1",
        turnSequence = 10,
        attemptId = "attempt-1",
        historyUpperBoundExclusive = 10,
        scope = scope,
    )

    private class EchoTool(name: String, scopes: Set<ToolScope>, idSuffix: String = name) : ToolSource {
        override val definition = registryTestDefinition(name, scopes).copy(id = "test.$idSuffix")

        override suspend fun execute(
            call: ModelToolCall,
            context: ToolExecutionContext,
            onProgress: suspend (ToolProgress) -> Unit,
        ) = ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
    }

    private class RecordingHistoryReader : HistoryReader {
        var lastRequest: HistoryReadRequest? = null

        override suspend fun read(request: HistoryReadRequest): HistoryReadPage {
            lastRequest = request
            return HistoryReadPage(
                records = listOf(
                    HistoryRecord("m2", 2, 0, HistoryRole.USER, "retained", HistoryMessageStatus.COMPLETE),
                    HistoryRecord("m4", 4, 1, HistoryRole.ASSISTANT, "also retained", HistoryMessageStatus.STOPPED),
                    HistoryRecord("m5", 5, 0, HistoryRole.USER, "secret-after-bound", HistoryMessageStatus.COMPLETE),
                ),
                continuation = HistoryCursor(4, 1),
                hasMore = true,
            )
        }
    }
}

private fun registryTestDefinition(name: String, scopes: Set<ToolScope>) = ToolDefinition(
    id = "test.$name",
    name = name,
    description = "test tool",
    parametersJsonSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}""",
    scopes = scopes,
)
