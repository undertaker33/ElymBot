package com.astrbot.android.runtime.llm

import kotlinx.coroutines.flow.flow

import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolCall
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Suppress("DEPRECATION")
internal open class ChatCompletionServiceLlmClient : LlmClientPort {

    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult {
        val chatTools = request.tools.map { it.toChatToolDefinition() }
        val result = com.astrbot.android.core.runtime.llm.ChatCompletionService.sendConfiguredChatWithTools(
            provider = request.provider,
            messages = request.messages,
            systemPrompt = request.systemPrompt,
            config = request.config,
            availableProviders = request.availableProviders,
            tools = chatTools,
        )
        return result.toPortInvocationResult()
    }

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = flow {
        val chatTools = request.tools.map { it.toChatToolDefinition() }
        try {
            val result = com.astrbot.android.core.runtime.llm.ChatCompletionService.sendConfiguredChatStreamWithTools(
                provider = request.provider,
                messages = request.messages,
                systemPrompt = request.systemPrompt,
                config = request.config,
                availableProviders = request.availableProviders,
                tools = chatTools,
                onDelta = { delta ->
                    if (delta.isNotBlank()) {
                        emit(LlmStreamEvent.TextDelta(delta))
                    }
                },
                onToolCallDelta = { index, name, argumentsFragment ->
                    emit(
                        LlmStreamEvent.ToolCallDelta(
                            index = index,
                            name = name,
                            argumentsFragment = argumentsFragment,
                        ),
                    )
                },
            )
            emit(LlmStreamEvent.Completed(result.toPortInvocationResult()))
        } catch (e: Throwable) {
            emit(LlmStreamEvent.Failed(e))
        }
    }
}

@Deprecated("Phase-2 residue. Production Hilt binding uses ChatCompletionServiceLlmClient.")
internal class LegacyChatCompletionServiceAdapter : ChatCompletionServiceLlmClient() {
    companion object {
        internal fun LlmToolDefinition.toLegacyToolDefinition(): com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition {
            return toChatToolDefinition()
        }

        internal fun com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatCompletionResult.toLlmInvocationResult(): LlmInvocationResult {
            return toPortInvocationResult()
        }
    }
}

private fun LlmToolDefinition.toChatToolDefinition(): com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition {
    val safeParameters = parametersJson.ifBlank { "{}" }
    return com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition(
        name = name,
        description = description,
        parameters = JSONObject(safeParameters),
    )
}

private fun com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatCompletionResult.toPortInvocationResult(): LlmInvocationResult {
    return LlmInvocationResult(
        text = text,
        toolCalls = toolCalls.map { tc ->
            LlmToolCall(
                id = tc.id,
                name = tc.name,
                arguments = tc.arguments,
            )
        },
        finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
    )
}
