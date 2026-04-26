package com.astrbot.android.core.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchResult
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.parser.SogouResultParser

class SogouEngineAdapter(
    private val transport: RuntimeNetworkTransport,
    private val parser: SogouResultParser,
) : SearchEngineAdapter {
    override val id: String = "sogou"
    override val displayName: String = "Sogou"
    override val capabilities: Set<SearchEngineCapability> = setOf(
        SearchEngineCapability.GENERAL,
        SearchEngineCapability.NEWS,
        SearchEngineCapability.WEATHER,
        SearchEngineCapability.CJK,
        SearchEngineCapability.MOBILE_HTML,
    )

    override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
        val networkRequest = buildRequest(request)
        val body = transport.execute(networkRequest).bodyString
        val results = parser.parse(
            html = body,
            maxResults = request.maxResults,
            searchUrl = networkRequest.url,
        )
        return EngineSearchResult(
            engineId = id,
            results = results,
            rawModules = results.groupingBy { it.module }.eachCount(),
        )
    }

    fun buildRequest(request: EngineSearchRequest): RuntimeNetworkRequest {
        val url = "https://www.sogou.com/web?query=${encodeQuery(request.query)}"
        return webSearchGetRequest(url, acceptLanguage(request.language))
    }
}
