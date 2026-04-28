package com.astrbot.android.core.runtime.search.local

import com.astrbot.android.core.runtime.search.SearchNaturalLanguageParser
import java.time.LocalDate
import javax.inject.Inject

class LocalSearchRelevanceScorer @Inject constructor(
    private val freshnessValidator: FreshnessValidator,
    private val placeholderFilter: PortalPlaceholderFilter,
) {
    fun filterPlaceholders(
        query: String,
        results: List<LocalSearchResult>,
    ): List<LocalSearchResult> = results.filterNot { placeholderFilter.isPlaceholder(query, it) }

    fun assess(
        query: String,
        intent: LocalSearchIntent,
        results: List<LocalSearchResult>,
        currentDate: LocalDate = LocalDate.now(),
    ): LocalSearchAssessment {
        if (results.isEmpty()) return LocalSearchAssessment(false, "no_results")
        return when (intent) {
            LocalSearchIntent.GENERAL -> assessGeneral(query, results)
            LocalSearchIntent.NEWS -> assessNews(query, results, currentDate)
            LocalSearchIntent.WEATHER -> assessWeather(query, results)
        }
    }

    private fun assessGeneral(
        query: String,
        results: List<LocalSearchResult>,
    ): LocalSearchAssessment {
        val cjkBigrams = SearchNaturalLanguageParser.extractCjkBigrams(query)
        val latinTokens = SearchNaturalLanguageParser.extractLatinTokens(query)
        if (cjkBigrams.size < 3 && latinTokens.isEmpty()) {
            return LocalSearchAssessment(true, "short_query_bypass", 1.0, results.size)
        }
        val scores = results.map { scoreQueryMatch(query, it) }
        val accepted = scores.count { it >= GENERAL_THRESHOLD }
        return if (accepted > 0 || latinTokens.isNotEmpty()) {
            LocalSearchAssessment(true, "passed_threshold", scores.maxOrNull() ?: 0.0, maxOf(accepted, 1))
        } else {
            LocalSearchAssessment(false, "no_result_passed_threshold", scores.maxOrNull() ?: 0.0)
        }
    }

    private fun assessNews(
        query: String,
        results: List<LocalSearchResult>,
        currentDate: LocalDate,
    ): LocalSearchAssessment {
        val targetYears = SearchNaturalLanguageParser.extractExplicitYears(query)
        var accepted = 0
        var dateMismatches = 0
        var bestScore = 0.0

        for (result in results) {
            val text = "${result.title} ${result.snippet} ${result.source}"
            val resultYears = SearchNaturalLanguageParser.extractExplicitYears(text)
            val dateMatches = targetYears.isEmpty() ||
                resultYears.isEmpty() ||
                resultYears.any { it in targetYears }
            if (!dateMatches) dateMismatches += 1

            val score = scoreQueryMatch(query, result)
            bestScore = maxOf(bestScore, score)
            val hasNews = hasAnySignal(text, newsTerms) || result.module.contains("news", ignoreCase = true)
            val fresh = freshnessValidator.hasFreshNewsSignal(text, result.publishedAt, currentDate)
            val portal = placeholderFilter.isNewsPortalPlaceholder(result)
            if (dateMatches && hasNews && fresh && !portal && score >= NEWS_QUERY_THRESHOLD) {
                accepted += 1
            }
        }

        return when {
            accepted > 0 -> LocalSearchAssessment(true, "news_result_passed_threshold", bestScore, accepted)
            targetYears.isNotEmpty() && dateMismatches == results.size -> {
                LocalSearchAssessment(false, "target_date_mismatch", bestScore)
            }
            else -> LocalSearchAssessment(false, "no_news_result_passed_threshold", bestScore)
        }
    }

    private fun assessWeather(
        query: String,
        results: List<LocalSearchResult>,
    ): LocalSearchAssessment {
        val locationTokens = SearchNaturalLanguageParser.extractCjkBigrams(query)
            .filterNot { it in weatherStopBigrams }
            .toSet()
        var accepted = 0
        var bestScore = 0.0
        for (result in results) {
            val text = "${result.title} ${result.snippet}"
            val score = scoreQueryMatch(query, result)
            bestScore = maxOf(bestScore, score)
            val hasWeather = hasAnySignal(text, weatherTerms) || result.module.contains("weather", ignoreCase = true)
            val hasLocation = locationTokens.isEmpty() || locationTokens.any { text.contains(it) }
            if (hasWeather && hasLocation) accepted += 1
        }
        return if (accepted > 0) {
            LocalSearchAssessment(true, "weather_result_passed_threshold", bestScore, accepted)
        } else {
            LocalSearchAssessment(false, "no_weather_result_passed_threshold", bestScore)
        }
    }

    private fun scoreQueryMatch(
        query: String,
        result: LocalSearchResult,
    ): Double {
        val text = "${result.title} ${result.snippet}"
        val lowerText = text.lowercase()
        val cjkBigrams = SearchNaturalLanguageParser.extractCjkBigrams(query)
        val latinTokens = SearchNaturalLanguageParser.extractLatinTokens(query)
        val cjkRatio = if (cjkBigrams.isEmpty()) {
            0.0
        } else {
            cjkBigrams.count { text.contains(it) }.toDouble() / cjkBigrams.size
        }
        val latinRatio = if (latinTokens.isEmpty()) {
            0.0
        } else {
            latinTokens.count { SearchNaturalLanguageParser.containsWholeWord(lowerText, it) }.toDouble() /
                latinTokens.size
        }
        return maxOf(cjkRatio, latinRatio)
    }

    private fun hasAnySignal(text: String, signals: Set<String>): Boolean {
        val lower = text.lowercase()
        return signals.any { signal ->
            if (signal.any { it.code > 127 }) text.contains(signal) else SearchNaturalLanguageParser.containsWholeWord(lower, signal)
        }
    }
}

