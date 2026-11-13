package com.elymbot.core.model

import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelProbeFailureKind
import com.elymbot.core.contract.ModelProbeTarget
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ModelToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleModelGatewayTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listModelsUsesBearerAuthenticationAndDeduplicatesIds() = runBlocking {
        server.enqueue(jsonResponse("""{"data":[{"id":"gpt-a"},{"id":"gpt-a"},{"id":"  "},{"name":"missing-id"},{"id":"gpt-b"}]}"""))

        val models = gateway().listModels(SERVICE_ID)

        assertEquals(listOf("gpt-a", "gpt-b"), models)
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("GET", request!!.method)
        assertEquals("/v1/models", request.url.encodedPath)
        assertEquals("Bearer " + API_KEY, request.headers["Authorization"])
        assertFalse(request.body?.utf8().orEmpty().contains(API_KEY))
    }

    @Test
    fun ordinaryResponsePreservesWhitespaceAndSerializesImagesAndToolHistory() = runBlocking {
        server.enqueue(jsonResponse(completionJson("  done\n ", "stop")))
        val request = ModelRequest(
            model = MODEL,
            messages = listOf(
                ModelMessage(ModelRole.SYSTEM, listOf(ModelContentPart.Text("Be concise."))),
                ModelMessage(
                    ModelRole.USER,
                    listOf(ModelContentPart.Text("Describe this: "), ModelContentPart.Image(byteArrayOf(1, 2, 3), "image/png")),
                ),
                ModelMessage(
                    ModelRole.ASSISTANT,
                    content = emptyList(),
                    toolCalls = listOf(ModelToolCall("call-1", "lookup", "{\"q\":\"x\"}")),
                ),
                ModelMessage(ModelRole.TOOL, listOf(ModelContentPart.Text("found")), toolCallId = "call-1"),
            ),
            tools = listOf(ModelToolDefinition("lookup", "Search", """{"type":"object","properties":{"q":{"type":"string"}}}""")),
            useNativeStreaming = false,
            maxOutputTokens = 321,
        )

        val events = gateway().stream(request).toListBlocking()

        assertEquals(
            listOf(
                ModelStreamEvent.TextDelta("  done\n "),
                ModelStreamEvent.Completed(ModelFinishReason.STOP),
            ),
            events,
        )
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer " + API_KEY, recorded.headers["Authorization"])
        val body = Json.parseToJsonElement(recorded.body?.utf8().orEmpty()).jsonObject
        assertEquals("model-a", body.getValue("model").jsonPrimitive.content)
        assertEquals(false, body.getValue("stream").jsonPrimitive.content.toBoolean())
        assertEquals(321, body.getValue("max_completion_tokens").jsonPrimitive.content.toInt())
        val messages = body.getValue("messages").jsonArray
        val imageParts = messages[1].jsonObject.getValue("content").jsonArray
        val imageUrl = imageParts[1].jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
        assertTrue(imageUrl.startsWith("data:image/png;base64,"))
        assertTrue(imageUrl.endsWith("AQID"))
        val assistantCall = messages[2].jsonObject.getValue("tool_calls").jsonArray.single().jsonObject
        assertEquals("call-1", assistantCall.getValue("id").jsonPrimitive.content)
        assertEquals("{\"q\":\"x\"}", assistantCall.getValue("function").jsonObject.getValue("arguments").jsonPrimitive.content)
        assertEquals("call-1", messages[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertFalse(recorded.body?.utf8().orEmpty().contains(API_KEY))
    }

    @Test
    fun sseTextArrivesBeforeTerminalAndKeepsUtf8AndWhitespace() = runBlocking {
        val firstFrame = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\" 你好\\n\"},\"finish_reason\":null}]}\r\n\r\n"
        val finalFrames = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\r\n\r\ndata: [DONE]\r\n\r\n"
        val fullBody = firstFrame + finalFrames
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .body(fullBody)
                .throttleBody(64, 250, TimeUnit.MILLISECONDS)
                .build(),
        )
        val firstEvent = CompletableDeferred<ModelStreamEvent>()
        val completed = CompletableDeferred<ModelStreamEvent.Completed>()
        val job = launch {
            gateway().stream(streamRequest()).collect { event ->
                if (event is ModelStreamEvent.TextDelta) firstEvent.complete(event)
                if (event is ModelStreamEvent.Completed) completed.complete(event)
            }
        }

        assertEquals(ModelStreamEvent.TextDelta(" 你好\n"), withTimeout(2_000) { firstEvent.await() })
        assertFalse("The terminal event must follow the first text delta.", completed.isCompleted)
        assertEquals(ModelFinishReason.STOP, withTimeout(3_000) { completed.await() }.finishReason)
        job.join()
    }

    @Test
    fun sseCombinesDataLinesAndDecodesUtf8SplitAcrossBytes() = runBlocking {
        val body = buildString {
            append(": keepalive\r\n\r\n")
            append("data: {\"choices\":\r\n")
            append("data: [{\"index\":0,\"delta\":{\"content\":\"天空\"},\"finish_reason\":null}]}\r\n\r\n")
            append(sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""))
            append(sseEvent("[DONE]"))
        }
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .chunkedBody(body, 1)
                .build(),
        )

        val events = gateway().stream(streamRequest()).toListBlocking()

        assertEquals(
            listOf(
                ModelStreamEvent.TextDelta("天空"),
                ModelStreamEvent.Completed(ModelFinishReason.STOP),
            ),
            events,
        )
    }

    @Test
    fun streamAssemblesInterleavedToolCallChunksBeforeEmittingTerminalBatch() = runBlocking {
        val body = buildString {
            append(sseEvent("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call-b","type":"function","function":{"name":"ca","arguments":"{\"b\":"}},{"index":0,"id":"call-a","type":"function","function":{"name":"look","arguments":"{\"a\":\""}}]},"finish_reason":null}]}"""))
            append(sseEvent("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"up","arguments":"x\"}"}},{"index":1,"function":{"name":"lc","arguments":"2}"}}]},"finish_reason":null}]}"""))
            append(sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}"""))
            append(sseEvent("[DONE]"))
        }
        server.enqueue(sseResponse(body))

        val events = gateway().stream(streamRequest()).toListBlocking()

        val completed = events.single() as ModelStreamEvent.Completed
        assertEquals(ModelFinishReason.TOOL_CALLS, completed.finishReason)
        assertEquals(
            listOf(
                ModelToolCall("call-a", "lookup", "{\"a\":\"x\"}"),
                ModelToolCall("call-b", "calc", "{\"b\":2}"),
            ),
            completed.toolCalls,
        )
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("true", Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject.getValue("stream").jsonPrimitive.content)
    }

    @Test
    fun incompleteToolCallBatchIsRejectedWithoutTerminalEvent() = runBlocking {
        val body = sseEvent(
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"type":"function","function":{"name":"lookup","arguments":"{}"}}]},"finish_reason":null}]}""",
        ) + sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""") + sseEvent("[DONE]")
        server.enqueue(sseResponse(body))
        val events = ArrayList<ModelStreamEvent>()

        val error = try {
            gateway().stream(streamRequest()).collect { events += it }
            throw AssertionError("Expected an invalid tool-call batch to fail.")
        } catch (failure: ModelRequestException.Protocol) {
            failure
        }

        assertTrue(error.message.orEmpty().contains("missing its ID"))
        assertTrue(events.none { it is ModelStreamEvent.Completed })
    }

    @Test
    fun incompleteToolCallWithoutNameIsRejectedWithoutTerminalEvent() = runBlocking {
        val body = sseEvent(
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"arguments":"{}"}}]},"finish_reason":null}]}""",
        ) + sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""") + sseEvent("[DONE]")
        server.enqueue(sseResponse(body))
        val events = ArrayList<ModelStreamEvent>()

        val error = try {
            gateway().stream(streamRequest()).collect { events += it }
            throw AssertionError("Expected a tool call missing its name to fail.")
        } catch (failure: ModelRequestException.Protocol) {
            failure
        }

        assertTrue(error.message.orEmpty().contains("missing its name"))
        assertTrue(events.none { it is ModelStreamEvent.Completed })
    }

    @Test
    fun completeToolCallsPreserveInvalidArgumentsForCallIdBoundValidation() = runBlocking {
        val cases = listOf(
            "" to "",
            ",\"arguments\":\"{\\\"q\\\":\"" to "{\"q\":",
            ",\"arguments\":\"[]\"" to "[]",
            ",\"arguments\":[]" to "[]",
        )

        cases.forEachIndexed { index, (argumentField, expectedArguments) ->
            val callId = "call-$index"
            val body = sseEvent(
                """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"$callId","type":"function","function":{"name":"lookup"$argumentField}}]},"finish_reason":null}]}""",
            ) + sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""") + sseEvent("[DONE]")
            server.enqueue(sseResponse(body))

            val events = gateway().stream(streamRequest()).toListBlocking()
            val completed = events.single() as ModelStreamEvent.Completed
            val call = completed.toolCalls.single()

            assertEquals(ModelFinishReason.TOOL_CALLS, completed.finishReason)
            assertEquals(callId, call.callId)
            assertEquals("lookup", call.name)
            assertEquals(expectedArguments, call.argumentsJson)
        }
    }

    @Test
    fun toolCallBatchWithoutTerminalMarkerIsInterruptedAndNotDelivered() = runBlocking {
        val body = sseEvent(
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{"}}]},"finish_reason":null}]}""",
        )
        server.enqueue(sseResponse(body))
        val events = ArrayList<ModelStreamEvent>()

        val error = try {
            gateway().stream(streamRequest()).collect { events += it }
            throw AssertionError("Expected an unterminated tool-call batch to fail.")
        } catch (failure: ModelRequestException.StreamInterrupted) {
            failure
        }

        assertTrue(error.message.orEmpty().contains("terminal"))
        assertTrue(events.none { it is ModelStreamEvent.Completed })
    }

    @Test
    fun ordinaryToolCallsPreserveInvalidArgumentsForCallIdBoundValidation() = runBlocking {
        val cases = listOf(
            "" to "",
            ",\"arguments\":\"{\\\"q\\\":\"" to "{\"q\":",
            ",\"arguments\":\"[]\"" to "[]",
            ",\"arguments\":[]" to "[]",
        )

        cases.forEachIndexed { index, (argumentField, expectedArguments) ->
            val callId = "ordinary-$index"
            val body = """{"choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"$callId","type":"function","function":{"name":"lookup"$argumentField}}]},"finish_reason":"tool_calls"}]}"""
            server.enqueue(jsonResponse(body))

            val events = gateway().stream(nonStreamingRequest()).toListBlocking()
            val completed = events.single() as ModelStreamEvent.Completed

            assertEquals(ModelFinishReason.TOOL_CALLS, completed.finishReason)
            assertEquals(callId, completed.toolCalls.single().callId)
            assertEquals(expectedArguments, completed.toolCalls.single().argumentsJson)
        }
    }

    @Test
    fun nonStreamingOutputLimitDropsIncompleteToolCalls() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"choices":[{"index":0,"message":{"role":"assistant","content":"partial","tool_calls":[{"id":"call-1","type":"function","function":{"name":"lookup"}}]},"finish_reason":"length"}]}""",
            ),
        )

        val events = gateway().stream(nonStreamingRequest()).toListBlocking()

        assertEquals(ModelStreamEvent.TextDelta("partial"), events.first())
        val completed = events.last() as ModelStreamEvent.Completed
        assertEquals(ModelFinishReason.OUTPUT_LIMIT, completed.finishReason)
        assertTrue(completed.toolCalls.isEmpty())
    }

    @Test
    fun outputLimitPreservesPartialTextAndDropsToolCalls() = runBlocking {
        val body = sseEvent(
            """{"choices":[{"index":0,"delta":{"content":"partial","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{\"q\":"}}]},"finish_reason":null}]}""",
        ) + sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""") + sseEvent("[DONE]")
        server.enqueue(sseResponse(body))

        val events = gateway().stream(streamRequest()).toListBlocking()

        assertEquals(ModelStreamEvent.TextDelta("partial"), events.first())
        val completed = events.last() as ModelStreamEvent.Completed
        assertEquals(ModelFinishReason.OUTPUT_LIMIT, completed.finishReason)
        assertTrue(completed.toolCalls.isEmpty())
    }

    @Test
    fun earlyEofIsAStreamInterruption() {
        server.enqueue(sseResponse(sseEvent("""{"choices":[{"index":0,"delta":{"content":"partial"},"finish_reason":null}]}""")))

        val exception = org.junit.Assert.assertThrows(ModelRequestException.StreamInterrupted::class.java) {
            gateway().stream(streamRequest()).toListBlocking()
        }

        assertTrue(exception.message.orEmpty().contains("terminal"))
    }

    @Test
    fun cancellingAnActiveStreamRemainsCoroutineCancellation() = runBlocking {
        val partial = sseEvent("""{"choices":[{"index":0,"delta":{"content":"partial"},"finish_reason":null}]}""")
        val keepAlive = ": keepalive\n".repeat(100)
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .body(partial + keepAlive + sseEvent("[DONE]"))
                .throttleBody(128, 100, TimeUnit.MILLISECONDS)
                .build(),
        )
        val failure = CompletableDeferred<Throwable?>()
        val firstDelta = CompletableDeferred<Unit>()
        val job = launch {
            try {
                gateway().stream(streamRequest()).collect { event ->
                    if (event is ModelStreamEvent.TextDelta) firstDelta.complete(Unit)
                }
                failure.complete(null)
            } catch (error: Throwable) {
                failure.complete(error)
            }
        }
        withTimeout(2_000) { firstDelta.await() }
        job.cancelAndJoin()

        val thrown = failure.await()
        assertTrue(thrown is CancellationException)
        assertFalse(thrown is ModelRequestException)
    }

    @Test
    fun probesReportEachCapabilityAndSendActualVisionAndToolRequests() = runBlocking {
        server.enqueue(jsonResponse(completionJson("OK", "stop")))
        server.enqueue(jsonResponse(completionJson("red blue green yellow", "stop")))
        server.enqueue(sseResponse(sseEvent("""{"choices":[{"index":0,"delta":{"content":"OK"},"finish_reason":null}]}""") +
            sseEvent("""{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""") + sseEvent("[DONE]")))
        server.enqueue(
            jsonResponse(
                """{"choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"probe-call","type":"function","function":{"name":"capability_probe","arguments":"{\"probe\":\"elymbot\"}"}}]},"finish_reason":"tool_calls"}]}""",
            ),
        )

        val report = gateway().probe(MODEL)

        assertEquals(CapabilityStatus.SUPPORTED, report.availability)
        assertEquals(CapabilityStatus.SUPPORTED, report.vision)
        assertEquals(CapabilityStatus.SUPPORTED, report.nativeStreaming)
        assertEquals(CapabilityStatus.SUPPORTED, report.structuredToolCalls)
        assertTrue(report.failuresByTarget.isEmpty())
        val availability = server.takeRequest(1, TimeUnit.SECONDS)!!
        val vision = server.takeRequest(1, TimeUnit.SECONDS)!!
        val streaming = server.takeRequest(1, TimeUnit.SECONDS)!!
        val tools = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("false", Json.parseToJsonElement(availability.body?.utf8().orEmpty()).jsonObject.getValue("stream").jsonPrimitive.content)
        val visionBody = Json.parseToJsonElement(vision.body?.utf8().orEmpty()).jsonObject
        val imagePart = visionBody.getValue("messages").jsonArray.single().jsonObject.getValue("content").jsonArray[1].jsonObject
        assertTrue(imagePart.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content.startsWith("data:image/png;base64,"))
        assertEquals("text/event-stream, application/json", streaming.headers["Accept"])
        val toolBody = Json.parseToJsonElement(tools.body?.utf8().orEmpty()).jsonObject
        assertEquals("required", toolBody.getValue("tool_choice").jsonPrimitive.content)
        assertEquals("Bearer " + API_KEY, tools.headers["Authorization"])
    }

    @Test
    fun unsupportedStreamingAndAuthenticationFailureStayDistinct() = runBlocking {
        server.enqueue(jsonResponse(completionJson("OK", "stop")))
        server.enqueue(jsonResponse(completionJson("red blue green yellow", "stop")))
        server.enqueue(jsonResponse(completionJson("whole response", "stop")))
        server.enqueue(jsonResponse(completionJson("no tool call", "stop")))

        val report = gateway().probe(MODEL)

        assertEquals(CapabilityStatus.SUPPORTED, report.availability)
        assertEquals(CapabilityStatus.SUPPORTED, report.vision)
        assertEquals(CapabilityStatus.UNSUPPORTED, report.nativeStreaming)
        assertEquals(ModelProbeFailureKind.API_REJECTED, report.failuresByTarget.getValue(ModelProbeTarget.NATIVE_STREAMING).kind)
        repeat(4) { assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }

        data class ProbeErrorCase(
            val status: Int,
            val code: String,
            val type: String,
            val expectedKind: ModelProbeFailureKind,
            val retryable: Boolean,
        )
        val probeErrorCases = listOf(
            ProbeErrorCase(401, "invalid_api_key", "authentication_error", ModelProbeFailureKind.AUTHENTICATION, false),
            ProbeErrorCase(403, "permission_denied", "permission_error", ModelProbeFailureKind.AUTHENTICATION, false),
            ProbeErrorCase(402, "insufficient_quota", "invalid_request_error", ModelProbeFailureKind.HTTP, false),
            ProbeErrorCase(429, "rate_limit_exceeded", "rate_limit_error", ModelProbeFailureKind.HTTP, true),
        )
        for (case in probeErrorCases) {
            server.enqueue(
                jsonResponse(
                    """{"error":{"code":"${case.code}","type":"${case.type}","message":"Provider rejected request."}}""",
                    case.status,
                ),
            )
            val report = gateway().probe(MODEL)
            val failure = report.failuresByTarget.getValue(ModelProbeTarget.AVAILABILITY)

            assertEquals(CapabilityStatus.UNKNOWN, report.availability)
            assertEquals(case.expectedKind, failure.kind)
            assertEquals(case.status, failure.httpStatusCode)
            assertEquals(case.retryable, failure.retryable)
            assertEquals(CapabilityStatus.UNKNOWN, report.vision)
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun providerErrorCodesAreStableAndMessagesRedactCredentials() {
        server.enqueue(
            jsonResponse(
                """{"error":{"code":"$API_KEY/provider-private-code","type":"permission_error","message":"Rejected credential $API_KEY."}}""",
                403,
            ),
        )

        val failure = org.junit.Assert.assertThrows(ModelRequestException.ApiRejected::class.java) {
            gateway().stream(nonStreamingRequest()).toListBlocking()
        }

        assertEquals("permission_denied", failure.code)
        assertEquals(403, failure.httpStatusCode)
        assertFalse(failure.message.orEmpty().contains(API_KEY))
    }

    @Test
    fun connectionFailureLeavesAvailabilityUnknown() = runBlocking {
        val unavailablePort = java.net.ServerSocket(0).use { it.localPort }
        val gateway = OpenAiCompatibleModelGateway(
            connectionProvider = ModelServiceConnectionProvider { ModelServiceConnection("http://127.0.0.1:$unavailablePort/v1", null) },
            probeTimeoutMillis = 2_000,
        )

        val report = gateway.probe(MODEL)

        assertEquals(CapabilityStatus.UNKNOWN, report.availability)
        assertEquals(CapabilityStatus.UNKNOWN, report.vision)
        assertEquals(ModelProbeFailureKind.NETWORK, report.failuresByTarget.getValue(ModelProbeTarget.AVAILABILITY).kind)
    }

    @Test
    fun contextLimitAndContentFilterHaveTypedFailureSemantics() {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error":{"type":"invalid_request_error","code":"context_length_exceeded","message":"Maximum context length exceeded."}}""")
                .build(),
        )
        val contextLimit = org.junit.Assert.assertThrows(ModelRequestException.ContextLimit::class.java) {
            gateway().stream(nonStreamingRequest()).toListBlocking()
        }
        assertTrue(contextLimit.message.orEmpty().contains("Maximum context length"))
        assertEquals(400, contextLimit.httpStatusCode)

        server.enqueue(jsonResponse(completionJson("filtered", "content_filter")))
        val filtered = org.junit.Assert.assertThrows(ModelRequestException.ApiRejected::class.java) {
            gateway().stream(nonStreamingRequest()).toListBlocking()
        }
        assertEquals("content_filter", filtered.code)
    }

    private fun gateway(): OpenAiCompatibleModelGateway = OpenAiCompatibleModelGateway(
        connectionProvider = ModelServiceConnectionProvider { serviceId ->
            if (serviceId == SERVICE_ID) ModelServiceConnection(server.url("/v1").toString().trimEnd('/'), API_KEY) else null
        },
        probeTimeoutMillis = 2_000,
    )

    private fun streamRequest() = ModelRequest(
        model = MODEL,
        messages = listOf(ModelMessage(ModelRole.USER, listOf(ModelContentPart.Text("hello")))),
        useNativeStreaming = true,
    )

    private fun nonStreamingRequest() = streamRequest().copy(useNativeStreaming = false)

    private fun jsonResponse(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun sseResponse(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/event-stream; charset=utf-8").body(body).build()

    private fun completionJson(text: String, finishReason: String): String =
        """{"choices":[{"index":0,"message":{"role":"assistant","content":"${text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}"},"finish_reason":"$finishReason"}]}"""

    private fun sseEvent(data: String) = "data: " + data + "\r\n\r\n"

    private fun kotlinx.coroutines.flow.Flow<ModelStreamEvent>.toListBlocking(): List<ModelStreamEvent> = runBlocking {
        val events = ArrayList<ModelStreamEvent>()
        collect { events += it }
        events
    }

    private companion object {
        const val SERVICE_ID = "service-a"
        const val API_KEY = "test-secret"
        val MODEL = ModelReference(SERVICE_ID, "model-a")
    }
}
