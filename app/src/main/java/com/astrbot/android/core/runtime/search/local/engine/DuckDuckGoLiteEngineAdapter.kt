package com.astrbot.android.core.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchResult
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.parser.DuckDuckGoLiteParser

class DuckDuckGoLiteEngineAdapter(
    private val transport: RuntimeNetworkTransport,
    private val parser: DuckDuckGoLiteParser,
) : SearchEngineAdapter {
    override val id: String = "duckduckgo_lite"
    override val displayName: String = "DuckDuckGo Lite"
    override val capabilities: Set<SearchEngineCapability> = setOf(
        SearchEngineCapability.GENERAL,
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
        val url = "https://html.duckduckgo.com/html/?q=${encodeQuery(request.query)}"
        return webSearchGetRequest(url, acceptLanguage(request.language ?: "en-US"))
    }
}
