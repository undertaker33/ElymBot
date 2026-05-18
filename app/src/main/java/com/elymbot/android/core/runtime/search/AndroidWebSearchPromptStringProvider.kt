package com.elymbot.android.core.runtime.search

import androidx.annotation.StringRes
import com.elymbot.android.AppStringResolver
import com.elymbot.android.AppStrings
import com.elymbot.android.R
import com.elymbot.android.core.runtime.context.RuntimeWebSearchPromptIntent
import com.elymbot.android.core.runtime.context.RuntimeWebSearchPromptStringProvider
import javax.inject.Inject

class AndroidWebSearchPromptStringProvider : WebSearchPromptStringProvider, RuntimeWebSearchPromptStringProvider {
    private val stringResolver: AppStringResolver?

    @Inject
    constructor(stringResolver: AppStringResolver) {
        this.stringResolver = stringResolver
    }

    constructor() {
        this.stringResolver = null
    }

    override fun guidanceFor(intent: WebSearchTriggerIntent): String? {
        val resId = when (intent) {
            WebSearchTriggerIntent.NEWS -> R.string.web_search_prompt_news_guidance
            WebSearchTriggerIntent.WEATHER -> R.string.web_search_prompt_weather_guidance
            WebSearchTriggerIntent.REALTIME -> R.string.web_search_prompt_realtime_guidance
            WebSearchTriggerIntent.NONE -> return null
        }
        return string(resId, WEB_SEARCH_TOOL_NAME).ifBlank { null }
    }

    override fun guidanceFor(intent: RuntimeWebSearchPromptIntent): String? {
        return guidanceFor(
            when (intent) {
                RuntimeWebSearchPromptIntent.NEWS -> WebSearchTriggerIntent.NEWS
                RuntimeWebSearchPromptIntent.WEATHER -> WebSearchTriggerIntent.WEATHER
                RuntimeWebSearchPromptIntent.REALTIME -> WebSearchTriggerIntent.REALTIME
                RuntimeWebSearchPromptIntent.NONE -> WebSearchTriggerIntent.NONE
            },
        )
    }

    override fun newsDirectDeliveryCommentary(
        factText: String,
        sent: Boolean,
    ): String {
        val status = string(
            if (sent) {
                R.string.web_search_prompt_direct_delivery_status_sent
            } else {
                R.string.web_search_prompt_direct_delivery_status_failed
            },
        )
        return string(
            R.string.web_search_prompt_news_direct_delivery_commentary,
            status,
            factText,
        )
    }

    private fun string(
        @StringRes resId: Int,
        vararg args: Any,
    ): String {
        val resolver = stringResolver ?: return AppStrings::class.simpleName.orEmpty().take(0)
        return resolver.get(resId, *args)
    }

    private companion object {
        const val WEB_SEARCH_TOOL_NAME = "web_search"
    }
}
