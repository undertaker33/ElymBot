package com.astrbot.android.core.runtime.search.local

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlCandidate
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlRequest
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlerLite
import com.astrbot.android.core.runtime.search.local.crawl.CrawlDiagnosticStatus
import com.astrbot.android.core.runtime.search.local.engine.SearchEngineAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import javax.inject.Inject

class LocalMetaSearchFallbackProvider @Inject constructor(
    private val registry: SearchEngineRegistry,
    private val policyResolver: LocalSearchPolicyResolver,
    private val merger: LocalSearchResultMerger,
    private val relevanceScorer: LocalSearchRelevanceScorer,
    private val contentCrawler: ContentCrawlerLite,
) : SearchProvider {
    override val providerId: String = "local_meta_search"
    override val providerName: String = "Local meta search"

    override suspend fun search(request: UnifiedSearchRequest): SearchProviderResult.Success {
        val query = request.query.trim()
        val maxResults = request.maxResults.coerceIn(1, 10)
        val policy = policyResolver.resolve(query)
        val engines = registry.ordered(policy)
        val engineRequest = policyResolver.requestFor(
            query = query,
            intent = policy.intent,
            maxResults = maxResults,
            locale = request.locale,
        )
        val diagnostics = mutableListOf<SearchAttemptDiagnostic>()
        val accepted = mutableListOf<LocalSearchResult>()
        var lowRelevanceFallback: List<LocalSearchResult> = emptyList()

        AppLogger.append(
            "WebSearch: provider=$providerId intent=${policy.intent.name} " +
                "engine_order=[${policy.engineOrder.joinToString(",")}] query='$query' max_results=$maxResults",
        )

        for (chunk in engines.chunked(policy.maxConcurrentEngines.coerceAtLeast(1))) {
            val executions = executeChunk(chunk, engineRequest)
            for (execution in executions) {
                val engine = execution.engine
                val failure = execution.failure
                if (failure != null) {
                    diagnostics += engine.failureDiagnostic(failure)
                    continue
                }

                val result = execution.result ?: continue
                diagnostics += result.diagnostics.map { it.toSearchDiagnostic() }
                val rawResults = result.results.take(maxResults)
                val filtered = relevanceScorer.filterPlaceholders(query, rawResults)
                if (filtered.isEmpty()) {
                    diagnostics += engine.diagnostic(
                        status = if (rawResults.isEmpty()) SearchAttemptStatus.EMPTY_RESULTS else SearchAttemptStatus.LOW_RELEVANCE,
                        reason = if (rawResults.isEmpty()) "empty_results" else "placeholder_results_only",
                        resultCount = rawResults.size,
                    )
                    continue
                }

                val assessment = relevanceScorer.assess(
                    query = query,
                    intent = policy.intent,
                    results = filtered,
                    currentDate = engineRequest.currentDate,
                )
                diagnostics += engine.diagnostic(
                    status = if (assessment.accepted) SearchAttemptStatus.SUCCESS else SearchAttemptStatus.LOW_RELEVANCE,
                    reason = assessment.reason,
                    resultCount = filtered.size,
                    relevanceAccepted = assessment.accepted,
                )
                AppLogger.append(
                    "WebSearch: provider=$providerId engine=${engine.id} status=${if (assessment.accepted) "accepted" else "rejected"} " +
                        "relevance_reason=${assessment.reason} result_count=${filtered.size}",
                )

                if (assessment.accepted) {
                    accepted += filtered.map { resultItem ->
                        resultItem.copy(scoreHints = resultItem.scoreHints + ("intent" to assessment.score))
                    }
                } else if (policy.allowLowRelevanceFallback && lowRelevanceFallback.isEmpty()) {
                    lowRelevanceFallback = filtered
                }
            }
        }

        val selected = when {
            accepted.isNotEmpty() -> accepted
            policy.allowLowRelevanceFallback && lowRelevanceFallback.isNotEmpty() -> {
                diagnostics += SearchAttemptDiagnostic(
                    providerId = providerId,
                    providerName = providerName,
                    status = SearchAttemptStatus.SUCCESS,
                    reason = "low_relevance_last_resort",
                    resultCount = lowRelevanceFallback.size,
                    relevanceAccepted = true,
                )
                lowRelevanceFallback
            }
            else -> emptyList()
        }
        val enriched = maybeEnrich(
            query = query,
            intent = policy.intent,
            currentDate = engineRequest.currentDate,
            results = merger.merge(selected).take(maxResults),
            crawlMaxPages = policy.crawlMaxPages,
            diagnostics = diagnostics,
        )
        val unifiedResults = enriched.mapIndexed { index, result -> result.toUnified(index + 1) }

        AppLogger.append(
            "WebSearch: provider=$providerId final_result_count=${unifiedResults.size} " +
                "diagnostics=[${diagnostics.joinToString(",") { "${it.providerId}:${it.status.name}:${it.reason}" }}]",
        )
        AppLogger.flush()

        return SearchProviderResult.Success(
            results = unifiedResults,
            diagnostics = diagnostics,
            relevanceAccepted = unifiedResults.isNotEmpty(),
            providerOverride = providerId,
        )
    }

    private suspend fun executeChunk(
        engines: List<SearchEngineAdapter>,
        request: EngineSearchRequest,
    ): List<EngineExecution> = coroutineScope {
        engines.map { engine ->
            async {
                try {
                    EngineExecution(engine = engine, result = engine.search(request))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    EngineExecution(engine = engine, failure = e)
                }
            }
        }.awaitAll()
    }

    private suspend fun maybeEnrich(
        query: String,
        intent: LocalSearchIntent,
        currentDate: LocalDate,
        results: List<LocalSearchResult>,
        crawlMaxPages: Int,
        diagnostics: MutableList<SearchAttemptDiagnostic>,
    ): List<LocalSearchResult> {
        if (crawlMaxPages <= 0 || results.isEmpty()) return results
        val response = contentCrawler.crawl(
            ContentCrawlRequest(
                query = query,
                intent = intent.toNaturalLanguageIntent(),
                maxPages = crawlMaxPages,
                currentDate = currentDate.toString(),
                candidates = results.take(crawlMaxPages).map { result ->
                    ContentCrawlCandidate(
                        title = result.title,
                        url = result.url,
                        snippet = result.snippet,
                        source = result.source,
                        publishedAt = result.publishedAt,
                        metadata = result.metadata,
                    )
                },
            ),
        )
        diagnostics += response.diagnostics.map { diagnostic ->
            SearchAttemptDiagnostic(
                providerId = "content_crawler_lite",
                providerName = "Content crawler lite",
                status = diagnostic.status.toSearchStatus(),
                reason = diagnostic.reason,
                traceId = diagnostic.traceId,
                durationMs = diagnostic.durationMs,
                resultCount = if (diagnostic.status == CrawlDiagnosticStatus.SUCCESS) 1 else 0,
                relevanceAccepted = diagnostic.status == CrawlDiagnosticStatus.SUCCESS,
            )
        }
        if (!response.success) return results
        val pagesByUrl = response.pages.associateBy { it.url }
        return results.map { result ->
            val page = pagesByUrl[result.url] ?: pagesByUrl[result.metadata["canonicalUrl"]] ?: return@map result
            result.copy(
                title = result.title.ifBlank { page.title },
                snippet = result.snippet.takeIf { it.length >= 80 } ?: page.text.take(700),
                publishedAt = result.publishedAt ?: page.publishedAt,
                metadata = result.metadata + mapOf(
                    "crawl_enriched" to "true",
                    "crawl_chars" to page.text.length.toString(),
                ),
            )
        }
    }

    private fun LocalSearchIntent.toNaturalLanguageIntent(): com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent {
        return when (this) {
            LocalSearchIntent.NEWS -> com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent.NEWS
            LocalSearchIntent.WEATHER -> com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent.WEATHER
            LocalSearchIntent.GENERAL -> com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent.REALTIME
        }
    }

    private fun CrawlDiagnosticStatus.toSearchStatus(): SearchAttemptStatus {
        return when (this) {
            CrawlDiagnosticStatus.SUCCESS -> SearchAttemptStatus.SUCCESS
            CrawlDiagnosticStatus.BLOCKED_URL -> SearchAttemptStatus.UNSUPPORTED
            CrawlDiagnosticStatus.FETCH_FAILED -> SearchAttemptStatus.NETWORK_ERROR
            CrawlDiagnosticStatus.UNSUPPORTED_CONTENT_TYPE -> SearchAttemptStatus.UNSUPPORTED
            CrawlDiagnosticStatus.TOO_LARGE -> SearchAttemptStatus.UNSUPPORTED
            CrawlDiagnosticStatus.EMPTY_CONTENT -> SearchAttemptStatus.EMPTY_RESULTS
            CrawlDiagnosticStatus.PARSE_ERROR -> SearchAttemptStatus.PARSE_ERROR
        }
    }

    private fun LocalSearchDiagnostic.toSearchDiagnostic(): SearchAttemptDiagnostic {
        return SearchAttemptDiagnostic(
            providerId = providerId,
            providerName = providerName,
            status = status.toSearchStatus(),
            reason = reason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            traceId = traceId,
            durationMs = durationMs,
            resultCount = resultCount,
            relevanceAccepted = relevanceAccepted,
        )
    }

    private fun LocalSearchDiagnosticStatus.toSearchStatus(): SearchAttemptStatus {
        return when (this) {
            LocalSearchDiagnosticStatus.SUCCESS -> SearchAttemptStatus.SUCCESS
            LocalSearchDiagnosticStatus.UNSUPPORTED,
            LocalSearchDiagnosticStatus.SKIPPED,
            -> SearchAttemptStatus.UNSUPPORTED
            LocalSearchDiagnosticStatus.TIMEOUT -> SearchAttemptStatus.TIMEOUT
            LocalSearchDiagnosticStatus.NETWORK_ERROR -> SearchAttemptStatus.NETWORK_ERROR
            LocalSearchDiagnosticStatus.HTTP_ERROR -> SearchAttemptStatus.HTTP_ERROR
            LocalSearchDiagnosticStatus.EMPTY_RESULTS -> SearchAttemptStatus.EMPTY_RESULTS
            LocalSearchDiagnosticStatus.LOW_RELEVANCE -> SearchAttemptStatus.LOW_RELEVANCE
            LocalSearchDiagnosticStatus.PARSE_ERROR -> SearchAttemptStatus.PARSE_ERROR
        }
    }

    private fun SearchEngineAdapter.diagnostic(
        status: SearchAttemptStatus,
        reason: String,
        errorCode: String? = null,
        errorMessage: String? = null,
        resultCount: Int = 0,
        relevanceAccepted: Boolean = false,
    ): SearchAttemptDiagnostic {
        return SearchAttemptDiagnostic(
            providerId = id,
            providerName = displayName,
            status = status,
            reason = reason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            resultCount = resultCount,
            relevanceAccepted = relevanceAccepted,
        )
    }

    private fun SearchEngineAdapter.failureDiagnostic(error: Exception): SearchAttemptDiagnostic {
        val status = if (error is RuntimeNetworkException) {
            error.failure.toSearchStatus()
        } else {
            SearchAttemptStatus.NETWORK_ERROR
        }
        return diagnostic(
            status = status,
            reason = error.message ?: error.javaClass.simpleName,
            errorCode = if (error is RuntimeNetworkException) error.failure.errorCode() else error.javaClass.simpleName,
            errorMessage = error.message,
        )
    }

    private fun RuntimeNetworkFailure.toSearchStatus(): SearchAttemptStatus {
        return when (this) {
            is RuntimeNetworkFailure.ConnectTimeout,
            is RuntimeNetworkFailure.ReadTimeout,
            -> SearchAttemptStatus.TIMEOUT
            is RuntimeNetworkFailure.Http -> SearchAttemptStatus.HTTP_ERROR
            is RuntimeNetworkFailure.Protocol -> SearchAttemptStatus.PARSE_ERROR
            is RuntimeNetworkFailure.Cancelled,
            is RuntimeNetworkFailure.Dns,
            is RuntimeNetworkFailure.Tls,
            is RuntimeNetworkFailure.Unknown,
            -> SearchAttemptStatus.NETWORK_ERROR
        }
    }

    private fun RuntimeNetworkFailure.errorCode(): String {
        return when (this) {
            is RuntimeNetworkFailure.Http -> statusCode.toString()
            else -> this::class.java.simpleName
        }
    }

    private data class EngineExecution(
        val engine: SearchEngineAdapter,
        val result: EngineSearchResult? = null,
        val failure: Exception? = null,
    )
}
