package com.elymbot.core.context

import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.CapabilityStatus
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
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelSelection
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.SummaryCommitResult
import com.elymbot.core.contract.ToolScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompilerTest {
    @Test
    fun compressesOldCompleteTurnsAndKeepsRecentTurns() = kotlinx.coroutines.runBlocking {
        val reader = FakeHistoryReader(records = completeTurns(8))
        val summaries = FakeSummaryStore(
            listOf(HistorySummary("prior", "session-1", 1, 1, "Prior compacted notes")),
        )
        val gateway = FakeGateway { request ->
            if (request.maxOutputTokens != null) {
                flow {
                    emit(ModelStreamEvent.TextDelta("Decisions and details from earlier turns."))
                    emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
                }
            } else {
                flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
            }
        }
        val compiler = ContextCompiler(
            historyReader = reader,
            summaryStore = summaries,
            modelGateway = gateway,
            budgetProvider = DefaultModelContextBudgetProvider(
                ContextBudget(
                    contextWindowTokens = 2_000,
                    outputReserveTokens = 200,
                    safetyMarginTokens = 100,
                    compressionTriggerRatio = 0.5,
                ),
            ),
        )

        val compiled = compiler.compile(request(), emptyList())
        val messageText = compiled.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>()
            .joinToString("\n") { it.value }

        assertTrue("At least one compacted range should be saved", summaries.entries.isNotEmpty())
        assertTrue("Compaction should retain turn coverage", summaries.entries.first().toTurnInclusive >= 1)
        assertTrue("Existing summary is explicitly replaced", summaries.candidates.any { "prior" in it.replacesSummaryIds })
        assertTrue(messageText.contains("U-7"))
        assertTrue(messageText.contains("U-8"))
        assertFalse(messageText.contains("U-1 " + "a".repeat(180)))
        assertTrue(compiled.estimatedInputTokens <= ContextBudget(2_000, 200, 100, 0.5).inputLimitTokens)
        assertEquals(100L, reader.requests.first().upperBoundExclusive)
    }

    @Test
    fun excludesInterruptedAndOutOfBoundTurnsFromModelContext() = kotlinx.coroutines.runBlocking {
        val records = completeTurns(2).filterNot { it.turnSequence == 2L && it.role == HistoryRole.ASSISTANT } +
            HistoryRecord("interrupted", 2, 1, HistoryRole.ASSISTANT, "unfinished-secret", HistoryMessageStatus.INTERRUPTED)
        val reader = FakeHistoryReader(records)
        val compiler = compiler(reader, FakeSummaryStore(), FakeGateway { flow { } })

        val compiled = compiler.compile(request(upperBound = 2), emptyList())
        val text = compiled.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertTrue(text.contains("U-1"))
        assertFalse(text.contains("U-2"))
        assertFalse(text.contains("unfinished-secret"))
        assertEquals(2L, reader.requests.single().upperBoundExclusive)
    }

    @Test
    fun excludesAnyTurnContainingATruncatedRecord() = kotlinx.coroutines.runBlocking {
        val records = completeTurns(2).map { record ->
            if (record.messageId == "u-2") record.copy(text = "partial-secret", truncated = true) else record
        }
        val compiled = compiler(FakeHistoryReader(records), FakeSummaryStore(), FakeGateway { flow { } })
            .compile(request(), emptyList())
        val text = compiled.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertTrue(text.contains("U-1"))
        assertFalse(text.contains("partial-secret"))
        assertFalse(text.contains("U-2"))
    }

    @Test
    fun summarySourceContainsOnlyCompleteNonTruncatedTurns() = kotlinx.coroutines.runBlocking {
        val records = completeTurns(8)
            .filterNot { it.messageId == "a-5" }
            .map { record ->
                when (record.messageId) {
                    "u-4" -> record.copy(text = "partial-secret", truncated = true)
                    "a-6" -> record.copy(text = "unfinished-secret", status = HistoryMessageStatus.INTERRUPTED)
                    else -> record
                }
            }
        val summaryInputs = mutableListOf<String>()
        val gateway = FakeGateway { modelRequest ->
            if (modelRequest.maxOutputTokens != null) {
                summaryInputs += modelRequest.messages
                    .flatMap { it.content }
                    .filterIsInstance<ModelContentPart.Text>()
                    .joinToString("\n") { it.value }
                flow {
                    emit(ModelStreamEvent.TextDelta("Summary of complete turn 7."))
                    emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
                }
            } else {
                flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
            }
        }
        val compiler = ContextCompiler(
            FakeHistoryReader(records),
            FakeSummaryStore(),
            gateway,
            DefaultModelContextBudgetProvider(
                ContextBudget(
                    contextWindowTokens = 2_000,
                    outputReserveTokens = 100,
                    safetyMarginTokens = 100,
                    compressionTriggerRatio = 0.8,
                    recentCompleteTurnsToKeep = 1,
                ),
            ),
        )

        compiler.compile(request(), emptyList(), forceCompaction = true)

        val summaryInput = summaryInputs.single()
        assertTrue(summaryInput.contains("U-7"))
        assertTrue(summaryInput.contains("A-7"))
        assertFalse(summaryInput.contains("partial-secret"))
        assertFalse(summaryInput.contains("unfinished-secret"))
        assertFalse(summaryInput.contains("U-5"))
        assertFalse(summaryInput.contains("U-6"))
    }

    @Test
    fun pagesCanSplitATurnButAReadLimitDoesNotPromoteItsPartialFrontier() = kotlinx.coroutines.runBlocking {
        val newestPage = HistoryReadPage(
            records = listOf(HistoryRecord("a-2", 2, 1, HistoryRole.ASSISTANT, "A-2", HistoryMessageStatus.COMPLETE)),
            continuation = com.elymbot.core.contract.HistoryCursor(2, 1),
            hasMore = true,
        )
        val olderPage = HistoryReadPage(
            records = listOf(
                HistoryRecord("u-1", 1, 0, HistoryRole.USER, "U-1", HistoryMessageStatus.COMPLETE),
                HistoryRecord("a-1", 1, 1, HistoryRole.ASSISTANT, "A-1", HistoryMessageStatus.COMPLETE),
                HistoryRecord("u-2", 2, 0, HistoryRole.USER, "U-2", HistoryMessageStatus.COMPLETE),
            ),
            continuation = null,
            hasMore = false,
        )
        val completeReader = FixedPageHistoryReader(listOf(newestPage, olderPage))
        val completeCompiler = ContextCompiler(
            completeReader,
            FakeSummaryStore(),
            FakeGateway { flow { } },
            DefaultModelContextBudgetProvider(ContextBudget(maxHistoryPages = 2, historyPageSize = 3)),
        )
        val complete = completeCompiler.compile(request(), emptyList())
        val completeText = complete.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertTrue(completeText.contains("U-2"))
        assertTrue(completeText.contains("A-2"))
        assertEquals(2, completeReader.requests.size)
        assertEquals(HistoryDirection.NEWEST_TO_OLDEST, completeReader.requests.first().direction)

        val limitedReader = FixedPageHistoryReader(listOf(newestPage))
        val limitedCompiler = ContextCompiler(
            limitedReader,
            FakeSummaryStore(),
            FakeGateway { flow { } },
            DefaultModelContextBudgetProvider(ContextBudget(maxHistoryPages = 1, historyPageSize = 1)),
        )
        val limited = limitedCompiler.compile(request(), emptyList())
        val limitedText = limited.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertFalse(limitedText.contains("A-2"))
        assertFalse(limitedText.contains("U-2"))
    }

    @Test
    fun summaryFailureKeepsPersistedSummaryAndFallsBackToRawHistory() = kotlinx.coroutines.runBlocking {
        val oldSummary = HistorySummary("old", "session-1", 1, 1, "Existing useful summary")
        val store = FakeSummaryStore(listOf(oldSummary))
        val gateway = FakeGateway { throw IllegalStateException("summary endpoint unavailable") }
        val compiler = ContextCompiler(
            FakeHistoryReader(completeTurns(10)),
            store,
            gateway,
            DefaultModelContextBudgetProvider(ContextBudget(1_000, 100, 100, 0.5)),
        )

        compiler.compile(request(), emptyList())

        assertEquals(listOf(oldSummary), store.entries)
        assertTrue(gateway.calls > 0)
    }

    @Test
    fun laterToolBatchCanTriggerAnotherCompaction() = kotlinx.coroutines.runBlocking {
        val reader = FakeHistoryReader(completeTurns(8))
        val store = FakeSummaryStore()
        val gateway = FakeGateway { request ->
            if (request.maxOutputTokens != null) {
                flow {
                    emit(ModelStreamEvent.TextDelta("Short retained decisions."))
                    emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
                }
            } else {
                flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
            }
        }
        val compiler = ContextCompiler(
            reader,
            store,
            gateway,
            DefaultModelContextBudgetProvider(ContextBudget(2_000, 200, 100, 0.5)),
        )
        compiler.compile(request(), emptyList())
        reader.records = completeTurns(10)
        val earlierBatch = listOf(
            ModelMessage(
                ModelRole.ASSISTANT,
                content = listOf(ModelContentPart.Text("Checking older records.")),
                toolCalls = listOf(com.elymbot.core.contract.ModelToolCall("tool-1", "search", "{}")),
            ),
            ModelMessage(
                ModelRole.TOOL,
                content = listOf(ModelContentPart.Text("tool result " + "r".repeat(2_700))),
                toolCallId = "tool-1",
            ),
        )

        compiler.compile(request(), emptyList(), activeToolBatches = listOf(earlierBatch))

        assertTrue("The later complete tool batch should trigger a new summary pass", store.candidates.size >= 2)
    }

    @Test
    fun summarizesOlderClosedToolBatchesAndRetainsNewestCallResultPair() = kotlinx.coroutines.runBlocking {
        val budget = ContextBudget(
            contextWindowTokens = 1_250,
            outputReserveTokens = 100,
            safetyMarginTokens = 100,
            compressionTriggerRatio = 1.0,
            maxSummaryOutputTokens = 100,
        )
        var summaryRequest: ModelRequest? = null
        val gateway = FakeGateway { request ->
            if (request.maxOutputTokens != null) {
                summaryRequest = request
                flow {
                    emit(ModelStreamEvent.TextDelta("Earlier results were summarized."))
                    emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
                }
            } else {
                flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
            }
        }
        val compiler = ContextCompiler(
            FakeHistoryReader(emptyList()),
            FakeSummaryStore(),
            gateway,
            DefaultModelContextBudgetProvider(budget),
            CharacterTokenCounter(),
        )
        val batches = listOf(
            closedBatch("call-1", "old-private-one".repeat(30)),
            closedBatch("call-2", "old-private-two".repeat(30)),
            closedBatch("call-3", "latest-result".repeat(20)),
        )

        val compiled = compiler.compile(request(), emptyList(), activeToolBatches = batches)
        val toolCalls = compiled.messages.flatMap { it.toolCalls }
        val toolResults = compiled.messages.filter { it.role == ModelRole.TOOL }
        val text = compiled.messages.flatMap { it.content }.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertTrue(compiled.droppedToolBatchCount >= 1)
        assertEquals("Earlier results were summarized.", compiled.toolBatchSummary)
        assertTrue(text.contains("Earlier results were summarized."))
        assertFalse(text.contains("old-private-one"))
        assertFalse(text.contains("old-private-two"))
        assertTrue(text.contains("latest-result"))
        assertEquals(listOf("call-3"), toolCalls.map { it.callId })
        assertEquals(listOf("call-3"), toolResults.map { it.toolCallId })
        assertTrue(compiled.estimatedInputTokens <= budget.inputLimitTokens)
        assertEquals(100, summaryRequest?.maxOutputTokens)
    }

    @Test
    fun contextLimitDoesNotSplitOrDiscardNewestUnclosedToolBatch() = kotlinx.coroutines.runBlocking {
        val budget = ContextBudget(
            contextWindowTokens = 500,
            outputReserveTokens = 100,
            safetyMarginTokens = 100,
            compressionTriggerRatio = 1.0,
            maxSummaryOutputTokens = 40,
        )
        val compiler = ContextCompiler(
            FakeHistoryReader(emptyList()),
            FakeSummaryStore(),
            FakeGateway { flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) } },
            DefaultModelContextBudgetProvider(budget),
            CharacterTokenCounter(),
        )
        val unclosed = listOf(
            ModelMessage(
                ModelRole.ASSISTANT,
                content = emptyList(),
                toolCalls = listOf(
                    com.elymbot.core.contract.ModelToolCall(
                        "call-latest",
                        "search",
                        "{\"query\":\"${"x".repeat(400)}\"}",
                    ),
                ),
            ),
        )

        try {
            compiler.compile(request(), emptyList(), activeToolBatches = listOf(unclosed))
            throw AssertionError("An unclosed batch must not be split or silently discarded")
        } catch (_: ContextLimitExceededException) {
            // The required newest tool batch remains atomic when no valid compression is possible.
        }
    }

    @Test
    fun requiredPersonaAndCurrentInputFailWhenTheyAloneExceedHardBudget() = kotlinx.coroutines.runBlocking {
        val compiler = ContextCompiler(
            FakeHistoryReader(emptyList()),
            FakeSummaryStore(),
            FakeGateway { flow { } },
            DefaultModelContextBudgetProvider(ContextBudget(100, 20, 20, 0.8)),
        )
        val oversized = request().copy(persona = PersonaPrompt("p", "Persona", "p".repeat(400)))

        try {
            compiler.compile(oversized, emptyList())
            throw AssertionError("Expected context overflow")
        } catch (_: ContextLimitExceededException) {
            // The required persona and current input are never silently clipped.
        }
    }

    private fun compiler(reader: FakeHistoryReader, store: FakeSummaryStore, gateway: FakeGateway) = ContextCompiler(
        reader,
        store,
        gateway,
        DefaultModelContextBudgetProvider(ContextBudget(2_000, 100, 100, 0.8)),
    )

    private fun request(upperBound: Long = 100) = AgentRequest(
        sessionId = "session-1",
        executionId = "execution-1",
        attemptId = "attempt-1",
        turnSequence = upperBound,
        historyUpperBoundExclusive = upperBound,
        persona = PersonaPrompt("persona-1", "Persona", "Respond helpfully."),
        primaryModel = ModelSelection(
            ModelReference("service-1", "model-1"),
            ModelCapabilityReport(
                ModelReference("service-1", "model-1"),
                CapabilityStatus.SUPPORTED,
                CapabilityStatus.SUPPORTED,
                CapabilityStatus.SUPPORTED,
                CapabilityStatus.SUPPORTED,
            ),
        ),
        currentInput = ModelMessage(ModelRole.USER, listOf(ModelContentPart.Text("Current question"))),
        toolScope = ToolScope.PERSONA_SESSION,
    )

    private fun completeTurns(count: Int): List<HistoryRecord> = (1..count).flatMap { turn ->
        listOf(
            HistoryRecord("u-$turn", turn.toLong(), 0, HistoryRole.USER, "U-$turn " + "a".repeat(180), HistoryMessageStatus.COMPLETE),
            HistoryRecord("a-$turn", turn.toLong(), 1, HistoryRole.ASSISTANT, "A-$turn " + "b".repeat(180), HistoryMessageStatus.COMPLETE),
        )
    }

    private class FakeHistoryReader(var records: List<HistoryRecord>) : HistoryReader {
        val requests = mutableListOf<HistoryReadRequest>()

        override suspend fun read(request: HistoryReadRequest): HistoryReadPage {
            requests += request
            val filtered = records
                .filter { it.turnSequence < request.upperBoundExclusive }
                .sortedWith(compareBy(HistoryRecord::turnSequence, HistoryRecord::messageIndex))
            return HistoryReadPage(filtered, null, false)
        }
    }

    private class FixedPageHistoryReader(
        private val pages: List<HistoryReadPage>,
    ) : HistoryReader {
        val requests = mutableListOf<HistoryReadRequest>()
        private var index = 0

        override suspend fun read(request: HistoryReadRequest): HistoryReadPage {
            requests += request
            return pages[index++]
        }
    }

    private fun closedBatch(callId: String, result: String) = listOf(
        ModelMessage(
            ModelRole.ASSISTANT,
            content = listOf(ModelContentPart.Text("Checking $callId")),
            toolCalls = listOf(com.elymbot.core.contract.ModelToolCall(callId, "search", "{\"query\":\"$callId\"}")),
        ),
        ModelMessage(
            ModelRole.TOOL,
            content = listOf(ModelContentPart.Text(result)),
            toolCallId = callId,
        ),
    )

    private class CharacterTokenCounter : ContextTokenCounter {
        override fun estimate(messages: List<ModelMessage>, tools: List<com.elymbot.core.contract.ModelToolDefinition>): Int =
            messages.sumOf { message ->
                1 + message.content.sumOf { part ->
                    when (part) {
                        is ModelContentPart.Text -> part.value.length
                        is ModelContentPart.Image -> 10
                    }
                } + message.toolCalls.sumOf { call -> call.callId.length + call.name.length + call.argumentsJson.length }
            } + tools.sumOf { it.name.length + it.description.length + it.parametersJsonSchema.length }
    }

    private class FakeSummaryStore(initial: List<HistorySummary> = emptyList()) : HistorySummaryStore {
        val entries = initial.toMutableList()
        val candidates = mutableListOf<HistorySummaryCandidate>()

        override suspend fun read(sessionId: String, upperBoundExclusive: Long): List<HistorySummary> = entries
            .filter { it.sessionId == sessionId && it.toTurnInclusive < upperBoundExclusive }

        override suspend fun commit(candidate: HistorySummaryCandidate): SummaryCommitResult {
            candidates += candidate
            entries.removeAll { it.id in candidate.replacesSummaryIds }
            val summary = HistorySummary(
                "summary-${entries.size + 1}",
                candidate.sessionId,
                candidate.fromTurnInclusive,
                candidate.toTurnInclusive,
                candidate.text,
            )
            entries += summary
            return SummaryCommitResult.Committed(summary)
        }
    }

    private class FakeGateway(
        private val response: (ModelRequest) -> Flow<ModelStreamEvent>,
    ) : ModelGateway {
        var calls = 0

        override suspend fun listModels(serviceId: String): List<String> = emptyList()

        override suspend fun probe(model: ModelReference): ModelCapabilityReport = error("Not used")

        override fun stream(request: ModelRequest): Flow<ModelStreamEvent> {
            calls += 1
            return response(request)
        }
    }
}
