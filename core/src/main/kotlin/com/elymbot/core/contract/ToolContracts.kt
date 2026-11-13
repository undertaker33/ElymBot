package com.elymbot.core.contract

enum class ToolScope {
    MAIN_SESSION,
    PERSONA_SESSION,
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
    val scopes: Set<ToolScope>,
)

data class ToolExecutionContext(
    val sessionId: String,
    val turnSequence: Long,
    val attemptId: String,
    val historyUpperBoundExclusive: Long,
    val scope: ToolScope,
)

enum class ToolResultStatus {
    SUCCESS,
    REJECTED,
    FAILED,
}

data class ToolExecutionResult(
    val callId: String,
    val status: ToolResultStatus,
    val content: String,
)

data class ToolProgress(val text: String)

/** One stable tool source. Execution is suspendable and observes caller cancellation. */
interface ToolSource {
    val definition: ToolDefinition

    suspend fun execute(
        call: ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (ToolProgress) -> Unit,
    ): ToolExecutionResult
}
