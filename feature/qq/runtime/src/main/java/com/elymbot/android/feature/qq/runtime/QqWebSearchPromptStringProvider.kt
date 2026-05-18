package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.core.runtime.search.WebSearchPromptStringProvider
import com.elymbot.android.core.runtime.search.WebSearchTriggerIntent

internal class QqWebSearchPromptStringProvider : WebSearchPromptStringProvider {
    override fun guidanceFor(intent: WebSearchTriggerIntent): String? {
        return when (intent) {
            WebSearchTriggerIntent.NEWS ->
                "When the user asks for news, call web_search first and use its returned facts."
            WebSearchTriggerIntent.WEATHER ->
                "When the user asks for weather, call web_search first and answer from the returned forecast facts."
            WebSearchTriggerIntent.REALTIME ->
                "When the user asks for current or time-sensitive facts, call web_search first."
            WebSearchTriggerIntent.NONE -> null
        }
    }

    override fun newsDirectDeliveryCommentary(factText: String, sent: Boolean): String {
        val status = if (sent) "sent to QQ" else "could not be sent to QQ"
        return "News search facts were $status before this follow-up: $factText"
    }
}
