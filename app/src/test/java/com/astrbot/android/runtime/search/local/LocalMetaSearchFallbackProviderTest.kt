package com.astrbot.android.runtime.search.local

import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchResult
import com.astrbot.android.core.runtime.search.local.FreshnessValidator
import com.astrbot.android.core.runtime.search.local.LocalMetaSearchFallbackProvider
import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import com.astrbot.android.core.runtime.search.local.LocalSearchResultMerger
import com.astrbot.android.core.runtime.search.local.LocalSearchRelevanceScorer
import com.astrbot.android.core.runtime.search.local.PortalPlaceholderFilter
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.SearchEngineRegistry
import com.astrbot.android.core.runtime.search.local.LocalSearchPolicyResolver
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlRequest
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlResponse
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlerLite
import com.astrbot.android.core.runtime.search.local.engine.SearchEngineAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMetaSearchFallbackProviderTest {
    @Test
    fun returnsMergedResultsWhenOneEngineFails() = runBlocking {
        val provider = provider(
            FailingEngine("bing"),
            StaticEngine(
                "sogou",
                listOf(
                    LocalSearchResult(
                        title = "OpenAI 今日发布新模型新闻",
                        url = "https://news.example.com/a?utm_source=x",
                        snippet = "今天 10:30 更新：OpenAI 发布最新消息。",
                        engine = "sogou",
                        module = "news",
                        source = "sogou news",
                    ),
                ),
            ),
        )

        val response = provider.search(UnifiedSearchRequest("OpenAI 今日新闻", maxResults = 3)) as SearchProviderResult.Success

        assertEquals(1, response.results.size)
        assertTrue(response.diagnostics.any { it.providerId == "bing" && it.status == SearchAttemptStatus.NETWORK_ERROR })
        assertTrue(response.diagnostics.any { it.providerId == "sogou" && it.status == SearchAttemptStatus.SUCCESS })
    }

    @Test
    fun returnsEmptySuccessWithDiagnosticsWhenAllResultsAreLowRelevanceForNews() = runBlocking {
        val provider = provider(
            StaticEngine(
                "bing_news",
                listOf(
                    LocalSearchResult(
                        title = "OpenAI company profile",
                        url = "https://example.com/profile",
                        snippet = "An encyclopedia style company overview from 2021.",
                        engine = "bing_news",
                        module = "bing_news_card",
                    ),
                ),
            ),
        )

        val response = provider.search(UnifiedSearchRequest("OpenAI 今日新闻", maxResults = 3)) as SearchProviderResult.Success

        assertTrue(response.results.isEmpty())
        assertTrue(response.diagnostics.any { it.status == SearchAttemptStatus.LOW_RELEVANCE })
    }

    private fun provider(vararg engines: SearchEngineAdapter): LocalMetaSearchFallbackProvider {
        val placeholderFilter = PortalPlaceholderFilter()
        return LocalMetaSearchFallbackProvider(
            registry = SearchEngineRegistry(engines.toList()),
            policyResolver = LocalSearchPolicyResolver(),
            merger = LocalSearchResultMerger(),
            relevanceScorer = LocalSearchRelevanceScorer(FreshnessValidator(), placeholderFilter),
            contentCrawler = NoopContentCrawler,
        )
    }

    private class StaticEngine(
        override val id: String,
        private val results: List<LocalSearchResult>,
    ) : SearchEngineAdapter {
        override val displayName: String = "$id test"
        override val capabilities: Set<SearchEngineCapability> = SearchEngineCapability.entries.toSet()
        override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
            return EngineSearchResult(engineId = id, results = results)
        }
    }

    private class FailingEngine(override val id: String) : SearchEngineAdapter {
        override val displayName: String = "$id failing"
        override val capabilities: Set<SearchEngineCapability> = SearchEngineCapability.entries.toSet()
        override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
            error("network down")
        }
    }

    private object NoopContentCrawler : ContentCrawlerLite {
        override suspend fun crawl(request: ContentCrawlRequest): ContentCrawlResponse {
            return ContentCrawlResponse(request.query, pages = emptyList(), diagnostics = emptyList())
        }
    }
}
