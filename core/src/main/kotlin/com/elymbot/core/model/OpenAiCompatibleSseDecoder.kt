package com.elymbot.core.model

import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object OpenAiCompatibleSseDecoder {
    fun streamResponse(
        response: Response,
        connection: ModelServiceConnection,
        emit: (ModelStreamEvent) -> Unit,
    ) {
        val body = response.body
        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
        if (!contentType.contains("text/event-stream")) {
            val root = OpenAiCompatibleResponseDecoder.parseObject(body.string(), "stream response")
            (root["error"] as? JsonObject)?.let { throw OpenAiCompatibleResponseDecoder.apiFailure(it, connection) }
            if (root["choices"] is JsonArray) {
                throw ModelRequestException.ApiRejected(
                    code = "streaming_unsupported",
                    message = "The model service returned a complete response to a streaming request.",
                )
            }
            throw ModelRequestException.Protocol("The model service did not return a valid SSE stream.")
        }
        val state = StreamingCompletionState(connection, emit)
        try {
            readSseEvents(body.byteStream(), state::consume)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (timeout: SocketTimeoutException) {
            throw ModelRequestException.TimedOut("The model stream timed out.", timeout)
        } catch (failure: IOException) {
            throw ModelRequestException.StreamInterrupted("The model stream was interrupted.", failure)
        }
        state.complete()
    }

}

private fun JsonObject.intValue(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private data class MutableToolCall(
    var callId: String? = null,
    val name: StringBuilder = StringBuilder(),
    val arguments: StringBuilder = StringBuilder(),
)

private class StreamingCompletionState(
    private val connection: ModelServiceConnection,
    private val emit: (ModelStreamEvent) -> Unit,
) {
    private val toolCalls = sortedMapOf<Int, MutableToolCall>()
    private val text = StringBuilder()
    private var finishReason: ModelFinishReason? = null
    private var sawDone = false

    fun consume(payload: String): Boolean {
        if (payload.trim() == "[DONE]") {
            sawDone = true
            return true
        }
        if (sawDone) throw ModelRequestException.Protocol("The model stream continued after its terminal marker.")
        val root = OpenAiCompatibleResponseDecoder.parseObject(payload, "stream event")
        (root["error"] as? JsonObject)?.let { throw OpenAiCompatibleResponseDecoder.apiFailure(it, connection) }
        val choices = root["choices"] as? JsonArray
            ?: throw ModelRequestException.Protocol("A model stream event is missing choices.")
        if (choices.isEmpty()) return false
        val choice = choices.firstOrNull { (it as? JsonObject)?.intValue("index") == 0 } as? JsonObject
            ?: choices.firstOrNull() as? JsonObject
            ?: throw ModelRequestException.Protocol("A model stream event contains an invalid choice.")
        val rawFinish = choice.stringValue("finish_reason")
        val delta = choice["delta"] as? JsonObject ?: if (rawFinish != null) JsonObject(emptyMap()) else {
            throw ModelRequestException.Protocol("A model stream event is missing delta.")
        }
        delta.stringValue("content")?.takeIf(String::isNotEmpty)?.let {
            text.append(it)
            emit(ModelStreamEvent.TextDelta(it))
        }
        delta.stringValue("refusal")?.takeIf(String::isNotEmpty)?.let {
            text.append(it)
            emit(ModelStreamEvent.TextDelta(it))
        }
        (delta["tool_calls"] as? JsonArray)?.forEach(::consumeToolDelta)
        if (rawFinish != null) setFinishReason(rawFinish)
        return false
    }

    fun complete(): ParsedCompletion {
        if (!sawDone && finishReason == null) {
            throw ModelRequestException.StreamInterrupted("The model stream ended before a terminal event.")
        }
        val reason = finishReason ?: if (toolCalls.isNotEmpty()) ModelFinishReason.TOOL_CALLS else ModelFinishReason.STOP
        if (reason == ModelFinishReason.OUTPUT_LIMIT) {
            emit(ModelStreamEvent.Completed(reason))
            return ParsedCompletion(text.toString(), reason, emptyList())
        }
        val calls = when (reason) {
            ModelFinishReason.TOOL_CALLS -> validatedToolCalls()
            ModelFinishReason.STOP -> {
                if (toolCalls.isNotEmpty()) {
                    throw ModelRequestException.Protocol("The model ended normally with an incomplete tool-call batch.")
                }
                emptyList()
            }
            ModelFinishReason.OUTPUT_LIMIT -> emptyList()
        }
        emit(ModelStreamEvent.Completed(reason, calls))
        return ParsedCompletion(text.toString(), reason, calls)
    }

    private fun consumeToolDelta(element: JsonElement) {
        val delta = element as? JsonObject
            ?: throw ModelRequestException.Protocol("A streamed tool call is invalid.")
        val index = delta.intValue("index")
            ?: throw ModelRequestException.Protocol("A streamed tool call is missing its index.")
        if (index < 0) throw ModelRequestException.Protocol("A streamed tool call has an invalid index.")
        val type = delta.stringValue("type")
        if (type != null && type != "function") {
            throw ModelRequestException.Protocol("The model returned an unsupported tool-call type.")
        }
        val call = toolCalls.getOrPut(index) { MutableToolCall() }
        delta.stringValue("id")?.takeIf(String::isNotEmpty)?.let { id ->
            if (call.callId == null) call.callId = id
            else if (call.callId != id) throw ModelRequestException.Protocol("A streamed tool-call ID changed between chunks.")
        }
        (delta["function"] as? JsonObject)?.let { function ->
            function.stringValue("name")?.let(call.name::append)
            when (val arguments = function["arguments"]) {
                null, JsonNull -> Unit
                is JsonPrimitive -> call.arguments.append(if (arguments.isString) arguments.content else arguments.toString())
                else -> call.arguments.append(arguments.toString())
            }
        }
    }

    private fun validatedToolCalls(): List<ModelToolCall> {
        if (toolCalls.isEmpty()) {
            throw ModelRequestException.Protocol("The model ended with tool_calls but returned no calls.")
        }
        val ids = HashSet<String>()
        return toolCalls.values.map { call ->
            val callId = call.callId?.takeIf(String::isNotBlank)
                ?: throw ModelRequestException.Protocol("A streamed tool call is missing its ID.")
            val name = call.name.toString().takeIf(String::isNotBlank)
                ?: throw ModelRequestException.Protocol("A streamed tool call is missing its name.")
            if (!ids.add(callId)) throw ModelRequestException.Protocol("The model returned duplicate tool-call IDs.")
            ModelToolCall(callId, name, call.arguments.toString())
        }
    }

    private fun setFinishReason(raw: String) {
        val parsed = OpenAiCompatibleResponseDecoder.parseFinishReason(raw)
        if (finishReason != null && finishReason != parsed) {
            throw ModelRequestException.Protocol("The model stream changed its finish reason.")
        }
        finishReason = parsed
    }
}

private fun readSseEvents(input: InputStream, consume: (String) -> Boolean) {
    val reader = InputStreamReader(input, StandardCharsets.UTF_8)
    val line = StringBuilder()
    val dataLines = ArrayList<String>()
    var hasData = false
    var skipNextLf = false
    var done = false

    fun dispatch() {
        if (hasData && consume(dataLines.joinToString(separator = "\n"))) done = true
        dataLines.clear()
        hasData = false
    }

    fun processLine(value: String) {
        if (value.isEmpty()) {
            dispatch()
            return
        }
        if (value.startsWith(':')) return
        val colon = value.indexOf(':')
        val field = if (colon < 0) value else value.substring(0, colon)
        var fieldValue = if (colon < 0) "" else value.substring(colon + 1)
        if (fieldValue.startsWith(' ')) fieldValue = fieldValue.substring(1)
        if (field == "data") {
            hasData = true
            dataLines += fieldValue
        }
    }

    val chars = CharArray(2048)
    while (!done) {
        val count = reader.read(chars)
        if (count < 0) break
        for (index in 0 until count) {
            val char = chars[index]
            if (skipNextLf) {
                skipNextLf = false
                if (char == '\n') continue
            }
            when (char) {
                '\r' -> {
                    processLine(line.toString())
                    line.setLength(0)
                    skipNextLf = true
                }
                '\n' -> {
                    processLine(line.toString())
                    line.setLength(0)
                }
                else -> line.append(char)
            }
            if (done) break
        }
    }
    if (!done) {
        if (line.isNotEmpty()) processLine(line.toString())
        if (hasData) dispatch()
    }
}
