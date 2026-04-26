package com.astrbot.android.core.runtime.search.local

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent
import com.astrbot.android.core.runtime.search.SearchNaturalLanguageParser
import java.time.LocalDate
import javax.inject.Inject

class LocalSearchPolicyResolver @Inject constructor() {
    fun resolve(query: String): LocalSearchPolicy {
        val parsed = SearchNaturalLanguageParser.parse(query)
        val intent = when (parsed.intent) {
            NaturalLanguageSearchIntent.NEWS -> LocalSearchIntent.NEWS
            NaturalLanguageSearchIntent.WEATHER -> LocalSearchIntent.WEATHER
            NaturalLanguageSearchIntent.NONE,
            NaturalLanguageSearchIntent.REALTIME,
            -> LocalSearchIntent.GENERAL
        }
        val cjk = parsed.containsCjkCharacters
        return when (intent) {
            LocalSearchIntent.WEATHER -> LocalSearchPolicy(
                intent = intent,
                engineOrder = if (cjk) {
                    listOf("sogou", "bing", "baidu_web_lite")
                } else {
                    listOf("bing", "duckduckgo_lite")
                },
                allowLowRelevanceFallback = false,
                requiresRelevantResults = true,
                crawlMaxPages = 0,
            )
            LocalSearchIntent.NEWS -> LocalSearchPolicy(
                intent = intent,
                engineOrder = if (cjk) {
                    listOf("sogou", "baidu_web_lite", "bing_news", "bing")
                } else {
                    listOf("bing_news", "duckduckgo_lite", "bing")
                },
                allowLowRelevanceFallback = false,
                requiresRelevantResults = true,
                crawlMaxPages = 2,
            )
            LocalSearchIntent.GENERAL -> LocalSearchPolicy(
                intent = intent,
                engineOrder = if (cjk) {
                    listOf("sogou", "bing", "baidu_web_lite", "duckduckgo_lite")
                } else {
                    listOf("bing", "duckduckgo_lite", "sogou", "baidu_web_lite")
                },
                allowLowRelevanceFallback = true,
                requiresRelevantResults = false,
                crawlMaxPages = 1,
            )
        }
    }

    fun requestFor(
        query: String,
        intent: LocalSearchIntent,
        maxResults: Int,
        locale: String?,
    ): EngineSearchRequest {
        val engineQuery = if (intent == LocalSearchIntent.WEATHER) {
            normalizeWeatherEngineQuery(query)
        } else {
            query
        }
        return EngineSearchRequest(
            query = engineQuery,
            intent = intent,
            maxResults = maxResults,
            language = locale,
            timeRange = if (intent == LocalSearchIntent.NEWS) SearchTimeRange.DAY else null,
            currentDate = LocalDate.now(),
        )
    }

    private fun normalizeWeatherEngineQuery(query: String): String {
        return query
            .replace(Regex("""(19|20)\d{2}\s*年\s*\d{1,2}\s*月\s*\d{1,2}\s*日?"""), " ")
            .replace(Regex("""(19|20)\d{2}[-/.]\d{1,2}[-/.]\d{1,2}"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { query.trim() }
    }
}

internal fun String.normalizeLocalSearchText(): String {
    return lowercase()
        .replace(Regex("\\s+"), "")
        .trim()
}
