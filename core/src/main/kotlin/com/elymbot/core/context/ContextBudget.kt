package com.elymbot.core.context

import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelToolDefinition
import com.elymbot.core.contract.ModelRole

data class ContextBudget(
    val contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    val outputReserveTokens: Int = DEFAULT_OUTPUT_RESERVE_TOKENS,
    val safetyMarginTokens: Int = DEFAULT_SAFETY_MARGIN_TOKENS,
    val compressionTriggerRatio: Double = DEFAULT_COMPRESSION_TRIGGER_RATIO,
    val recentCompleteTurnsToKeep: Int = DEFAULT_RECENT_COMPLETE_TURNS,
    val maxHistoryTokensToRead: Int = DEFAULT_MAX_HISTORY_TOKENS_TO_READ,
    val maxHistoryPages: Int = DEFAULT_MAX_HISTORY_PAGES,
    val historyPageSize: Int = DEFAULT_HISTORY_PAGE_SIZE,
    val maxSummaryOutputTokens: Int = DEFAULT_MAX_SUMMARY_OUTPUT_TOKENS,
) {
    val inputLimitTokens: Int
        get() = contextWindowTokens - outputReserveTokens - safetyMarginTokens

    val compressionTriggerTokens: Int
        get() = (inputLimitTokens * compressionTriggerRatio).toInt()

    init {
        require(contextWindowTokens > 0)
        require(outputReserveTokens >= 0)
        require(safetyMarginTokens >= 0)
        require(outputReserveTokens + safetyMarginTokens < contextWindowTokens)
        require(compressionTriggerRatio in 0.1..1.0)
        require(recentCompleteTurnsToKeep >= 0)
        require(maxHistoryTokensToRead > 0)
        require(maxHistoryPages > 0)
        require(historyPageSize > 0)
        require(maxSummaryOutputTokens > 0)
    }

    companion object {
        const val DEFAULT_CONTEXT_WINDOW_TOKENS = 64 * 1024
        const val DEFAULT_OUTPUT_RESERVE_TOKENS = 4 * 1024
        const val DEFAULT_SAFETY_MARGIN_TOKENS = 2 * 1024
        const val DEFAULT_COMPRESSION_TRIGGER_RATIO = 0.8
        const val DEFAULT_RECENT_COMPLETE_TURNS = 2
        const val DEFAULT_MAX_HISTORY_TOKENS_TO_READ = 256 * 1024
        const val DEFAULT_MAX_HISTORY_PAGES = 64
        const val DEFAULT_HISTORY_PAGE_SIZE = 64
        const val DEFAULT_MAX_SUMMARY_OUTPUT_TOKENS = 1024
    }
}

fun interface ModelContextBudgetProvider {
    fun budgetFor(model: ModelReference): ContextBudget
}

class DefaultModelContextBudgetProvider(
    private val fallback: ContextBudget = ContextBudget(),
) : ModelContextBudgetProvider {
    override fun budgetFor(model: ModelReference): ContextBudget = fallback
}

fun interface ContextTokenCounter {
    fun estimate(messages: List<ModelMessage>, tools: List<ModelToolDefinition>): Int
}

/** Conservative fallback until a model-specific tokenizer is available. */
class ConservativeContextTokenCounter(
    private val imageTokenEstimate: Int = 2048,
    private val messageOverheadTokens: Int = 4,
    private val toolOverheadTokens: Int = 8,
) : ContextTokenCounter {
    init {
        require(imageTokenEstimate > 0)
        require(messageOverheadTokens >= 0)
        require(toolOverheadTokens >= 0)
    }

    override fun estimate(messages: List<ModelMessage>, tools: List<ModelToolDefinition>): Int {
        val messageCost = messages.sumOf { message ->
            messageOverheadTokens + message.content.sumOf(::estimateContent) +
                message.toolCalls.sumOf { call ->
                    textTokens(call.callId) + textTokens(call.name) + textTokens(call.argumentsJson)
                } + if (message.role == ModelRole.TOOL) textTokens(message.toolCallId.orEmpty()) else 0
        }
        val toolCost = tools.sumOf { tool ->
            toolOverheadTokens + textTokens(tool.name) + textTokens(tool.description) +
                textTokens(tool.parametersJsonSchema)
        }
        return messageCost + toolCost
    }

    private fun estimateContent(part: ModelContentPart): Int = when (part) {
        is ModelContentPart.Text -> textTokens(part.value)
        is ModelContentPart.Image -> imageTokenEstimate
    }

    private fun textTokens(value: String): Int {
        if (value.isEmpty()) return 0
        val byteCount = value.toByteArray(Charsets.UTF_8).size
        return (byteCount + 2) / 3
    }
}
