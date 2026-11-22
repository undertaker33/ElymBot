package com.elymbot.core.agent

import com.elymbot.core.context.CompiledContext
import com.elymbot.core.context.ContextCompiler
import com.elymbot.core.context.ContextLimitExceededException
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentFailure
import com.elymbot.core.contract.AgentFailureKind
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentRuntime
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelSelection
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ModelToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ModelContentPart.Text
import com.elymbot.core.tool.HistoryReadToolSource
import com.elymbot.core.tool.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class ReActAgentRuntime(
    private val modelGateway: ModelGateway,
    private val contextCompiler: ContextCompiler,
    private val historyReader: HistoryReader,
    private val maxModelCalls: Int = DEFAULT_MAX_MODEL_CALLS,
    private val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    private val toolTimeoutMillis: Long = ToolRegistry.DEFAULT_TOOL_TIMEOUT_MILLIS,
    private val turnTimeoutMillis: Long = DEFAULT_TURN_TIMEOUT_MILLIS,
) : AgentRuntime {
    init {
        require(maxModelCalls > 0)
        require(maxToolCalls >= 0)
        require(toolTimeoutMillis > 0)
        require(turnTimeoutMillis > 0)
    }

    override fun run(request: AgentRequest): Flow<AgentEvent> = flow {
        val ownerJob = currentCoroutineContext()[Job]
        try {
            withTimeout(turnTimeoutMillis) {
                runTurn(request) { event -> emit(event) }
            }
            emit(finished(request, AgentTerminalStatus.COMPLETED))
        } catch (timeout: TimeoutCancellationException) {
            if (ownerJob?.isActive == false) throw timeout
            emit(
                finished(
                    request,
                    AgentTerminalStatus.TIMED_OUT,
                    AgentFailure(AgentFailureKind.NETWORK, retryable = true),
                ),
            )
        } catch (failure: AgentRunFailure) {
            emit(finished(request, failure.status, failure.failure))
        } catch (limit: ContextLimitExceededException) {
            emit(
                finished(
                    request,
                    AgentTerminalStatus.CONTEXT_LIMIT,
                    AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emit(
                finished(
                    request,
                    AgentTerminalStatus.FAILED,
                    AgentFailure(AgentFailureKind.INTERNAL, retryable = false),
                ),
            )
        }
    }

    private suspend fun runTurn(
        originalRequest: AgentRequest,
        emitEvent: suspend (AgentEvent) -> Unit,
    ) {
        val request = prepareVision(originalRequest)
        val sources = request.toolSources + HistoryReadToolSource(historyReader)
        val registry = try {
            ToolRegistry(sources, executionTimeoutMillis = toolTimeoutMillis)
        } catch (_: IllegalArgumentException) {
            throw AgentRunFailure(
                AgentTerminalStatus.FAILED,
                AgentFailure(AgentFailureKind.INTERNAL, retryable = false),
            )
        }
        val tools = if (request.primaryModel.capabilities.structuredToolCalls == CapabilityStatus.SUPPORTED) {
            registry.definitionsFor(request.toolScope).map { definition ->
                ModelToolDefinition(definition.name, definition.description, definition.parametersJsonSchema)
            }
        } else {
            emptyList()
        }
        val batches = mutableListOf<List<ModelMessage>>()
        var toolBatchSummary: String? = null
        var modelCalls = 0
        var toolCalls = 0
        var contextRecoveryUsed = false

        while (true) {
            if (modelCalls >= maxModelCalls) {
                throw AgentRunFailure(
                    AgentTerminalStatus.FAILED,
                    AgentFailure(AgentFailureKind.EXECUTION_LIMIT, retryable = false),
                )
            }
            modelCalls += 1
            var compiled = try {
                contextCompiler.compile(request, tools, batches, toolBatchSummary = toolBatchSummary)
            } catch (limit: ContextLimitExceededException) {
                throw limit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw AgentRunFailure(
                    AgentTerminalStatus.FAILED,
                    AgentFailure(AgentFailureKind.INTERNAL, retryable = false),
                )
            }
            toolBatchSummary = compiled.toolBatchSummary
            if (compiled.droppedToolBatchCount > 0) {
                repeat(compiled.droppedToolBatchCount.coerceAtMost(batches.size)) { batches.removeAt(0) }
            }

            val modelAttempt = try {
                requestModel(request, compiled, emitEvent)
            } catch (partialContextLimit: ContextLimitAfterDelta) {
                throw AgentRunFailure(
                    AgentTerminalStatus.CONTEXT_LIMIT,
                    AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
                )
            } catch (contextLimit: ModelRequestException.ContextLimit) {
                if (contextRecoveryUsed || modelCalls >= maxModelCalls) {
                    throw AgentRunFailure(
                        AgentTerminalStatus.CONTEXT_LIMIT,
                        AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
                    )
                }
                contextRecoveryUsed = true
                modelCalls += 1
                compiled = try {
                    contextCompiler.compile(
                        request,
                        tools,
                        batches,
                        forceCompaction = true,
                        toolBatchSummary = toolBatchSummary,
                    )
                } catch (limit: ContextLimitExceededException) {
                    throw limit
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw AgentRunFailure(
                        AgentTerminalStatus.FAILED,
                        AgentFailure(AgentFailureKind.INTERNAL, retryable = false),
                    )
                }
                toolBatchSummary = compiled.toolBatchSummary
                if (compiled.droppedToolBatchCount > 0) {
                    repeat(compiled.droppedToolBatchCount.coerceAtMost(batches.size)) { batches.removeAt(0) }
                }
                try {
                    requestModel(request, compiled, emitEvent)
                } catch (retryPartialLimit: ContextLimitAfterDelta) {
                    throw AgentRunFailure(
                        AgentTerminalStatus.CONTEXT_LIMIT,
                        AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
                    )
                } catch (retryFailure: ModelRequestException.ContextLimit) {
                    throw AgentRunFailure(
                        AgentTerminalStatus.CONTEXT_LIMIT,
                        AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
                    )
                } catch (retryFailure: ModelRequestException) {
                    throw retryFailure.toAgentRunFailure()
                }
            } catch (failure: ModelRequestException) {
                throw failure.toAgentRunFailure()
            }

            when (modelAttempt.finishReason) {
                ModelFinishReason.STOP -> {
                    if (modelAttempt.toolCalls.isNotEmpty()) throw protocolFailure()
                    return
                }

                ModelFinishReason.OUTPUT_LIMIT -> {
                    if (modelAttempt.toolCalls.isNotEmpty()) throw protocolFailure()
                    throw AgentRunFailure(
                        AgentTerminalStatus.OUTPUT_LIMIT,
                        AgentFailure(AgentFailureKind.OUTPUT_LIMIT, retryable = true),
                    )
                }

                ModelFinishReason.TOOL_CALLS -> {
                    if (modelAttempt.toolCalls.isEmpty()) throw protocolFailure()
                    if (modelAttempt.toolCalls.map { it.callId }.any(String::isBlank) ||
                        modelAttempt.toolCalls.map { it.callId }.toSet().size != modelAttempt.toolCalls.size
                    ) {
                        throw protocolFailure()
                    }
                    val assistantMessage = ModelMessage(
                        role = ModelRole.ASSISTANT,
                        content = modelAttempt.text.takeIf(String::isNotEmpty)?.let { listOf(Text(it)) }.orEmpty(),
                        toolCalls = modelAttempt.toolCalls,
                    )
                    val batchMessages = mutableListOf(assistantMessage)
                    var exceededToolLimit = false
                    for (call in modelAttempt.toolCalls) {
                        val result = if (toolCalls >= maxToolCalls) {
                            exceededToolLimit = true
                            ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "Tool execution limit reached.")
                        } else {
                            toolCalls += 1
                            registry.execute(
                                call = call,
                                context = ToolExecutionContext(
                                    sessionId = request.sessionId,
                                    turnSequence = request.turnSequence,
                                    attemptId = request.attemptId,
                                    historyUpperBoundExclusive = request.historyUpperBoundExclusive,
                                    scope = request.toolScope,
                                ),
                                onExecutionState = { active ->
                                    emitEvent(AgentEvent.ToolProgress(request.executionId, request.attemptId, active))
                                },
                            )
                        }
                        if (result.status != ToolResultStatus.SUCCESS) {
                            val failureText = "${call.name}: ${result.content}"
                            emitEvent(
                                AgentEvent.AssistantFeedback(
                                    request.executionId,
                                    request.attemptId,
                                    failureText.take(MAX_FEEDBACK_CHARACTERS),
                                ),
                            )
                        }
                        batchMessages += ModelMessage(
                            role = ModelRole.TOOL,
                            content = listOf(Text(result.content)),
                            toolCallId = call.callId,
                        )
                    }
                    batches += batchMessages
                    if (exceededToolLimit) {
                        throw AgentRunFailure(
                            AgentTerminalStatus.FAILED,
                            AgentFailure(AgentFailureKind.TOOL_LIMIT, retryable = false),
                        )
                    }
                }
            }
        }
    }

    private suspend fun requestModel(
        request: AgentRequest,
        compiled: CompiledContext,
        emitEvent: suspend (AgentEvent) -> Unit,
    ): ModelAttempt {
        val output = StringBuilder()
        var finish: ModelFinishReason? = null
        var calls: List<ModelToolCall> = emptyList()
        try {
            modelGateway.stream(
                ModelRequest(
                    model = request.primaryModel.reference,
                    messages = compiled.messages,
                    tools = compiled.toolDefinitions,
                    useNativeStreaming = request.primaryModel.capabilities.nativeStreaming == CapabilityStatus.SUPPORTED,
                ),
            ).collect { event ->
                when (event) {
                    is ModelStreamEvent.TextDelta -> {
                        output.append(event.text)
                        emitEvent(AgentEvent.TextDelta(request.executionId, request.attemptId, event.text))
                    }

                    is ModelStreamEvent.Completed -> {
                        finish = event.finishReason
                        calls = event.toolCalls
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (contextLimit: ModelRequestException.ContextLimit) {
            if (output.isNotEmpty()) throw ContextLimitAfterDelta(contextLimit)
            throw contextLimit
        } catch (failure: ModelRequestException) {
            throw failure
        } catch (_: Exception) {
            throw ModelRequestException.StreamInterrupted("Model stream failed.")
        }
        return ModelAttempt(finish ?: throw ModelRequestException.StreamInterrupted("Model stream ended without a terminal event."), output.toString(), calls)
    }

    private suspend fun prepareVision(original: AgentRequest): AgentRequest {
        val userTextParts = original.currentInput.content.filterIsInstance<Text>()
        val images = original.currentInput.content.filterIsInstance<ModelContentPart.Image>()
        if (images.isEmpty()) return original
        when (original.primaryModel.capabilities.vision) {
            CapabilityStatus.SUPPORTED -> return original
            CapabilityStatus.UNKNOWN -> throw AgentRunFailure(
                AgentTerminalStatus.FAILED,
                AgentFailure(AgentFailureKind.VISION_CAPABILITY_UNKNOWN, retryable = false),
            )

            CapabilityStatus.UNSUPPORTED -> Unit
        }
        val vision = original.visionModel ?: throw AgentRunFailure(
            AgentTerminalStatus.FAILED,
            AgentFailure(AgentFailureKind.VISION_MODEL_MISSING, retryable = false),
        )
        when (vision.capabilities.vision) {
            CapabilityStatus.UNSUPPORTED -> throw AgentRunFailure(
                AgentTerminalStatus.FAILED,
                AgentFailure(AgentFailureKind.VISION_MODEL_MISSING, retryable = false),
            )

            CapabilityStatus.UNKNOWN -> throw AgentRunFailure(
                AgentTerminalStatus.FAILED,
                AgentFailure(AgentFailureKind.VISION_CAPABILITY_UNKNOWN, retryable = false),
            )

            CapabilityStatus.SUPPORTED -> Unit
        }
        val description = requestVisionDescription(vision, images)
        val content = buildList<ModelContentPart> {
            addAll(userTextParts)
            add(Text("Image description:\n$description"))
        }
        return original.copy(currentInput = original.currentInput.copy(content = content))
    }

    private suspend fun requestVisionDescription(
        vision: ModelSelection,
        images: List<ModelContentPart.Image>,
    ): String {
        val output = StringBuilder()
        var finish: ModelFinishReason? = null
        try {
            modelGateway.stream(
                ModelRequest(
                    model = vision.reference,
                    messages = listOf(
                        ModelMessage(
                            role = ModelRole.SYSTEM,
                            content = listOf(Text("Describe the attached image content objectively and concisely. Do not answer a user question.")),
                        ),
                        ModelMessage(
                            role = ModelRole.USER,
                            content = listOf(Text("Describe the attached image or images.")) + images,
                        ),
                    ),
                    useNativeStreaming = false,
                    maxOutputTokens = MAX_VISION_DESCRIPTION_TOKENS,
                ),
            ).collect { event ->
                when (event) {
                    is ModelStreamEvent.TextDelta -> output.append(event.text)
                    is ModelStreamEvent.Completed -> {
                        finish = event.finishReason
                        if (event.toolCalls.isNotEmpty()) finish = null
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ModelRequestException) {
            throw failure.toAgentRunFailure()
        } catch (_: Exception) {
            throw AgentRunFailure(
                AgentTerminalStatus.INTERRUPTED,
                AgentFailure(AgentFailureKind.STREAM_INTERRUPTED, retryable = true),
            )
        }
        val text = output.toString().trim()
        if (finish != ModelFinishReason.STOP || text.isEmpty()) {
            throw AgentRunFailure(
                AgentTerminalStatus.FAILED,
                AgentFailure(AgentFailureKind.MODEL_REJECTED, retryable = false),
            )
        }
        return text.take(MAX_VISION_DESCRIPTION_CHARACTERS)
    }

    private fun ModelRequestException.toAgentRunFailure(): AgentRunFailure = when (this) {
        is ModelRequestException.Network -> AgentRunFailure(
            AgentTerminalStatus.FAILED,
            AgentFailure(AgentFailureKind.NETWORK, retryable = true),
        )

        is ModelRequestException.HttpStatus -> if (statusCode == 401 || statusCode == 403) {
            AgentRunFailure(AgentTerminalStatus.FAILED, AgentFailure(AgentFailureKind.AUTHENTICATION, retryable = false))
        } else {
            AgentRunFailure(AgentTerminalStatus.FAILED, AgentFailure(AgentFailureKind.MODEL_REJECTED, retryable = statusCode >= 500 || statusCode == 429))
        }

        is ModelRequestException.ApiRejected -> if (httpStatusCode == 401 || httpStatusCode == 403) {
            AgentRunFailure(AgentTerminalStatus.FAILED, AgentFailure(AgentFailureKind.AUTHENTICATION, retryable = false))
        } else {
            AgentRunFailure(AgentTerminalStatus.FAILED, AgentFailure(AgentFailureKind.MODEL_REJECTED, retryable = false))
        }

        is ModelRequestException.Protocol -> AgentRunFailure(
            AgentTerminalStatus.FAILED,
            AgentFailure(AgentFailureKind.PROTOCOL, retryable = false),
        )

        is ModelRequestException.StreamInterrupted -> AgentRunFailure(
            AgentTerminalStatus.INTERRUPTED,
            AgentFailure(AgentFailureKind.STREAM_INTERRUPTED, retryable = true),
        )

        is ModelRequestException.ContextLimit -> AgentRunFailure(
            AgentTerminalStatus.CONTEXT_LIMIT,
            AgentFailure(AgentFailureKind.CONTEXT_LIMIT, retryable = false),
        )

        is ModelRequestException.TimedOut -> AgentRunFailure(
            AgentTerminalStatus.TIMED_OUT,
            AgentFailure(AgentFailureKind.NETWORK, retryable = true),
        )
    }

    private fun protocolFailure() = AgentRunFailure(
        AgentTerminalStatus.FAILED,
        AgentFailure(AgentFailureKind.PROTOCOL, retryable = false),
    )

    private fun finished(
        request: AgentRequest,
        status: AgentTerminalStatus,
        failure: AgentFailure? = null,
    ) = AgentEvent.Finished(request.executionId, request.attemptId, status, failure)

    private data class ModelAttempt(
        val finishReason: ModelFinishReason,
        val text: String,
        val toolCalls: List<ModelToolCall>,
    )

    private class AgentRunFailure(
        val status: AgentTerminalStatus,
        val failure: AgentFailure,
    ) : RuntimeException()

    private class ContextLimitAfterDelta(cause: ModelRequestException.ContextLimit) : RuntimeException(cause)

    companion object {
        const val DEFAULT_MAX_MODEL_CALLS = 8
        const val DEFAULT_MAX_TOOL_CALLS = 16
        const val DEFAULT_TURN_TIMEOUT_MILLIS = 180_000L
        private const val MAX_FEEDBACK_CHARACTERS = 240
        private const val MAX_VISION_DESCRIPTION_TOKENS = 1_024
        private const val MAX_VISION_DESCRIPTION_CHARACTERS = 12_000
    }
}
