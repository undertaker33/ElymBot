package com.elymbot.core.agent

import com.elymbot.core.context.ContextCompiler
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentMessage
import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.HistoryReadPage
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.HistorySummary
import com.elymbot.core.contract.HistorySummaryCandidate
import com.elymbot.core.contract.HistorySummaryStore
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelSelection
import com.elymbot.core.contract.MediaResourceReference
import com.elymbot.core.contract.MessageReference
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.ReplyMessage
import com.elymbot.core.contract.ReplySubmission
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.ReplySubmitter
import com.elymbot.core.contract.SummaryCommitResult
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import com.elymbot.core.model.ModelServiceConnection
import com.elymbot.core.model.ModelServiceConnectionProvider
import com.elymbot.core.model.OpenAiCompatibleModelGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReplyRuntimeMockWebServerTest {
    @Test
    fun durableReplyCallsAreOrderedAndTheRuntimeContinuesWithoutExposingRawText() = runBlocking {
        Fixture().use { fixture ->
            fixture.answer(completion("private draft", call("reused-id", "reply", replyArguments("first" to 0, "second" to 40))))
            fixture.answer(completion(null, call("inspect-call", "inspect", "{}")))
            fixture.answer(completion(null, call("reused-id", "reply", replyArguments("last" to 50))))
            fixture.answer(completion("ordinary private final"))

            val events = fixture.run()
            val requests = List(4) { fixture.takeRequest() }

            assertEquals(listOf("inspect"), fixture.executedTools)
            assertEquals(2, fixture.submissions.size)
            assertEquals(listOf("first", "second"), fixture.submissions[0].messages.map(ReplyMessage::text))
            assertEquals(listOf(0L, 40L), fixture.submissions[0].messages.map(ReplyMessage::suggestedDelayMillis))
            assertEquals(listOf("last"), fixture.submissions[1].messages.map(ReplyMessage::text))
            assertEquals(0, fixture.submissions[0].context.toolStepOrdinal)
            assertEquals(0, fixture.submissions[0].context.toolCallOrdinal)
            assertEquals("reused-id", fixture.submissions[0].context.toolCallId)
            assertEquals(2, fixture.submissions[1].context.toolStepOrdinal)
            assertEquals(0, fixture.submissions[1].context.toolCallOrdinal)
            assertEquals("reused-id", fixture.submissions[1].context.toolCallId)
            assertTrue(events.filterIsInstance<AgentEvent.Finished>().single().status == AgentTerminalStatus.COMPLETED)
            assertFalse(events.any { it is AgentEvent.TextDelta || it is AgentEvent.AssistantFeedback })
            requests.forEach { request ->
                assertEquals("false", request.getValue("stream").jsonPrimitive.content)
                assertTrue(request.getValue("tools").jsonArray.any { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content == "reply" })
                assertTrue(request.getValue("tools").jsonArray.any { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content == "HistoryRead" })
            }
            assertEquals(emptyList<String>(), toolIds(requests[0]))
            assertEquals(listOf("reused-id"), toolIds(requests[1]))
            assertEquals(listOf("reused-id", "inspect-call"), toolIds(requests[2]))
            assertEquals(listOf("reused-id", "inspect-call", "reused-id"), toolIds(requests[3]))
            assertEquals(
                listOf("Reply accepted for durable delivery (2 messages)."),
                toolResults(requests[1]).map { it.second },
            )
            assertEquals(
                listOf("Reply accepted for durable delivery (2 messages).", "private inspect result"),
                toolResults(requests[2]).map { it.second },
            )
            assertEquals(
                listOf("Reply accepted for durable delivery (2 messages).", "private inspect result", "Reply accepted for durable delivery (1 message)."),
                toolResults(requests[3]).map { it.second },
            )
            assertEquals("execution-1", fixture.submissions[0].context.executionId)
            assertEquals("attempt-1", fixture.submissions[0].context.attemptId)
            assertEquals(10L, fixture.submissions[0].context.turnSequence)
        }
    }

    @Test
    fun emptyStopIsExplicitSilenceButOrdinaryTextWithoutReplyIsControlledFailure() = runBlocking {
        Fixture().use { silent ->
            silent.answer(completion(null))
            val events = silent.run()
            val finished = events.filterIsInstance<AgentEvent.Finished>().single()
            assertEquals(AgentTerminalStatus.COMPLETED, finished.status)
            assertTrue(finished.successfulSilence)
            assertFalse(events.any { it is AgentEvent.TextDelta || it is AgentEvent.AssistantFeedback })
            assertEquals("false", silent.takeRequest().getValue("stream").jsonPrimitive.content)
        }

        Fixture().use { plainText ->
            plainText.answer(completion("ordinary response"))
            plainText.answer(completion("still ordinary response"))
            val events = plainText.run()
            val finished = events.filterIsInstance<AgentEvent.Finished>().single()
            val requests = List(2) { plainText.takeRequest() }
            assertEquals(AgentTerminalStatus.FAILED, finished.status)
            assertFalse(finished.successfulSilence)
            assertEquals(com.elymbot.core.contract.AgentFailureKind.PROTOCOL, finished.failure?.kind)
            assertFalse(events.any { it is AgentEvent.TextDelta || it is AgentEvent.AssistantFeedback })
            assertTrue(requests[1].toString().contains("For this turn, use the language"))
            assertFalse(requests[1].toString().contains("ordinary response"))
            assertEquals(null, withContext(Dispatchers.IO) { plainText.server.takeRequest(100, TimeUnit.MILLISECONDS) })
        }
        Unit
    }

    @Test
    fun rejectedReplyDoesNotExposeRawToolFeedbackAndDoesNotCountAsSilence() = runBlocking {
        Fixture(replyResult = ReplySubmissionResult.Rejected(com.elymbot.core.contract.ReplyRejectionCode.NOT_AUTHORIZED)).use { fixture ->
            fixture.answer(completion(null, call("reply-1", "reply", replyArguments("not queued" to 0))))
            fixture.answer(completion(null))

            val events = fixture.run()
            val finished = events.filterIsInstance<AgentEvent.Finished>().single()
            val requests = List(2) { fixture.takeRequest() }

            assertEquals(1, fixture.submissions.size)
            assertEquals(AgentTerminalStatus.FAILED, finished.status)
            assertEquals(com.elymbot.core.contract.AgentFailureKind.PROTOCOL, finished.failure?.kind)
            assertFalse(finished.successfulSilence)
            assertFalse(events.any { it is AgentEvent.TextDelta || it is AgentEvent.AssistantFeedback })
            val toolResult = requests[1].getValue("messages").jsonArray
                .single { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }
            assertEquals("Reply was not accepted: NOT_AUTHORIZED.", toolResult.jsonObject.getValue("content").jsonPrimitive.content)
        }
    }

    @Test
    fun normalizedFiveSegmentInputProjectsOnlyTextAndSupportedImageToModel() = runBlocking {
        Fixture().use { fixture ->
            fixture.answer(completion(null))
            val base = fixture.request()
            val origin = MessageRoute("qq", "private-1", accountId = "account-1", actorId = "user-1")
            val modelRequest = base.copy(
                primaryModel = base.primaryModel.copy(
                    capabilities = base.primaryModel.capabilities.copy(vision = CapabilityStatus.SUPPORTED),
                ),
                currentInput = ModelMessage(
                    ModelRole.USER,
                    listOf(
                        ModelContentPart.Text("Describe this picture."),
                        ModelContentPart.Image(byteArrayOf(1, 2, 3), "image/png"),
                    ),
                ),
                inputMessage = AgentMessage(
                    messageId = "qq-message-1",
                    origin = origin,
                    target = MessageRoute("qq", "private-1", accountId = "account-1"),
                    reference = MessageReference(origin, "qq-parent-message"),
                    segments = listOf(
                        AgentMessageSegment.Text("Describe this picture."),
                        AgentMessageSegment.Image(MediaResourceReference("image-ref", "image/png")),
                        AgentMessageSegment.Audio(MediaResourceReference("audio-ref", "audio/ogg")),
                        AgentMessageSegment.Video(MediaResourceReference("video-ref", "video/mp4")),
                        AgentMessageSegment.File(MediaResourceReference("file-ref", "application/pdf", "report.pdf")),
                    ),
                ),
            )

            val events = fixture.runtime.run(modelRequest).toList()
            val body = fixture.takeRequest()
            val userContent = body.getValue("messages").jsonArray
                .single { it.jsonObject["role"]?.jsonPrimitive?.content == "user" }
                .jsonObject.getValue("content").jsonArray

            assertEquals(AgentTerminalStatus.COMPLETED, events.filterIsInstance<AgentEvent.Finished>().single().status)
            assertEquals(2, userContent.size)
            assertEquals("Describe this picture.", userContent[0].jsonObject.getValue("text").jsonPrimitive.content)
            val imageUrl = userContent[1].jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
            assertTrue(imageUrl.startsWith("data:image/png;base64,"))
            assertTrue(imageUrl.endsWith("AQID"))
            val encodedBody = body.toString()
            assertFalse(encodedBody.contains("audio-ref"))
            assertFalse(encodedBody.contains("video-ref"))
            assertFalse(encodedBody.contains("file-ref"))
        }
    }

    @Test
    fun unsupportedOrUnknownStructuredCallsFailBeforeMakingARequest() = runBlocking {
        for (capability in listOf(CapabilityStatus.UNKNOWN, CapabilityStatus.UNSUPPORTED)) {
            Fixture().use { fixture ->
                val events = fixture.run(capability)
                val finished = events.filterIsInstance<AgentEvent.Finished>().single()
                assertEquals(AgentTerminalStatus.FAILED, finished.status)
                assertFalse(finished.successfulSilence)
                assertTrue(events.none { it is AgentEvent.TextDelta || it is AgentEvent.AssistantFeedback })
                assertEquals(null, withContext(Dispatchers.IO) { fixture.server.takeRequest(100, TimeUnit.MILLISECONDS) })
            }
        }
    }

    @Test
    fun cancellationDuringNonStreamingResponsePreventsReplySubmission() = runBlocking {
        Fixture().use { fixture ->
            fixture.answer(completion(null, call("late", "reply", replyArguments("late" to 0))), delayMillis = 1_200)
            val events = mutableListOf<AgentEvent>()
            val caller = launch { fixture.runtime.run(fixture.request()).toList(events) }
            withContext(Dispatchers.IO) { fixture.takeRequest() }

            caller.cancelAndJoin()

            assertTrue(caller.isCancelled)
            assertTrue(fixture.submissions.isEmpty())
            assertTrue(events.none { it is AgentEvent.Finished })
        }
    }

    private class Fixture(
        private val replyResult: ReplySubmissionResult = ReplySubmissionResult.Accepted(listOf("stored-message")),
    ) : AutoCloseable {
        val server = MockWebServer().also { it.start() }
        val submissions = mutableListOf<ReplySubmission>()
        val executedTools = mutableListOf<String>()
        private val gateway = OpenAiCompatibleModelGateway(
            connectionProvider = ModelServiceConnectionProvider {
                ModelServiceConnection(server.url("/v1").toString(), "synthetic-test-credential")
            },
        )
        val runtime = ReActAgentRuntime(
            gateway,
            ContextCompiler(modelGateway = gateway, historyReader = EmptyHistory, summaryStore = EmptySummaries),
            EmptyHistory,
        )

        fun request(capability: CapabilityStatus = CapabilityStatus.SUPPORTED): AgentRequest {
            val model = ModelReference("service-1", "fixture-model")
            return AgentRequest(
                sessionId = "session-1",
                executionId = "execution-1",
                attemptId = "attempt-1",
                turnSequence = 10,
                historyUpperBoundExclusive = 10,
                persona = PersonaPrompt("persona-1", "Persona", "Synthetic test."),
                primaryModel = ModelSelection(
                    model,
                    ModelCapabilityReport(
                        model,
                        availability = CapabilityStatus.SUPPORTED,
                        vision = CapabilityStatus.UNKNOWN,
                        nativeStreaming = CapabilityStatus.SUPPORTED,
                        structuredToolCalls = capability,
                    ),
                ),
                currentInput = ModelMessage(ModelRole.USER, listOf(ModelContentPart.Text("synthetic input"))),
                toolScope = ToolScope.MAIN_SESSION,
                toolSources = listOf(InspectTool()),
                replySubmitter = ReplySubmitter { submission ->
                    submissions += submission
                    replyResult
                },
            )
        }

        suspend fun run(capability: CapabilityStatus = CapabilityStatus.SUPPORTED) = runtime.run(request(capability)).toList()

        fun answer(body: String, delayMillis: Long = 0) {
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "application/json")
                    .body(body)
                    .bodyDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .build(),
            )
        }

        fun takeRequest(): JsonObject {
            val recorded = checkNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertEquals("/v1/chat/completions", recorded.url.encodedPath)
            return Json.parseToJsonElement(checkNotNull(recorded.body).utf8()).jsonObject
        }

        override fun close() = server.close()

        private inner class InspectTool : ToolSource {
            override val definition = ToolDefinition(
                id = "test.inspect",
                name = "inspect",
                description = "Synthetic ordinary tool",
                parametersJsonSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
                scopes = setOf(ToolScope.MAIN_SESSION),
            )

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                executedTools += call.name
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "private inspect result")
            }
        }
    }

    private object EmptyHistory : HistoryReader {
        override suspend fun read(request: HistoryReadRequest) = HistoryReadPage(emptyList(), null, false)
    }

    private object EmptySummaries : HistorySummaryStore {
        override suspend fun read(sessionId: String, upperBoundExclusive: Long): List<HistorySummary> = emptyList()

        override suspend fun commit(candidate: HistorySummaryCandidate) = SummaryCommitResult.RejectedStaleCoverage
    }

    private fun toolIds(request: JsonObject) = request.getValue("messages").jsonArray
        .map { it.jsonObject }
        .filter { it["role"]?.jsonPrimitive?.content == "tool" }
        .map { it.getValue("tool_call_id").jsonPrimitive.content }

    private fun toolResults(request: JsonObject) = request.getValue("messages").jsonArray
        .map { it.jsonObject }
        .filter { it["role"]?.jsonPrimitive?.content == "tool" }
        .map { it.getValue("tool_call_id").jsonPrimitive.content to it.getValue("content").jsonPrimitive.content }

    private fun replyArguments(vararg messages: Pair<String, Int>) = buildJsonObject {
        put("messages", buildJsonArray {
            messages.forEach { (text, delayMillis) ->
                add(buildJsonObject {
                    put("text", text)
                    put("delayMillis", delayMillis)
                })
            }
        })
    }.toString()

    private fun call(id: String, name: String, arguments: String) = buildJsonObject {
        put("id", id)
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        })
    }

    private fun completion(content: String?, vararg calls: JsonObject) = buildJsonObject {
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("index", 0)
                put("finish_reason", if (calls.isEmpty()) "stop" else "tool_calls")
                put("message", buildJsonObject {
                    put("role", "assistant")
                    if (content == null) put("content", JsonNull) else put("content", content)
                    if (calls.isNotEmpty()) put("tool_calls", buildJsonArray { calls.forEach { add(it) } })
                })
            })
        })
    }.toString()
}
