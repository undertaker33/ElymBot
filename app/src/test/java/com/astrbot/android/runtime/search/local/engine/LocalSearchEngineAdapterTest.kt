package com.astrbot.android.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.core.runtime.network.SseEvent
import com.astrbot.android.core.runtime.search.local.EngineSearchRequest
import com.astrbot.android.core.runtime.search.local.LocalSearchIntent
import com.astrbot.android.core.runtime.search.local.SearchEngineCapability
import com.astrbot.android.core.runtime.search.local.engine.BaiduWebLiteEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.BingEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.BingNewsEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.DuckDuckGoLiteEngineAdapter
import com.astrbot.android.core.runtime.search.local.parser.BaiduWebParser
import com.astrbot.android.core.runtime.search.local.parser.BingNewsResultParser
import com.astrbot.android.core.runtime.search.local.parser.BingResultParser
import com.astrbot.android.core.runtime.search.local.parser.DuckDuckGoLiteParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchEngineAdapterTest {
    @Test
    fun bingAdapterUsesWebSearchCapabilityTimeoutAndHeaders() = runTest {
        val transport = RecordingTransport(ADAPTER_BING_HTML)
        val adapter = BingEngineAdapter(transport, BingResultParser())

        val response = adapter.search(
            EngineSearchRequest(
                query = "android search",
                maxResults = 3,
                language = "zh-CN",
                intent = LocalSearchIntent.GENERAL,
            ),
        )

        val request = transport.lastRequest
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, request.capability)
        assertEquals(RuntimeTimeoutProfile.WEB_SEARCH, request.timeoutProfile)
        assertTrue(request.url.startsWith("https://www.bing.com/search?"))
        assertTrue(request.url.contains("q=android+search"))
        assertEquals("Example", response.results.single().title)
    }

    @Test
    fun duckDuckGoLiteAdapterBuildsStaticHtmlRequest() = runTest {
        val transport = RecordingTransport(ADAPTER_DUCK_HTML)
        val adapter = DuckDuckGoLiteEngineAdapter(transport, DuckDuckGoLiteParser())

        adapter.search(
            EngineSearchRequest(
                query = "kotlin coroutine",
                maxResults = 2,
                language = "en-US",
                intent = LocalSearchIntent.GENERAL,
            ),
        )

        val request = transport.lastRequest
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, request.capability)
        assertEquals(RuntimeTimeoutProfile.WEB_SEARCH, request.timeoutProfile)
        assertTrue(request.url.startsWith("https://html.duckduckgo.com/html/?"))
        assertTrue(request.url.contains("q=kotlin+coroutine"))
        assertEquals("en-US,en;q=0.9,zh-CN;q=0.7,zh;q=0.6", request.headers["Accept-Language"])
    }

    @Test
    fun baiduWebAdapterBuildsLiteWebRequest() = runTest {
        val transport = RecordingTransport(ADAPTER_BAIDU_HTML)
        val adapter = BaiduWebLiteEngineAdapter(transport, BaiduWebParser())

        adapter.search(EngineSearchRequest(query = "天气", maxResults = 1, intent = LocalSearchIntent.GENERAL))

        val request = transport.lastRequest
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, request.capability)
        assertEquals(RuntimeTimeoutProfile.WEB_SEARCH, request.timeoutProfile)
        assertTrue(request.url.startsWith("https://www.baidu.com/s?"))
        assertTrue(request.url.contains("wd=%E5%A4%A9%E6%B0%94"))
        assertTrue(request.url.contains("rn=1"))
    }

    @Test
    fun bingNewsAdapterUsesNewsVertical() = runTest {
        val transport = RecordingTransport(ADAPTER_BING_NEWS_HTML)
        val adapter = BingNewsEngineAdapter(transport, BingNewsResultParser())

        val response = adapter.search(
            EngineSearchRequest(
                query = "space",
                maxResults = 2,
                intent = LocalSearchIntent.NEWS,
            ),
        )

        val request = transport.lastRequest
        assertTrue(request.url.startsWith("https://www.bing.com/news/search?"))
        assertTrue(adapter.capabilities.contains(SearchEngineCapability.NEWS))
        assertEquals("Space News", response.results.single().title)
    }

    private class RecordingTransport(
        private val body: String,
    ) : RuntimeNetworkTransport {
        lateinit var lastRequest: RuntimeNetworkRequest

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            lastRequest = request
            return RuntimeNetworkResponse(
                statusCode = 200,
                headers = emptyMap(),
                bodyBytes = body.encodeToByteArray(),
                traceId = "test",
                durationMs = 1,
            )
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> = emptyFlow()

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> = emptyFlow()
    }
}

private const val ADAPTER_BING_HTML = """
<html><body>
  <li class="b_algo"><h2><a href="https://example.test/a">Example</a></h2><div class="b_caption"><p>Snippet</p></div></li>
</body></html>
"""

private const val ADAPTER_DUCK_HTML = """
<html><body>
  <div class="result"><a class="result__a" href="https://example.test/kotlin">Kotlin</a><a class="result__snippet">Coroutine guide</a></div>
</body></html>
"""

private const val ADAPTER_BAIDU_HTML = """
<html><body>
  <div class="result"><h3><a href="https://example.test/weather">天气</a></h3><div class="c-abstract">天气预报</div></div>
</body></html>
"""

private const val ADAPTER_BING_NEWS_HTML = """
<html><body>
  <div class="news-card"><a class="title" href="https://example.test/news">Space News</a><div class="snippet">latest update</div></div>
</body></html>
"""
