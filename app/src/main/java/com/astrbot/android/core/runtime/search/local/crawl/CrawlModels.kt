package com.astrbot.android.core.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent

interface ContentCrawlerLite {
    suspend fun crawl(request: ContentCrawlRequest): ContentCrawlResponse
}

data class ContentCrawlRequest(
    val query: String,
    val intent: NaturalLanguageSearchIntent = NaturalLanguageSearchIntent.NONE,
    val maxPages: Int = 3,
    val maxBytes: Int = 512 * 1024,
    val maxTextChars: Int = 6_000,
    val currentDate: String = "",
    val candidates: List<ContentCrawlCandidate>,
)

data class ContentCrawlCandidate(
    val title: String,
    val url: String,
    val snippet: String = "",
    val source: String = "",
    val publishedAt: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class ContentCrawlResponse(
    val query: String,
    val pages: List<CrawledPage>,
    val diagnostics: List<CrawlDiagnostic>,
) {
    val success: Boolean get() = pages.isNotEmpty()
}

data class CrawledPage(
    val title: String,
    val url: String,
    val canonicalUrl: String?,
    val publishedAt: String?,
    val text: String,
    val markdown: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class CrawlDiagnostic(
    val url: String,
    val status: CrawlDiagnosticStatus,
    val reason: String,
    val traceId: String? = null,
    val durationMs: Long? = null,
)

enum class CrawlDiagnosticStatus {
    SUCCESS,
    BLOCKED_URL,
    FETCH_FAILED,
    UNSUPPORTED_CONTENT_TYPE,
    TOO_LARGE,
    EMPTY_CONTENT,
    PARSE_ERROR,
}

data class UrlSafetyDecision(
    val allowed: Boolean,
    val reason: String = "",
)

data class FetchedContent(
    val url: String,
    val contentType: String?,
    val charset: String,
    val text: String,
    val traceId: String?,
    val durationMs: Long?,
)

sealed class ContentFetchResult {
    data class Success(val content: FetchedContent) : ContentFetchResult()
    data class Failure(
        val status: CrawlDiagnosticStatus,
        val reason: String,
        val traceId: String? = null,
        val durationMs: Long? = null,
    ) : ContentFetchResult()
}

data class ExtractedContent(
    val title: String,
    val canonicalUrl: String?,
    val publishedAt: String?,
    val paragraphs: List<String>,
)

data class PrunedContent(
    val selectedParagraphs: List<String>,
    val text: String,
)

data class MarkdownLiteDocument(
    val text: String,
    val metadata: Map<String, String>,
)
