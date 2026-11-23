package com.elymbot.core.tool

import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ReplyMessage
import com.elymbot.core.contract.ReplySubmission
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.ReplySubmitter
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Host tool that durably queues user-visible messages through the App boundary. */
class ReplyToolSource(
    private val submitter: ReplySubmitter,
) : ToolSource {
    override val definition = ToolDefinition(
        id = "core.reply",
        name = TOOL_NAME,
        description = "Queue one or more user-visible text messages. Use this tool for every reply to the user; it does not end the turn.",
        parametersJsonSchema = PARAMETERS_SCHEMA,
        scopes = setOf(ToolScope.MAIN_SESSION, ToolScope.PERSONA_SESSION),
    )

    override suspend fun execute(
        call: ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (ToolProgress) -> Unit,
    ): ToolExecutionResult {
        val arguments = runCatching { Json.parseToJsonElement(call.argumentsJson) as? JsonObject }.getOrNull()
            ?: return rejected(call.callId)
        val rawMessages = arguments["messages"] as? JsonArray ?: return rejected(call.callId)
        if (rawMessages.isEmpty() || rawMessages.size > MAX_MESSAGES_PER_CALL) return rejected(call.callId)
        val messages = mutableListOf<ReplyMessage>()
        var totalCharacters = 0
        for (element in rawMessages) {
            val message = element as? JsonObject ?: return rejected(call.callId)
            val text = (message["text"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.length <= MAX_MESSAGE_CHARACTERS }
                ?: return rejected(call.callId)
            val delayMillis = (message["delayMillis"] as? JsonPrimitive)?.intOrNull
                ?.takeIf { it in 0..MAX_SUGGESTED_DELAY_MILLIS }
                ?: return rejected(call.callId)
            totalCharacters += text.length
            if (totalCharacters > MAX_TOTAL_CHARACTERS_PER_CALL) return rejected(call.callId)
            messages += ReplyMessage(text = text, suggestedDelayMillis = delayMillis.toLong())
        }

        val result = try {
            submitter.submit(
                ReplySubmission(
                    context = ReplySubmissionContext(
                        sessionId = context.sessionId,
                        executionId = context.executionId,
                        attemptId = context.attemptId,
                        turnSequence = context.turnSequence,
                        toolStepOrdinal = context.toolStepOrdinal,
                        toolCallOrdinal = context.toolCallOrdinal,
                        toolCallId = call.callId,
                    ),
                    messages = messages,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ToolExecutionResult(call.callId, ToolResultStatus.FAILED, "Reply submission failed.")
        }
        return when (result) {
            is ReplySubmissionResult.Accepted -> ToolExecutionResult(
                call.callId,
                ToolResultStatus.SUCCESS,
                "Reply accepted for durable delivery (${messages.size} message${if (messages.size == 1) "" else "s"}).",
            )

            is ReplySubmissionResult.Rejected -> ToolExecutionResult(
                call.callId,
                ToolResultStatus.REJECTED,
                "Reply was not accepted: ${result.code.name}.",
            )

            is ReplySubmissionResult.Failed -> ToolExecutionResult(
                call.callId,
                ToolResultStatus.FAILED,
                "Reply submission failed: ${result.code.name}.",
            )
        }
    }

    private fun rejected(callId: String) = ToolExecutionResult(
        callId,
        ToolResultStatus.REJECTED,
        "Reply request is invalid.",
    )

    companion object {
        const val TOOL_NAME = "reply"
        const val MAX_MESSAGES_PER_CALL = 8
        const val MAX_MESSAGE_CHARACTERS = 4_000
        const val MAX_TOTAL_CHARACTERS_PER_CALL = 12_000
        const val MAX_SUGGESTED_DELAY_MILLIS = 30_000

        private const val PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "messages": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 8,
                  "items": {
                    "type": "object",
                    "properties": {
                      "text": {"type": "string", "minLength": 1, "maxLength": 4000},
                      "delayMillis": {"type": "integer", "minimum": 0, "maximum": 30000}
                    },
                    "required": ["text", "delayMillis"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["messages"],
              "additionalProperties": false
            }
        """
    }
}
