package com.elymbot.android.feature.cron.runtime

import com.elymbot.android.core.runtime.context.ResolvedRuntimeContext
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.llm.LlmConversationAttachment
import com.elymbot.android.core.runtime.llm.LlmConversationMessage
import com.elymbot.android.core.runtime.llm.LlmConversationToolCall
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmFeatureSupportState
import com.elymbot.android.core.runtime.llm.LlmInvocationRequest
import com.elymbot.android.core.runtime.llm.LlmInvocationResult
import com.elymbot.android.core.runtime.llm.LlmProviderCapability
import com.elymbot.android.core.runtime.llm.LlmProviderProfile
import com.elymbot.android.core.runtime.llm.LlmProviderType
import com.elymbot.android.core.runtime.llm.LlmRuntimeConfig
import com.elymbot.android.core.runtime.llm.LlmStreamEvent
import com.elymbot.android.core.runtime.llm.LlmToolDefinition
import com.elymbot.android.feature.plugin.runtime.MessageConverters.toConversationMessages
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmResponse
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmToolCall
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmToolCallDelta
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderRequest
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderInvocationResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderStreamChunk
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationToolCall
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

internal class ScheduledTaskProviderInvocationService(
    private val llmClient: LlmClientPort,
) {
    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult {
        val availableProviders = ctx.availableProviders.map { it.toLlmProviderProfile() }
        val resolvedProvider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                LlmProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(request.requestId)
        val llmTools = request.tools.map { def ->
            LlmToolDefinition(
                name = def.name,
                description = def.description,
                parametersJson = JSONObject(
                    def.inputSchema.filterValues { it != null } as Map<*, *>,
                ).toString(),
            )
        }
        val llmRequest = LlmInvocationRequest(
            provider = resolvedProvider,
            messages = messages.toLlmConversationMessages(),
            systemPrompt = request.systemPrompt,
            config = ctx.toLlmRuntimeConfig(),
            availableProviders = availableProviders,
            tools = llmTools,
        )

        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled) {
            val result = llmClient.sendWithTools(llmRequest)
            PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = resolvedProvider.id,
                    modelId = request.selectedModelId.ifBlank { resolvedProvider.model },
                    text = result.text,
                    toolCalls = result.toolCalls.map { tc ->
                        PluginLlmToolCall(
                            toolCallId = tc.id,
                            toolName = tc.name,
                            arguments = parseToolCallArguments(tc.arguments),
                        )
                    },
                ),
            )
        } else {
            val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
            var completedResult: LlmInvocationResult? = null
            llmClient.streamWithTools(llmRequest).collect { event ->
                when (event) {
                    is LlmStreamEvent.TextDelta -> {
                        chunks += PluginV2ProviderStreamChunk(deltaText = event.text)
                    }
                    is LlmStreamEvent.ToolCallDelta -> {
                        // Collect tool call deltas; finalization happens on Completed.
                    }
                    is LlmStreamEvent.Completed -> {
                        completedResult = event.result
                    }
                    is LlmStreamEvent.Failed -> {
                        throw event.throwable
                    }
                }
            }
            val result = completedResult ?: LlmInvocationResult(text = "")
            val finalizedToolDeltas = result.toolCalls.mapIndexedNotNull { index, toolCall ->
                val normalizedName = toolCall.name.trim()
                if (normalizedName.isBlank()) {
                    null
                } else {
                    PluginLlmToolCallDelta(
                        index = index,
                        toolCallId = toolCall.id,
                        toolName = normalizedName,
                        arguments = parseToolCallArguments(toolCall.arguments),
                    )
                }
            }
            if (finalizedToolDeltas.isNotEmpty()) {
                chunks += PluginV2ProviderStreamChunk(toolCallDeltas = finalizedToolDeltas)
            }
            chunks += PluginV2ProviderStreamChunk(
                isCompletion = true,
                finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop",
            )
            if (result.text.isNotBlank() && chunks.size == 1) {
                chunks.add(0, PluginV2ProviderStreamChunk(deltaText = result.text))
            }
            PluginV2ProviderInvocationResult.Streaming(events = chunks)
        }
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun ResolvedRuntimeContext.toLlmRuntimeConfig(): LlmRuntimeConfig {
        return LlmRuntimeConfig(
            id = config.id,
            imageCaptionTextEnabled = config.imageCaptionTextEnabled,
            defaultVisionProviderId = config.defaultVisionProviderId,
        )
    }

    private fun RuntimeProviderSnapshot.toLlmProviderProfile(): LlmProviderProfile {
        return LlmProviderProfile(
            id = id,
            name = name,
            baseUrl = baseUrl,
            model = model,
            providerType = providerType.toLlmProviderType(),
            apiKey = apiKey,
            capabilities = capabilities.mapNotNull { it.toLlmProviderCapabilityOrNull() }.toSet()
                .ifEmpty { setOf(LlmProviderCapability.CHAT) },
            enabled = enabled,
            multimodalRuleSupport = multimodalRuleSupport.toLlmFeatureSupportState(),
            multimodalProbeSupport = multimodalProbeSupport.toLlmFeatureSupportState(),
            nativeStreamingRuleSupport = nativeStreamingRuleSupport.toLlmFeatureSupportState(),
            nativeStreamingProbeSupport = nativeStreamingProbeSupport.toLlmFeatureSupportState(),
            sttProbeSupport = sttProbeSupport.toLlmFeatureSupportState(),
            ttsProbeSupport = ttsProbeSupport.toLlmFeatureSupportState(),
            ttsVoiceOptions = ttsVoiceOptions,
        )
    }

    private fun String.toLlmProviderType(): LlmProviderType =
        runCatching { LlmProviderType.valueOf(this) }.getOrDefault(LlmProviderType.CUSTOM)

    private fun String.toLlmProviderCapabilityOrNull(): LlmProviderCapability? =
        runCatching { LlmProviderCapability.valueOf(this) }.getOrNull()

    private fun String.toLlmFeatureSupportState(): LlmFeatureSupportState =
        runCatching { LlmFeatureSupportState.valueOf(this) }.getOrDefault(LlmFeatureSupportState.UNKNOWN)

    private fun List<ConversationMessage>.toLlmConversationMessages(): List<LlmConversationMessage> =
        map { it.toLlmConversationMessage() }

    private fun ConversationMessage.toLlmConversationMessage(): LlmConversationMessage {
        return LlmConversationMessage(
            id = id,
            role = role,
            content = content,
            timestamp = timestamp,
            attachments = attachments.map { it.toLlmConversationAttachment() },
            toolCallId = toolCallId,
            assistantToolCalls = assistantToolCalls.map { it.toLlmConversationToolCall() },
        )
    }

    private fun ConversationAttachment.toLlmConversationAttachment(): LlmConversationAttachment {
        return LlmConversationAttachment(
            id = id,
            type = type,
            mimeType = mimeType,
            fileName = fileName,
            base64Data = base64Data,
            remoteUrl = remoteUrl,
        )
    }

    private fun ConversationToolCall.toLlmConversationToolCall(): LlmConversationToolCall {
        return LlmConversationToolCall(
            id = id,
            name = name,
            arguments = arguments,
        )
    }
}

