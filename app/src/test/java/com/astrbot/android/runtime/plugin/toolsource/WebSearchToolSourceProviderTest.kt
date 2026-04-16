package com.astrbot.android.runtime.plugin.toolsource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolSourceProviderTest {

    @Test
    fun chinese_query_prefers_sogou_before_bing() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "福州明天天气预报",
            maxResults = 3,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(WebSearchResult(title = "Bing result", url = "https://bing.test", snippet = "bing"))
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(WebSearchResult(title = "福州天气", url = "https://sogou.test", snippet = "18~25 小雨转阴"))
            },
        )

        assertEquals(listOf("sogou"), called)
        assertTrue(text.contains("福州天气"))
        assertTrue(text.contains("18~25"))
    }

    @Test
    fun chinese_query_falls_back_to_bing_when_sogou_is_empty() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "异环 开服时间 游戏 上线",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(WebSearchResult(title = "异环公测时间", url = "https://bing.test/game", snippet = "异环将于近期上线"))
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                emptyList()
            },
        )

        assertEquals(listOf("sogou", "bing"), called)
        assertTrue(text.contains("异环公测时间"))
    }

    @Test
    fun sogou_weather_card_is_extracted_from_special_module() {
        val html = """
            <html>
              <body>
                <div class="location-module">
                  <div class="location-tab">
                    <ul><li>福州</li></ul>
                  </div>
                </div>
                <div class="weather210208 special-subject">
                  <div class="content-main">
                    <div class="content-mes">
                      <div class="temperature js_shikuang"><p>18~25</p><i></i></div>
                      <div class="wind-box"><span class="weath">小雨转阴</span></div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val results = parseSogouResults(
            html = html,
            query = "福州明天天气预报",
            maxResults = 5,
            searchUrl = "https://www.sogou.com/web?query=%E7%A6%8F%E5%B7%9E%E6%98%8E%E5%A4%A9%E5%A4%A9%E6%B0%94%E9%A2%84%E6%8A%A5",
        )

        assertTrue(results.isNotEmpty())
        assertEquals("福州天气", results.first().title)
        assertTrue(results.first().snippet.contains("18~25"))
        assertTrue(results.first().snippet.contains("小雨转阴"))
    }
}
