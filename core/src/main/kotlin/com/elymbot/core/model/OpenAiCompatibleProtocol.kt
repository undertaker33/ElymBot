package com.elymbot.core.model

import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ModelToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

internal data class ParsedCompletion(
    val text: String,
    val finishReason: ModelFinishReason,
    val toolCalls: List<ModelToolCall>,
)

private const val MAX_ERROR_MESSAGE_LENGTH = 512

internal object OpenAiCompatibleProtocol {
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val json = Json { ignoreUnknownKeys = true }
    private val imageMediaTypeRegex = Regex("image/[A-Za-z0-9.+-]+")
    private val contextLimitTerms = listOf(
        "context_length_exceeded",
        "maximum context length",
        "maximum context window",
        "too many tokens",
        "input tokens exceed",
    )
    private val stableApiCodeAliases = mapOf(
        "invalid_api_key" to "invalid_api_key",
        "authentication_error" to "authentication_error",
        "unauthorized" to "authentication_error",
        "permission_denied" to "permission_denied",
        "permission_error" to "permission_denied",
        "rate_limit" to "rate_limit",
        "rate_limit_exceeded" to "rate_limit",
        "too_many_requests" to "rate_limit",
        "insufficient_quota" to "insufficient_quota",
        "overloaded" to "overloaded",
        "server_overloaded" to "overloaded",
        "temporarily_unavailable" to "temporarily_unavailable",
        "service_unavailable" to "temporarily_unavailable",
        "context_length_exceeded" to "context_length_exceeded",
        "model_not_found" to "model_not_found",
        "unsupported_model" to "model_unsupported",
        "model_unsupported" to "model_unsupported",
        "unsupported_image_input" to "vision_unsupported",
        "vision_not_supported" to "vision_unsupported",
        "unsupported_streaming" to "streaming_unsupported",
        "streaming_unsupported" to "streaming_unsupported",
        "function_call_unsupported" to "tool_unsupported",
        "tool_calls_not_supported" to "tool_unsupported",
        "content_filter" to "content_filter",
        "invalid_request_error" to "invalid_request",
        "server_error" to "server_error",
        "api_error" to "api_error",
    )

    fun visionProbeImageBytes(): ByteArray = VisionProbeImage.pngBytes()

