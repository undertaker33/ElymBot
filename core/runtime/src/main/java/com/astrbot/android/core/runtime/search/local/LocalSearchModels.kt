package com.astrbot.android.core.runtime.search.local

import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import java.time.LocalDate

enum class LocalSearchIntent {
    GENERAL,
    NEWS,
    WEATHER,
}

enum class SearchEngineCapability {
    GENERAL,
    NEWS,
    WEATHER,
    CJK,
    ENGLISH,
    TIME_RANGE,
    SAFE_SEARCH,
    MOBILE_HTML,
    JSON_ENDPOINT,
}

enum class SearchSafeSearch {
    OFF,
    MODERATE,
    STRICT,
}

enum class SearchTimeRange {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

enum class UserAgentProfile {
    ANDROID_MOBILE,
    DESKTOP,
}

data class EngineSearchRequest(
    val query: String,
    val intent: LocalSearchIntent,
    val maxResults: Int,
    val page: Int = 1,
    val language: String? = null,
    val timeRange: SearchTimeRange? = null,
    val safeSearch: SearchSafeSearch = SearchSafeSearch.MODERATE,
    val currentDate: LocalDate = LocalDate.now(),
    val userAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_MOBILE,
)

data class EngineSearchResult(
    val engineId: String,
    val results: List<LocalSearchResult>,
    val diagnostics: List<LocalSearchDiagnostic> = emptyList(),
    val rawModules: Map<String, Int> = emptyMap(),
    val durationMs: Long = 0,
)

data class LocalSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "",
    val engine: String = "",
    val module: String = "",
    val publishedAt: String? = null,
    val rank: Int = 0,
    val scoreHints: Map<String, Double> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toUnified(index: Int): UnifiedSearchResult {
        return UnifiedSearchResult(
            index = index,
            title = title,
            url = url,
            snippet = snippet,
            source = source,
            providerId = engine,
            publishedAt = publishedAt,
            metadata = (
                mapOf(
                    "engine" to engine,
                    "module" to module,
                ) + metadata
                ).filterValues(String::isNotBlank),
        )
    }
}

data class LocalSearchPolicy(
    val intent: LocalSearchIntent,
    val engineOrder: List<String>,
    val allowLowRelevanceFallback: Boolean,
    val requiresRelevantResults: Boolean,
    val maxConcurrentEngines: Int = 2,
    val crawlMaxPages: Int = 0,
)

data class LocalSearchAssessment(
    val accepted: Boolean,
    val reason: String,
    val score: Double = 0.0,
    val acceptedCount: Int = 0,
)

data class LocalSearchDiagnostic(
    val providerId: String,
    val providerName: String,
    val status: LocalSearchDiagnosticStatus,
    val reason: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val traceId: String? = null,
    val durationMs: Long? = null,
    val resultCount: Int = 0,
    val relevanceAccepted: Boolean = false,
)

enum class LocalSearchDiagnosticStatus {
    SUCCESS,
    UNSUPPORTED,
    TIMEOUT,
    NETWORK_ERROR,
    HTTP_ERROR,
    EMPTY_RESULTS,
    LOW_RELEVANCE,
    PARSE_ERROR,
    SKIPPED,
}

internal fun List<LocalSearchResult>.moduleSummary(): String {
    return if (isEmpty()) {
        "none"
    } else {
        groupingBy { it.module.ifBlank { "${it.engine}_unknown" } }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }
    }
}
