package com.elymbot.core.agent

import com.elymbot.core.context.ContextBudget
import com.elymbot.core.context.ContextCompiler
import com.elymbot.core.context.ConservativeContextTokenCounter
import com.elymbot.core.context.DefaultModelContextBudgetProvider
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentFailureKind
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.HistoryMessageStatus
import com.elymbot.core.contract.HistoryReadPage
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
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
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelSelection
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.SummaryCommitResult
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReActAgentRuntimeTest {
    @Test
    fun executesToolBatchInOrderReturnsResultsByIdAndContinues() = runTest {
        val gateway = FakeGateway { call, _ ->
            if (call == 1) flow {
                emit(ModelStreamEvent.TextDelta("I will check."))
                emit(
                    ModelStreamEvent.Completed(
                        ModelFinishReason.TOOL_CALLS,
                        listOf(ModelToolCall("id-a", "alpha", "{}"), ModelToolCall("id-b", "beta", "{}")),
                    ),
                )
            } else flow {
                emit(ModelStreamEvent.TextDelta("Answer."))
                emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
            }
        }
        val order = mutableListOf<String>()
        val sources = listOf(
            RecordingTool("alpha", order),
            RecordingTool("beta", order),
        )
        val runtime = runtime(gateway)

        val events = runtime.run(request(toolSources = sources)).toList()

        assertEquals(listOf("alpha", "beta"), order)
        assertEquals(2, gateway.requests.size)
        val resultMessages = gateway.requests[1].messages.filter { it.role == ModelRole.TOOL }
        assertEquals(listOf("id-a", "id-b"), resultMessages.map { it.toolCallId })
        assertEquals(listOf("result:alpha", "result:beta"), resultMessages.map { (it.content.single() as ModelContentPart.Text).value })
        assertEquals(
            listOf(true, false, true, false),
            events.filterIsInstance<AgentEvent.ToolProgress>().map { it.active },
        )
        assertTrue(events.none { it is AgentEvent.AssistantFeedback && it.text.startsWith("Started ") })
        assertTrue(events.any { it is AgentEvent.TextDelta && it.text == "Answer." })
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
        assertTrue(events.all { it.executionId == "execution-1" && it.attemptId == "attempt-1" })
    }

    @Test
    fun rejectedToolResultIsReturnedAndModelCanContinue() = runTest {
        val gateway = FakeGateway { call, _ ->
            if (call == 1) flow {
                emit(ModelStreamEvent.Completed(ModelFinishReason.TOOL_CALLS, listOf(ModelToolCall("id-x", "denied", "{}"))))
            } else flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
        }
        val rejected = object : ToolSource {
            override val definition = agentTestTool("denied")
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ) = ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "operation not permitted")
        }

        val events = runtime(gateway).run(request(toolSources = listOf(rejected))).toList()
        val result = gateway.requests[1].messages.single { it.role == ModelRole.TOOL }

        assertEquals("id-x", result.toolCallId)
        assertEquals("operation not permitted", (result.content.single() as ModelContentPart.Text).value)
        assertTrue(events.any { it is AgentEvent.AssistantFeedback && it.text.contains("denied: operation not permitted") })
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
    }

    @Test
    fun hardContextLimitRetriesTheCurrentModelRequestOnlyOnce() = runTest {
        val gateway = FakeGateway { call, _ ->
            when (call) {
                1 -> flow {
                    emit(ModelStreamEvent.Completed(ModelFinishReason.TOOL_CALLS, listOf(ModelToolCall("id-a", "alpha", "{}"))))
                }

                2 -> flow { throw ModelRequestException.ContextLimit("too large") }
                else -> flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
            }
        }
        val toolOrder = mutableListOf<String>()
        val events = runtime(gateway, maxModelCalls = 3)
            .run(request(toolSources = listOf(RecordingTool("alpha", toolOrder))))
            .toList()

        assertEquals(3, gateway.requests.size)
        assertEquals(listOf("alpha"), toolOrder)
        assertEquals(
            gateway.requests[1].messages.filter { it.role == ModelRole.TOOL },
            gateway.requests[2].messages.filter { it.role == ModelRole.TOOL },
        )
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
    }

    @Test
    fun serverContextLimitCompactsOlderToolBatchesWhenLocalBudgetHasRoom() = runTest {
        val budget = ContextBudget()
        val toolOrder = mutableListOf<String>()
        val normalRequests = mutableListOf<ModelRequest>()
        val summaryRequests = mutableListOf<ModelRequest>()
        val gateway = FakeGateway { _, modelRequest ->
            if (modelRequest.maxOutputTokens != null) {
                summaryRequests += modelRequest
                flow {
                    emit(ModelStreamEvent.TextDelta("Earlier alpha result is summarized."))
                    emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
                }
            } else {
                normalRequests += modelRequest
                when (normalRequests.size) {
                    1 -> flow {
                        emit(ModelStreamEvent.Completed(
                            ModelFinishReason.TOOL_CALLS,
                            listOf(ModelToolCall("id-a", "alpha", "{}")),
                        ))
                    }

                    2 -> flow {
                        emit(ModelStreamEvent.Completed(
                            ModelFinishReason.TOOL_CALLS,
                            listOf(ModelToolCall("id-b", "beta", "{}")),
                        ))
                    }

                    3 -> flow { throw ModelRequestException.ContextLimit("server context limit") }
                    else -> flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
                }
            }
        }
        val contextCompiler = ContextCompiler(
            historyReader = EmptyHistoryReader,
            summaryStore = EmptySummaryStore,
            modelGateway = gateway,
            budgetProvider = DefaultModelContextBudgetProvider(budget),
        )
        val runtime = ReActAgentRuntime(
            modelGateway = gateway,
            contextCompiler = contextCompiler,
            historyReader = EmptyHistoryReader,
            maxModelCalls = 4,
            turnTimeoutMillis = 20_000,
        )

        val events = runtime.run(
            request(
                toolSources = listOf(
                    RecordingTool("alpha", toolOrder, "result:alpha " + "x".repeat(2_000)),
                    RecordingTool("beta", toolOrder),
                ),
            ),
        ).toList()

        assertEquals(listOf("alpha", "beta"), toolOrder)
        assertEquals(4, normalRequests.size)
        assertEquals(1, summaryRequests.size)
        val rejectedRequest = normalRequests[2]
        val retryRequest = normalRequests[3]
        val localEstimate = ConservativeContextTokenCounter().estimate(rejectedRequest.messages, rejectedRequest.tools)
        assertTrue(localEstimate < budget.compressionTriggerTokens)
        assertTrue(localEstimate < budget.inputLimitTokens)

        val rejectedText = rejectedRequest.messages
            .flatMap { it.content }
            .filterIsInstance<ModelContentPart.Text>()
            .joinToString("\n") { it.value }
        assertTrue(rejectedText.contains("result:alpha"))
        assertTrue(rejectedText.contains("result:beta"))
        assertFalse(rejectedText.contains("Earlier alpha result is summarized."))
        assertTrue(
            summaryRequests.single().messages.flatMap { it.content }
                .filterIsInstance<ModelContentPart.Text>()
                .any { it.value.contains("result:alpha") },
        )

        val retryText = retryRequest.messages
            .flatMap { it.content }
            .filterIsInstance<ModelContentPart.Text>()
            .joinToString("\n") { it.value }
        assertTrue(retryText.contains("Earlier alpha result is summarized."))
        assertFalse(retryText.contains("result:alpha"))
        assertTrue(retryText.contains("result:beta"))
        assertEquals(listOf("id-b"), retryRequest.messages.flatMap { it.toolCalls }.map { it.callId })
        assertEquals(listOf("id-b"), retryRequest.messages.filter { it.role == ModelRole.TOOL }.map { it.toolCallId })
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
    }

    @Test
    fun thrownToolFailureIsReturnedAndModelCanContinue() = runTest {
        val gateway = FakeGateway { call, _ ->
            if (call == 1) flow {
                emit(ModelStreamEvent.Completed(ModelFinishReason.TOOL_CALLS, listOf(ModelToolCall("id-fail", "broken", "{}"))))
            } else flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) }
        }
        val broken = object : ToolSource {
            override val definition = agentTestTool("broken")
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult = error("private source detail")
        }

        val events = runtime(gateway).run(request(toolSources = listOf(broken))).toList()
        val result = gateway.requests[1].messages.single { it.role == ModelRole.TOOL }

        assertEquals("id-fail", result.toolCallId)
        assertEquals("Tool execution failed.", (result.content.single() as ModelContentPart.Text).value)
        assertTrue(events.any { it is AgentEvent.AssistantFeedback && it.text.contains("Tool execution failed") })
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
    }

    @Test
    fun toolLimitStopsBeforeExecutingExcessCalls() = runTest {
        val gateway = FakeGateway { _, _ ->
            flow {
                emit(ModelStreamEvent.Completed(ModelFinishReason.TOOL_CALLS, listOf(ModelToolCall("id-a", "alpha", "{}"))))
            }
        }
        val order = mutableListOf<String>()

        val events = runtime(gateway, maxToolCalls = 0)
            .run(request(toolSources = listOf(RecordingTool("alpha", order))))
            .toList()
        val finish = events.last() as AgentEvent.Finished

        assertTrue(order.isEmpty())
        assertEquals(1, gateway.requests.size)
        assertEquals(AgentFailureKind.TOOL_LIMIT, finish.failure!!.kind)
    }

    @Test
    fun visionFallbackDescribesImageThenSendsTextToPrimary() = runTest {
        val gateway = FakeGateway { call, _ ->
            if (call == 1) flow {
                emit(ModelStreamEvent.TextDelta("A blue boat on a lake."))
                emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
            } else flow {
                emit(ModelStreamEvent.Completed(ModelFinishReason.STOP))
            }
        }
        val input = request().copy(
            primaryModel = selection("model-1", CapabilityStatus.UNSUPPORTED),
            visionModel = selection("vision", CapabilityStatus.SUPPORTED),
            currentInput = ModelMessage(
                ModelRole.USER,
                listOf(
                    ModelContentPart.Text("What is in this picture?"),
                    ModelContentPart.Image(byteArrayOf(1, 2, 3), "image/png"),
                ),
            ),
        )

        val events = runtime(gateway).run(input).toList()
        val visionRequest = gateway.requests[0]
        val primaryRequest = gateway.requests[1]
        val primaryUserText = primaryRequest.messages.last().content.filterIsInstance<ModelContentPart.Text>().joinToString { it.value }

        assertEquals("vision", visionRequest.model.modelId)
        assertTrue(visionRequest.messages.flatMap { it.content }.any { it is ModelContentPart.Image })
        assertEquals("model-1", primaryRequest.model.modelId)
        assertFalse(primaryRequest.messages.flatMap { it.content }.any { it is ModelContentPart.Image })
        assertTrue(primaryUserText.contains("What is in this picture?"))
        assertTrue(primaryUserText.contains("A blue boat on a lake."))
        assertEquals(AgentTerminalStatus.COMPLETED, (events.last() as AgentEvent.Finished).status)
    }

    @Test
    fun unknownPrimaryVisionCapabilityStopsBeforeCallingModel() = runTest {
        val gateway = FakeGateway { _, _ -> flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) } }
        val input = request().copy(
            primaryModel = selection("model-1", CapabilityStatus.UNKNOWN),
            currentInput = ModelMessage(
                ModelRole.USER,
                listOf(ModelContentPart.Image(byteArrayOf(1), "image/png")),
            ),
        )

        val events = runtime(gateway).run(input).toList()
        val finish = events.last() as AgentEvent.Finished

        assertEquals(0, gateway.requests.size)
        assertEquals(AgentFailureKind.VISION_CAPABILITY_UNKNOWN, finish.failure!!.kind)
    }

    @Test
    fun apiRejectedAuthenticationStatusMapsToAuthenticationFailure() = runTest {
        val gateway = FakeGateway { _, _ ->
            flow { throw ModelRequestException.ApiRejected("unauthorized", "request rejected", httpStatusCode = 401) }
        }

        val events = runtime(gateway).run(request()).toList()

        assertEquals(AgentFailureKind.AUTHENTICATION, (events.last() as AgentEvent.Finished).failure!!.kind)
    }

    @Test
    fun supportedPrimaryReceivesImageDirectly() = runTest {
        val gateway = FakeGateway { _, _ -> flow { emit(ModelStreamEvent.Completed(ModelFinishReason.STOP)) } }
        val input = request().copy(
            currentInput = ModelMessage(
                ModelRole.USER,
                listOf(ModelContentPart.Text("Look at this"), ModelContentPart.Image(byteArrayOf(7), "image/jpeg")),
            ),
        )

        runtime(gateway).run(input).toList()

        assertEquals(1, gateway.requests.size)
        assertTrue(gateway.requests.single().messages.flatMap { it.content }.any { it is ModelContentPart.Image })
    }

    @Test
    fun callerCancellationPropagatesWithoutFinishedEvent() = runTest {
        val gateway = FakeGateway { _, _ -> flow { awaitCancellation() } }
        val job = launch { runtime(gateway).run(request()).collect { } }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    private fun runtime(gateway: FakeGateway, maxModelCalls: Int = 8, maxToolCalls: Int = 16) = ReActAgentRuntime(
        modelGateway = gateway,
        contextCompiler = ContextCompiler(
            historyReader = EmptyHistoryReader,
            summaryStore = EmptySummaryStore,
            modelGateway = gateway,
            budgetProvider = DefaultModelContextBudgetProvider(ContextBudget()),
        ),
        historyReader = EmptyHistoryReader,
        maxModelCalls = maxModelCalls,
        maxToolCalls = maxToolCalls,
        turnTimeoutMillis = 20_000,
    )

    private fun request(toolSources: List<ToolSource> = emptyList()) = AgentRequest(
        sessionId = "session-1",
        executionId = "execution-1",
        attemptId = "attempt-1",
        turnSequence = 10,
        historyUpperBoundExclusive = 10,
        persona = PersonaPrompt("persona-1", "Persona", "Be helpful."),
        primaryModel = selection("model-1", CapabilityStatus.SUPPORTED),
        currentInput = ModelMessage(ModelRole.USER, listOf(ModelContentPart.Text("Question?"))),
        toolScope = ToolScope.PERSONA_SESSION,
        toolSources = toolSources,
    )

    private fun selection(modelId: String, vision: CapabilityStatus) = ModelSelection(
        ModelReference("service-1", modelId),
        ModelCapabilityReport(
            ModelReference("service-1", modelId),
            availability = CapabilityStatus.SUPPORTED,
            vision = vision,
            nativeStreaming = CapabilityStatus.SUPPORTED,
            structuredToolCalls = CapabilityStatus.SUPPORTED,
        ),
    )

    private class RecordingTool(
        name: String,
        private val order: MutableList<String>,
        private val result: String = "result:$name",
    ) : ToolSource {
        override val definition = agentTestTool(name)

        override suspend fun execute(
            call: ModelToolCall,
            context: ToolExecutionContext,
            onProgress: suspend (ToolProgress) -> Unit,
        ): ToolExecutionResult {
            order += call.name
            onProgress(ToolProgress("Started ${call.name}"))
            return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, result)
        }
    }

    private class FakeGateway(
        private val answer: (Int, ModelRequest) -> Flow<ModelStreamEvent>,
    ) : ModelGateway {
        val requests = mutableListOf<ModelRequest>()

        override suspend fun listModels(serviceId: String): List<String> = emptyList()

        override suspend fun probe(model: ModelReference): ModelCapabilityReport = error("Not used")

        override fun stream(request: ModelRequest): Flow<ModelStreamEvent> {
            requests += request
            return answer(requests.size, request)
        }
    }

    private object EmptyHistoryReader : HistoryReader {
        override suspend fun read(request: HistoryReadRequest) = HistoryReadPage(emptyList(), null, false)
    }

    private object EmptySummaryStore : HistorySummaryStore {
        override suspend fun read(sessionId: String, upperBoundExclusive: Long): List<HistorySummary> = emptyList()

        override suspend fun commit(candidate: HistorySummaryCandidate) = SummaryCommitResult.RejectedStaleCoverage
    }
}

private fun agentTestTool(name: String) = ToolDefinition(
    id = "test.$name",
    name = name,
    description = "Test tool",
    parametersJsonSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
    scopes = setOf(ToolScope.PERSONA_SESSION, ToolScope.MAIN_SESSION),
)
