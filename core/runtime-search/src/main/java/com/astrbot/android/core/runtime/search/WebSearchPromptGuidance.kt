package com.astrbot.android.core.runtime.search

/**
 * Centralized natural-language web-search guidance for LLM prompts.
 *
 * Keep this provider-agnostic: model-specific native search behavior must not
 * leak into runtime prompts. The host exposes one `web_search` tool surface.
 */
object WebSearchPromptGuidance {
    fun forMessage(
        text: String,
        strings: WebSearchPromptStringProvider,
    ): String? {
        return forTrigger(WebSearchTriggerRules.evaluate(text), strings)
    }

    fun forTrigger(
        trigger: WebSearchTriggerRuleResult,
        strings: WebSearchPromptStringProvider,
    ): String? {
        return when (trigger.intent) {
            WebSearchTriggerIntent.NEWS -> strings.guidanceFor(WebSearchTriggerIntent.NEWS)
            WebSearchTriggerIntent.WEATHER -> strings.guidanceFor(WebSearchTriggerIntent.WEATHER)
            WebSearchTriggerIntent.REALTIME -> strings.guidanceFor(WebSearchTriggerIntent.REALTIME)
            WebSearchTriggerIntent.NONE -> null
        }
    }
}

interface WebSearchPromptStringProvider {
    fun guidanceFor(intent: WebSearchTriggerIntent): String?

    fun newsDirectDeliveryCommentary(
        factText: String,
        sent: Boolean,
    ): String
}
