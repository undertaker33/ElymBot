package com.astrbot.android.core.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.EngineSearchResult
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.parser.BaiduWebParser

class BaiduWebLiteEngineAdapter(
    private val transport: RuntimeNetworkTransport,
    private val parser: BaiduWebParser,
) : SearchEngineAdapter {
    override val id: String = "baidu_web_lite"
    override val displayName: String = "Baidu Web Lite"
    override val capabilities: Set<SearchEngineCapability> = setOf(
        SearchEngineCapability.GENERAL,
        SearchEngineCapability.NEWS,
        SearchEngineCapability.CJK,
        SearchEngineCapability.MOBILE_HTML,
    )

    override suspend fun search(request: EngineSearchRequest): EngineSearchResult {
        val networkRequest = buildRequest(request)
        val body = transport.execute(networkRequest).bodyString
        val results = parser.parse(body, request.maxResults)
            .map { it.copy(engine = id) }
        return EngineSearchResult(
            engineId = id,
            results = results,
            rawModules = results.groupingBy { it.module }.eachCount(),
        )
    }

    fun buildRequest(request: EngineSearchRequest): RuntimeNetworkRequest {
        val url = "https://www.baidu.com/s?wd=${encodeQuery(request.query)}&rn=${request.maxResults}"
        return webSearchGetRequest(url, acceptLanguage(request.language))
    }
}