class FreshnessValidator @Inject constructor() {
    fun hasFreshNewsSignal(
        text: String,
        publishedAt: String?,
        currentDate: LocalDate,
    ): Boolean {
        val currentYear = currentDate.year.toString()
        val currentMonthDay = "${currentDate.monthValue}\u6708${currentDate.dayOfMonth}\u65e5"
        val explicitYears = SearchNaturalLanguageParser.extractExplicitYears("$text ${publishedAt.orEmpty()}")
        if (explicitYears.isNotEmpty() && currentDate.year !in explicitYears) return false
        return publishedAt?.contains(currentYear) == true ||
            text.contains(currentYear) ||
            text.contains(currentMonthDay) ||
            freshTerms.any { text.contains(it) || text.lowercase().contains(it) } ||
            datePatterns.any { it.containsMatchIn(text) }
    }
}

class PortalPlaceholderFilter @Inject constructor() {
    fun isPlaceholder(
        query: String,
        result: LocalSearchResult,
    ): Boolean {
        val normalizedTitle = result.title.normalizeLocalSearchText()
        val normalizedQuery = query.normalizeLocalSearchText()
        val normalizedSnippet = result.snippet.normalizeLocalSearchText()
        val normalizedUrl = result.url.lowercase()
        val searchPortal = normalizedUrl.contains("bing.com/search?") ||
            normalizedUrl.contains("sogou.com/web?") ||
            normalizedUrl.contains("baidu.com/s?")
        val genericSnippet = normalizedSnippet in setOf(
            "qq\u6d4f\u89c8\u5668\u641c\u7d22".normalizeLocalSearchText(),
            "\u641c\u72d7\u641c\u7d22".normalizeLocalSearchText(),
            "\u767e\u5ea6\u641c\u7d22".normalizeLocalSearchText(),
            "bing",
            "\u641c\u7d22".normalizeLocalSearchText(),
        )
        return searchPortal && (normalizedTitle == normalizedQuery || genericSnippet)
    }

    fun isNewsPortalPlaceholder(result: LocalSearchResult): Boolean {
        val text = "${result.title} ${result.snippet}".lowercase()
        return newsPlaceholderTerms.any { text.contains(it) }
    }
}

private val freshTerms = setOf(
    "\u4eca\u5929",
    "\u4eca\u65e5",
    "\u521a\u521a",
    "\u6700\u65b0",
    "\u5b9e\u65f6",
    "\u5206\u949f\u524d",
    "\u5c0f\u65f6\u524d",
    "\u6628\u65e5",
    "today",
    "latest",
    "breaking",
    "updated",
    "hours ago",
    "minutes ago",
)

private val newsTerms = setOf(
    "\u65b0\u95fb",
    "\u5feb\u8baf",
    "\u5934\u6761",
    "\u8d44\u8baf",
    "\u62a5\u9053",
    "\u6d88\u606f",
    "news",
    "headline",
    "breaking",
    "report",
)

private val weatherTerms = setOf(
    "\u5929\u6c14",
    "\u9884\u62a5",
    "\u6c14\u6e29",
    "\u6e29\u5ea6",
    "\u5c0f\u96e8",
    "\u4e2d\u96e8",
    "\u591a\u4e91",
    "\u6674",
    "\u98ce",
    "\u6e7f\u5ea6",
    "weather",
    "forecast",
    "temperature",
    "rain",
    "cloudy",
    "sunny",
    "humidity",
    "wind",
)

private val newsPlaceholderTerms = setOf(
    "\u5e73\u53f0",
    "\u4e0b\u8f7d",
    "\u767e\u79d1",
    "\u4e13\u9898",
    "\u5168\u96c6",
    "\u4ecb\u7ecd",
    "app download",
)

private val weatherStopBigrams = setOf(
    "\u5929\u6c14",
    "\u6c14\u9884",
    "\u9884\u62a5",
    "\u660e\u5929",
    "\u4eca\u5929",
)

private val datePatterns = listOf(
    Regex("""\b(19|20)\d{2}[-/.]\d{1,2}[-/.]\d{1,2}\b"""),
    Regex("""(19|20)\d{2}\u5e74\d{1,2}\u6708\d{1,2}\u65e5"""),
    Regex("""\b\d{1,2}:\d{2}\b"""),
)

private const val GENERAL_THRESHOLD = 0.25
private const val NEWS_QUERY_THRESHOLD = 0.15
