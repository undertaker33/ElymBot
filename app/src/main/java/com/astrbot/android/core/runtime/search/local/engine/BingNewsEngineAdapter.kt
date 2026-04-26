package com.astrbot.android.core.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchResult
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.parser.BingNewsResultParser

class BingNewsEngineAdapter(
    private val transport: RuntimeNetworkTransport,
    private val parser: BingNewsResultParser,
) : SearchEngineAdapter {
    override val id: String = "bing_news"
    override val displayName: String = "Bing News"
    override val capabilities: Set<SearchEngineCapability> = setOf(
        SearchEngineCapability.NEWS,
        SearchEngineCapability.ENGLISH,
        SearchEngineCapability.MOBILE_HTML,
    )

    override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
        val networkRequest = buildRequest(request)
        val body = transport.execute(networkRequest).bodyString
        val results = parser.parse(body, request.maxResults)
        return EngineSearchResult(
            engineId = id,
            results = results,
            rawModules = results.groupingBy { it.module }.eachCount(),
        )
    }

    fun buildRequest(request: EngineSearchRequest): RuntimeNetworkRequest {
        val url = "https://www.bing.com/news/search?q=${encodeQuery(request.query)}&count=${request.maxResults}"
        return webSearchGetRequest(url, acceptLanguage(request.language))
    }
}
