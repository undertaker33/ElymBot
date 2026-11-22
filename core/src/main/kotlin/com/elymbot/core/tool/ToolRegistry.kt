package com.elymbot.core.tool

import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class ToolRegistry(
    sources: List<ToolSource>,
    private val executionTimeoutMillis: Long = DEFAULT_TOOL_TIMEOUT_MILLIS,
    private val maxResultCharacters: Int = DEFAULT_MAX_RESULT_CHARACTERS,
) {
    private val sourcesByName: Map<String, ToolSource>

    init {
        require(executionTimeoutMillis > 0)
        require(maxResultCharacters > 0)
        val duplicateIds = sources.groupBy { it.definition.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) { "Tool source IDs must be unique." }
        val duplicateNames = sources.groupBy { it.definition.name }.filterValues { it.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Tool names must be unique." }
        require(sources.all { it.definition.id.isNotBlank() && it.definition.name.isNotBlank() }) {
            "Tool source IDs and names must not be blank."
        }
        require(sources.all { definition -> parseSchema(definition.definition.parametersJsonSchema) != null }) {
            "Tool parameter schemas must be JSON objects."
        }
        sourcesByName = sources.associateBy { it.definition.name }
    }

    fun definitionsFor(scope: ToolScope): List<ToolDefinition> = sourcesByName.values
        .map { it.definition }
        .filter { scope in it.scopes }

    suspend fun execute(
        call: ModelToolCall,
        context: ToolExecutionContext,
        onExecutionState: suspend (Boolean) -> Unit = {},
    ): ToolExecutionResult {
        if (call.callId.isBlank()) return rejected(call.callId, "Missing tool call ID.")
        val source = sourcesByName[call.name] ?: return rejected(call.callId, "Unknown tool.")
        if (context.scope !in source.definition.scopes) return rejected(call.callId, "Tool is not available in this session.")
        val validationError = validateArguments(source.definition, call.argumentsJson)
        if (validationError != null) return rejected(call.callId, validationError)

        val callerJob = currentCoroutineContext()[Job]
        var started = false
        return try {
            onExecutionState(true)
            started = true
            val result = withTimeout(executionTimeoutMillis) {
                source.execute(call, context) { _ -> }
            }
            ToolExecutionResult(
                callId = call.callId,
                status = result.status,
                content = bounded(result.content, maxResultCharacters),
            )
        } catch (timeout: TimeoutCancellationException) {
            if (callerJob?.isActive == false) throw timeout
            ToolExecutionResult(call.callId, ToolResultStatus.FAILED, "Tool execution timed out.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ToolExecutionResult(call.callId, ToolResultStatus.FAILED, "Tool execution failed.")
        } finally {
            if (started) {
                try {
                    onExecutionState(false)
                } catch (cancelled: CancellationException) {
                    if (callerJob?.isActive != false) throw cancelled
                }
            }
        }
    }

    private fun validateArguments(definition: ToolDefinition, raw: String): String? {
        val schema = parseSchema(definition.parametersJsonSchema)
            ?: return "Tool schema is invalid."
        val arguments = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return "Tool arguments must be a JSON object."
        return if (matchesSchema(arguments, schema)) null else "Tool arguments do not match the declared schema."
    }

    private fun matchesSchema(value: JsonElement, schema: JsonObject): Boolean {
        val expectedType = (schema["type"] as? JsonPrimitive)?.contentOrNull
        val typeMatches = when (expectedType) {
            null -> true
            "string" -> (value as? JsonPrimitive)?.isString == true
            "integer" -> (value as? JsonPrimitive)?.longOrNull != null
            "number" -> (value as? JsonPrimitive)?.doubleOrNull != null
            "boolean" -> (value as? JsonPrimitive)?.booleanOrNull != null
            "object" -> value is JsonObject
            "array" -> value is JsonArray
            "null" -> value == kotlinx.serialization.json.JsonNull
            else -> false
        }
        if (!typeMatches) return false
        val enumValues = schema["enum"] as? JsonArray
        if (enumValues != null && !enumValues.contains(value)) return false
        when (value) {
            is JsonObject -> {
                val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
                val required = (schema["required"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                if (required.any { it !in value }) return false
                val additional = schema["additionalProperties"]
                if (additional == JsonPrimitive(false) && value.keys.any { it !in properties }) return false
                for ((name, propertyValue) in value) {
                    val propertySchema = properties[name] as? JsonObject
                    if (propertySchema != null && !matchesSchema(propertyValue, propertySchema)) return false
                    if (name !in properties && additional is JsonObject && !matchesSchema(propertyValue, additional)) return false
                }
            }

            is JsonArray -> {
                val minimum = schema["minItems"]?.asIntOrNull()
                val maximum = schema["maxItems"]?.asIntOrNull()
                if (minimum != null && value.size < minimum) return false
                if (maximum != null && value.size > maximum) return false
                val itemSchema = schema["items"] as? JsonObject
                if (itemSchema != null && value.any { !matchesSchema(it, itemSchema) }) return false
            }

            is JsonPrimitive -> {
                if (value.isString) {
                    val length = value.content.length
                    val minimum = schema["minLength"]?.asIntOrNull()
                    val maximum = schema["maxLength"]?.asIntOrNull()
                    if (minimum != null && length < minimum) return false
                    if (maximum != null && length > maximum) return false
                } else {
                    val numeric = value.doubleOrNull
                    val minimum = schema["minimum"]?.doubleOrNull()
                    val maximum = schema["maximum"]?.doubleOrNull()
                    if ((minimum != null || maximum != null) && numeric == null) return false
                    if (numeric != null && minimum != null && numeric < minimum) return false
                    if (numeric != null && maximum != null && numeric > maximum) return false
                }
            }
        }
        return true
    }

    private fun JsonElement.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement.doubleOrNull(): Double? = (this as? JsonPrimitive)?.doubleOrNull

    private fun parseSchema(raw: String): JsonObject? =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    private fun bounded(value: String, limit: Int): String =
        if (value.length <= limit) value else value.take(limit) + "…[truncated]"

    private fun rejected(callId: String, reason: String) = ToolExecutionResult(
        callId = callId,
        status = ToolResultStatus.REJECTED,
        content = reason,
    )

    companion object {
        const val DEFAULT_TOOL_TIMEOUT_MILLIS = 10_000L
        const val DEFAULT_MAX_RESULT_CHARACTERS = 16_000
    }
}
