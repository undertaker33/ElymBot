package com.elymbot.android.core.runtime.search.local.engine

import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.search.local.EngineSearchRequest
import com.elymbot.android.core.runtime.search.local.EngineSearchResult
import com.elymbot.android.core.runtime.search.local.SearchEngineCapability
import com.elymbot.android.core.runtime.search.local.parser.BingResultParser

class BingEngineAdapter(
    private val transport: RuntimeNetworkTransport,
    private val parser: BingResultParser,
) : SearchEngineAdapter {
    override val id: String = "bing"
    override val displayName: String = "Bing"
    override val capabilities: Set<SearchEngineCapability> = setOf(
        SearchEngineCapability.GENERAL,
        SearchEngineCapability.ENGLISH,
        SearchEngineCapability.WEATHER,
        SearchEngineCapability.MOBILE_HTML,
    )

    override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
        val networkRequest = buildRequest(request)
        val body = transport.execute(networkRequest).bodyString
        val results = parser.parse(body, request.maxResults, engineId = id, searchUrl = networkRequest.url)
        return EngineSearchResult(
            engineId = id,
            results = results,
            rawModules = results.groupingBy { it.module }.eachCount(),
        )
    }

    fun buildRequest(request: EngineSearchRequest): RuntimeNetworkRequest {
        val url = "https://www.bing.com/search?q=${encodeQuery(request.query)}&count=${request.maxResults}"
        return webSearchGetRequest(url, acceptLanguage(request.language))
    }
}