    fun buildRequest(
        request: ModelRequest,
        streaming: Boolean,
        forcedToolChoice: String? = null,
    ): String {
        validateRequest(request)
        return buildJsonObject {
            put("model", request.model.modelId)
            put("messages", buildJsonArray { request.messages.forEach { add(serializeMessage(it)) } })
            put("stream", streaming)
            request.maxOutputTokens?.let { put("max_completion_tokens", it) }
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray { request.tools.forEach { add(serializeTool(it)) } })
                if (forcedToolChoice != null) put("tool_choice", forcedToolChoice)
            }
        }.toString()
    }

    fun parseModels(body: String): List<String> {
        val root = parseObject(body, "model list")
        val data = root["data"] as? JsonArray
            ?: throw ModelRequestException.Protocol("The model list response is missing data.")
        return data.mapNotNull { item ->
            (item as? JsonObject)?.string("id")?.takeIf(String::isNotBlank)
        }.distinct()
    }

    fun parseCompletion(body: String, connection: ModelServiceConnection): ParsedCompletion {
        val root = parseObject(body, "chat completion")
        (root["error"] as? JsonObject)?.let { throw apiFailure(it, connection) }
        val choices = root["choices"] as? JsonArray
            ?: throw ModelRequestException.Protocol("The chat completion response is missing choices.")
        val choice = choices.firstOrNull { (it as? JsonObject)?.int("index") == 0 } as? JsonObject
            ?: choices.firstOrNull() as? JsonObject
            ?: throw ModelRequestException.Protocol("The chat completion response contains no choices.")
        val message = choice["message"] as? JsonObject
            ?: throw ModelRequestException.Protocol("The chat completion response is missing its message.")
        val text = parseCompletionText(message)
        val finish = choice.string("finish_reason")?.let(::parseFinishReason)
            ?: throw ModelRequestException.Protocol("The chat completion response is missing its finish reason.")
        if (finish == ModelFinishReason.OUTPUT_LIMIT) return ParsedCompletion(text, finish, emptyList())
        val toolCalls = parseCompletedToolCalls(message)
        if (finish == ModelFinishReason.TOOL_CALLS && toolCalls.isEmpty()) {
            throw ModelRequestException.Protocol("The chat completion ended with tool_calls but returned no calls.")
        }
        if (finish == ModelFinishReason.STOP && toolCalls.isNotEmpty()) {
            throw ModelRequestException.Protocol("The chat completion ended normally with an incomplete tool-call batch.")
        }
        return ParsedCompletion(text, finish, toolCalls)
    }

    fun streamResponse(
        response: Response,
        connection: ModelServiceConnection,
        emit: (ModelStreamEvent) -> Unit,
    ) {
        val body = response.body
        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
        if (!contentType.contains("text/event-stream")) {
            val root = parseObject(body.string(), "stream response")
            (root["error"] as? JsonObject)?.let { throw apiFailure(it, connection) }
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

    fun responseFailure(response: Response, connection: ModelServiceConnection): ModelRequestException {
        val root = parseObjectOrNull(response.body.string())
        val error = root?.get("error") as? JsonObject
        if (error != null) return apiFailure(error, connection, response.code)
        return ModelRequestException.HttpStatus(response.code, "The model service returned HTTP " + response.code + ".")
    }

    fun parseObject(body: String, description: String): JsonObject =
        parseElement(body, description) as? JsonObject
            ?: throw ModelRequestException.Protocol("The " + description + " response is not a JSON object.")

    private fun validateRequest(request: ModelRequest) {
        if (request.model.serviceId.isBlank() || request.model.modelId.isBlank()) {
            throw ModelRequestException.Protocol("The model reference is incomplete.")
        }
        if (request.messages.isEmpty()) throw ModelRequestException.Protocol("A model request has no messages.")
        if (request.maxOutputTokens != null && request.maxOutputTokens <= 0) {
            throw ModelRequestException.Protocol("The maximum output token count must be positive.")
        }
        for (message in request.messages) {
            when (message.role) {
                ModelRole.SYSTEM, ModelRole.USER -> if (message.toolCallId != null || message.toolCalls.isNotEmpty()) {
                    throw ModelRequestException.Protocol("A user or system message contains tool-call fields.")
                }
                ModelRole.ASSISTANT -> if (message.toolCallId != null) {
                    throw ModelRequestException.Protocol("An assistant message contains a tool-result ID.")
                }
                ModelRole.TOOL -> if (message.toolCallId.isNullOrBlank() || message.toolCalls.isNotEmpty()) {
                    throw ModelRequestException.Protocol("A tool result is missing its call ID.")
                }
            }
        }
        if (request.tools.map(ModelToolDefinition::name).any(String::isBlank)) {
            throw ModelRequestException.Protocol("A tool definition has no name.")
        }
        if (request.tools.map(ModelToolDefinition::name).toSet().size != request.tools.size) {
            throw ModelRequestException.Protocol("A model request contains duplicate tool names.")
        }
    }

    private fun serializeMessage(message: ModelMessage): JsonObject {
        val role = when (message.role) {
            ModelRole.SYSTEM -> "system"
            ModelRole.USER -> "user"
            ModelRole.ASSISTANT -> "assistant"
            ModelRole.TOOL -> "tool"
        }
        val content = serializeContent(message)
        return buildJsonObject {
            put("role", role)
            content?.let { put("content", it) }
            if (message.role == ModelRole.TOOL) put("tool_call_id", message.toolCallId.orEmpty())
            if (message.role == ModelRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
                put("tool_calls", buildJsonArray { message.toolCalls.forEach { add(serializeToolCall(it)) } })
            }
        }
    }

    private fun serializeContent(message: ModelMessage): JsonElement? {
        val hasImage = message.content.any { it is ModelContentPart.Image }
        if (!hasImage) {
            return JsonPrimitive(message.content.joinToString(separator = "") { (it as ModelContentPart.Text).value })
        }
        if (message.role != ModelRole.USER) {
            throw ModelRequestException.Protocol("Only user messages can contain image parts.")
        }
        return buildJsonArray {
            message.content.forEach { part ->
                when (part) {
                    is ModelContentPart.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", part.value)
                    })
                    is ModelContentPart.Image -> {
                        if (!part.mediaType.matches(imageMediaTypeRegex)) {
                            throw ModelRequestException.Protocol("An image message has an invalid media type.")
                        }
                        val encoded = Base64.getEncoder().encodeToString(part.bytes)
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:" + part.mediaType + ";base64," + encoded)
                            })
                        })
                    }
                }
            }
        }
    }

    private fun serializeTool(definition: ModelToolDefinition): JsonObject {
        val schema = parseObject(definition.parametersJsonSchema, "tool parameters schema")
        return buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", definition.name)
                put("description", definition.description)
                put("parameters", schema)
            })
        }
    }

    private fun serializeToolCall(call: ModelToolCall): JsonObject {
        if (call.callId.isBlank() || call.name.isBlank()) {
            throw ModelRequestException.Protocol("An assistant tool call is missing its ID or name.")
        }
        return buildJsonObject {
            put("id", call.callId)
            put("type", "function")
            put("function", buildJsonObject {
                put("name", call.name)
                put("arguments", call.argumentsJson)
            })
        }
    }

    private fun parseCompletionText(message: JsonObject): String {
        val content = when (val value = message["content"]) {
            null, JsonNull -> ""
            is JsonPrimitive -> if (value.isString) value.content else {
                throw ModelRequestException.Protocol("The model returned non-text content.")
            }
            is JsonArray -> value.mapNotNull { part ->
                val objectPart = part as? JsonObject ?: return@mapNotNull null
                if (objectPart.string("type") == "text") objectPart.string("text") else null
            }.joinToString(separator = "")
            else -> throw ModelRequestException.Protocol("The model returned an invalid content value.")
        }
        val refusal = message.string("refusal").orEmpty()
        return if (content.isEmpty()) refusal else content
    }

    private fun parseCompletedToolCalls(message: JsonObject): List<ModelToolCall> {
        val elements = message["tool_calls"] as? JsonArray ?: return emptyList()
        val ids = HashSet<String>()
        return elements.map { element ->
            val call = element as? JsonObject
                ?: throw ModelRequestException.Protocol("The chat completion contains an invalid tool call.")
            val id = call.string("id")?.takeIf(String::isNotBlank)
                ?: throw ModelRequestException.Protocol("A tool call is missing its ID.")
            if (!ids.add(id)) throw ModelRequestException.Protocol("The model returned duplicate tool-call IDs.")
            if (call.string("type")?.let { it != "function" } == true) {
                throw ModelRequestException.Protocol("The model returned an unsupported tool-call type.")
            }
            val function = call["function"] as? JsonObject
                ?: throw ModelRequestException.Protocol("A tool call is missing its function.")
            val name = function.string("name")?.takeIf(String::isNotBlank)
                ?: throw ModelRequestException.Protocol("A tool call is missing its function name.")
            ModelToolCall(id, name, function.argumentJson("arguments"))
        }
    }

    private fun parseFinishReason(raw: String): ModelFinishReason = when (raw) {
        "stop" -> ModelFinishReason.STOP
        "tool_calls" -> ModelFinishReason.TOOL_CALLS
        "length" -> ModelFinishReason.OUTPUT_LIMIT
        "content_filter" -> throw ModelRequestException.ApiRejected(
            code = "content_filter",
            message = "The model response was blocked by a content filter.",
        )
        else -> throw ModelRequestException.Protocol("The model returned an unsupported finish reason.")
    }

    internal fun apiFailure(
        error: JsonObject,
        connection: ModelServiceConnection,
        httpStatusCode: Int? = null,
    ): ModelRequestException {
        val rawCode = error.string("code")
        val rawType = error.string("type")
        val message = connection.sanitize(error.string("message") ?: "The model service rejected the request.")
            .take(MAX_ERROR_MESSAGE_LENGTH)
        val providerValues = listOfNotNull(rawCode, rawType).map { it.trim().lowercase(Locale.ROOT) }
        val code = providerValues.firstNotNullOfOrNull(stableApiCodeAliases::get)
        val normalized = (providerValues + message.lowercase(Locale.ROOT)).joinToString(" ")
        return if (contextLimitTerms.any(normalized::contains)) {
            ModelRequestException.ContextLimit(message, httpStatusCode)
        } else {
            ModelRequestException.ApiRejected(code, message, httpStatusCode)
        }
    }

    private fun parseElement(body: String, description: String): JsonElement = try {
        json.parseToJsonElement(body)
    } catch (failure: SerializationException) {
        throw ModelRequestException.Protocol("The " + description + " response is not valid JSON.", failure)
    }

    private fun parseObjectOrNull(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.argumentJson(name: String): String = when (val arguments = this[name]) {
        null, JsonNull -> ""
        is JsonPrimitive -> if (arguments.isString) arguments.content else arguments.toString()
        else -> arguments.toString()
    }

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private class VisionProbeImage {
        companion object {
            private const val WIDTH = 96
            private const val HEIGHT = 64
            private val colors = arrayOf(
                byteArrayOf(0xF2.toByte(), 0x25, 0x32),
                byteArrayOf(0x14, 0x64, 0xE8.toByte()),
                byteArrayOf(0x14, 0x8A.toByte(), 0x32),
                byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00),
            )

            fun pngBytes(): ByteArray {
                val rows = ByteArrayOutputStream()
                for (y in 0 until HEIGHT) {
                    rows.write(0)
                    for (x in 0 until WIDTH) {
                        val quadrant = (if (y < HEIGHT / 2) 0 else 2) + if (x < WIDTH / 2) 0 else 1
                        rows.write(colors[quadrant])
                    }
                }
                val compressed = ByteArrayOutputStream().also { target ->
                    DeflaterOutputStream(target).use { it.write(rows.toByteArray()) }
                }.toByteArray()
                val image = ByteArrayOutputStream()
                image.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                val header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
                    .putInt(WIDTH).putInt(HEIGHT).put(8).put(2).put(0).put(0).put(0).array()
                writeChunk(image, "IHDR", header)
                writeChunk(image, "IDAT", compressed)
                writeChunk(image, "IEND", ByteArray(0))
                return image.toByteArray()
            }

            private fun writeChunk(output: ByteArrayOutputStream, name: String, data: ByteArray) {
                output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
                val type = name.toByteArray(StandardCharsets.US_ASCII)
                output.write(type)
                output.write(data)
                val checksum = CRC32().apply { update(type); update(data) }.value.toInt()
                output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array())
            }
        }
    }

}

internal fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

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
        val root = OpenAiCompatibleProtocol.parseObject(payload, "stream event")
        (root["error"] as? JsonObject)?.let { throw OpenAiCompatibleProtocol.apiFailure(it, connection) }
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
        val parsed = parseFinishReason(raw)
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

private fun parseFinishReason(raw: String): ModelFinishReason = when (raw) {
    "stop" -> ModelFinishReason.STOP
    "tool_calls" -> ModelFinishReason.TOOL_CALLS
    "length" -> ModelFinishReason.OUTPUT_LIMIT
    "content_filter" -> throw ModelRequestException.ApiRejected(
        code = "content_filter",
        message = "The model response was blocked by a content filter.",
    )
    else -> throw ModelRequestException.Protocol("The model returned an unsupported finish reason.")
}
