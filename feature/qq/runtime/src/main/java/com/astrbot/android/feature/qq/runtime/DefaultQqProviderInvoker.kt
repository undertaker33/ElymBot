package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.feature.plugin.domain.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.domain.runtime.PluginLlmToolCall
import com.astrbot.android.feature.plugin.domain.runtime.PluginLlmToolCallDelta
import com.astrbot.android.feature.plugin.domain.runtime.PluginProviderMessageDto
import com.astrbot.android.feature.plugin.domain.runtime.PluginProviderMessageMediaRefPart
import com.astrbot.android.feature.plugin.domain.runtime.PluginProviderMessageTextPart
import com.astrbot.android.feature.plugin.domain.runtime.PluginProviderMessageRole
import com.astrbot.android.feature.plugin.domain.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.domain.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.domain.runtime.PluginV2ProviderStreamChunk
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal class DefaultQqProviderInvoker(
    private val llmClient: LlmClientPort,
) : QqProviderInvoker {

    override suspend fun invoke(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
        config: ConfigProfile,
    ): PluginV2ProviderInvocationResult {
        val availableProviders = ctx.availableProviders.map { it.toProviderProfile() }
        val provider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val llmRequest = LlmInvocationRequest(
            provider = provider.toLlmProviderProfile(),
            messages = request.messages.toConversationMessages(request.requestId).toLlmConversationMessages(),
            systemPrompt = request.systemPrompt,
            config = config.toLlmRuntimeConfig(),
            availableProviders = availableProviders.map { it.toLlmProviderProfile() },
            tools = request.tools.map { tool ->
                LlmToolDefinition(
                    name = tool.name,
                    description = tool.description,
                    parametersJson = JSONObject(tool.inputSchema.filterValues { it != null } as Map<*, *>).toString(),
                )
            },
        )
        if (mode == PluginV2StreamingMode.NON_STREAM || !request.streamingEnabled) {
            val result = llmClient.sendWithTools(llmRequest)
            return PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = provider.id,
                    modelId = request.selectedModelId.ifBlank { provider.model },
                    text = result.text,
                    toolCalls = result.toolCalls.map { toolCall ->
                        PluginLlmToolCall(
                            toolCallId = toolCall.id,
                            toolName = toolCall.name,
                            arguments = parseToolCallArguments(toolCall.arguments),
                        )
                    },
                ),
            )
        }

        val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
        var completedText = ""
        var completedToolCalls: List<PluginLlmToolCall> = emptyList()
        llmClient.streamWithTools(llmRequest).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    if (event.text.isNotBlank()) {
                        chunks += PluginV2ProviderStreamChunk(deltaText = event.text)
                    }
                }

                is LlmStreamEvent.ToolCallDelta -> {
                    chunks += PluginV2ProviderStreamChunk(
                        toolCallDeltas = listOf(
                            PluginLlmToolCallDelta(
                                index = event.index,
                                toolName = event.name?.trim().takeUnless { it.isNullOrBlank() } ?: "tool-${event.index}",
                                arguments = emptyMap(),
                            ),
                        ),
                    )
                }

                is LlmStreamEvent.Completed -> {
                    completedText = event.result.text
                    completedToolCalls = event.result.toolCalls.mapIndexed { index, toolCall ->
                        PluginLlmToolCall(
                            toolCallId = toolCall.id,
                            toolName = toolCall.name.ifBlank { "tool-$index" },
                            arguments = parseToolCallArguments(toolCall.arguments),
                        )
                    }
                }

                is LlmStreamEvent.Failed -> throw event.throwable
            }
        }
        val finalizedToolDeltas = completedToolCalls.mapIndexed { index, toolCall ->
            PluginLlmToolCallDelta(
                index = index,
                toolCallId = toolCall.toolCallId,
                toolName = toolCall.toolName,
                arguments = toolCall.arguments,
            )
        }
        if (finalizedToolDeltas.isNotEmpty()) {
            chunks += PluginV2ProviderStreamChunk(toolCallDeltas = finalizedToolDeltas)
        }
        if (completedText.isNotBlank() && chunks.none { it.deltaText.isNotBlank() }) {
            chunks.add(0, PluginV2ProviderStreamChunk(deltaText = completedText))
        }
        chunks += PluginV2ProviderStreamChunk(
            isCompletion = true,
            finishReason = if (completedToolCalls.isNotEmpty()) "tool_calls" else "stop",
        )
        return PluginV2ProviderInvocationResult.Streaming(events = chunks.toList())
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun List<PluginProviderMessageDto>.toConversationMessages(
        requestId: String,
    ): List<ConversationMessage> {
        return mapIndexed { index, message ->
            val text = message.parts
                .filterIsInstance<PluginProviderMessageTextPart>()
                .joinToString(separator = "\n") { part -> part.text }
            val attachments = message.parts
                .filterIsInstance<PluginProviderMessageMediaRefPart>()
                .mapIndexed { attachmentIndex, part ->
                    ConversationAttachment(
                        id = "$requestId-$index-$attachmentIndex",
                        type = if (part.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = part.mimeType,
                        remoteUrl = part.uri,
                    )
                }
            val toolCallId = if (message.role == PluginProviderMessageRole.TOOL) {
                extractHostToolCallId(message.metadata)
            } else {
                null
            }
            ConversationMessage(
                id = toolCallId ?: "$requestId-$index",
                role = message.role.wireValue.lowercase(Locale.US),
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
                toolCallId = toolCallId.orEmpty(),
                assistantToolCalls = message.toolCalls.map { toolCall ->
                    com.astrbot.android.model.chat.ConversationToolCall(
                        id = toolCall.normalizedId,
                        name = toolCall.normalizedToolName,
                        arguments = canonicalJson(toolCall.normalizedArguments),
                    )
                },
            )
        }
    }

    private fun canonicalJson(value: Map<String, Any?>): String {
        return JSONObject().apply {
            value.keys.sorted().forEach { key ->
                put(key, toJsonValue(value[key]))
            }
        }.toString()
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is Number -> value.toDouble()
            is List<*> -> JSONArray().apply {
                value.forEach { item -> put(toJsonValue(item)) }
            }
            is Map<*, *> -> JSONObject().apply {
                value.keys
                    .map { key -> key as? String ?: error("JSON-like map keys must be strings.") }
                    .sorted()
                    .forEach { key -> put(key, toJsonValue(value[key])) }
            }
            else -> error("Unsupported JSON-like value type: ${value::class.java.name}")
        }
    }

    private fun extractHostToolCallId(metadata: Map<String, *>?): String? {
        val host = metadata?.get("__host") as? Map<*, *> ?: return null
        return (host["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}

fun createQqProviderInvoker(llmClient: LlmClientPort): QqProviderInvoker {
    return DefaultQqProviderInvoker(llmClient)
}
