package com.elymbot.core.model

import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelToolCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.Response
import java.util.Locale

internal fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

internal data class ParsedCompletion(
    val text: String,
    val finishReason: ModelFinishReason,
    val toolCalls: List<ModelToolCall>,
)

private const val MAX_ERROR_MESSAGE_LENGTH = 512

internal object OpenAiCompatibleResponseDecoder {
    private val json = Json { ignoreUnknownKeys = true }
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

    fun responseFailure(response: Response, connection: ModelServiceConnection): ModelRequestException {
        val root = parseObjectOrNull(response.body.string())
        val error = root?.get("error") as? JsonObject
        if (error != null) return apiFailure(error, connection, response.code)
        return ModelRequestException.HttpStatus(response.code, "The model service returned HTTP " + response.code + ".")
    }

    internal fun parseObject(body: String, description: String): JsonObject =
        parseElement(body, description) as? JsonObject
            ?: throw ModelRequestException.Protocol("The " + description + " response is not a JSON object.")

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

    internal fun parseFinishReason(raw: String): ModelFinishReason = when (raw) {
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

}
