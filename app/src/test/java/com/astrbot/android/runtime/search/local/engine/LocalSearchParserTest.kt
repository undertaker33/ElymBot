package com.astrbot.android.runtime.search.local.engine

import com.astrbot.android.core.runtime.search.local.parser.BaiduWebParser
import com.astrbot.android.core.runtime.search.local.parser.BingNewsResultParser
import com.astrbot.android.core.runtime.search.local.parser.BingResultParser
import com.astrbot.android.core.runtime.search.local.parser.DuckDuckGoLiteParser
import com.astrbot.android.core.runtime.search.local.parser.SogouResultParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchParserTest {
    @Test
    fun bingParserExtractsOrganicResults() {
        val results = BingResultParser().parse(BING_HTML, maxResults = 3)

        assertEquals(1, results.size)
        assertEquals("Example", results[0].title)
        assertEquals("https://example.com/page", results[0].url)
        assertEquals("bing", results[0].engine)
    }

    @Test
    fun sogouParserExtractsOrganicResults() {
        val results = SogouResultParser().parse(SOGOU_HTML, maxResults = 3, searchUrl = "https://www.sogou.com/web")

        assertEquals(1, results.size)
        assertEquals("Sogou Example", results[0].title)
        assertEquals("https://example.cn/sogou", results[0].url)
        assertEquals("sogou", results[0].engine)
    }

    @Test
    fun sogouParserExtractsNewsVideoResults() {
        val results = SogouResultParser().parse(
            SOGOU_NEWS_VIDEO_HTML,
            maxResults = 3,
            searchUrl = "https://www.sogou.com/web?query=x",
        )

        assertEquals(1, results.size)
        assertEquals("2026\u798f\u5dde\u201c\u4e94\u4e00\u201d\u6587\u65c5\u4e3b\u9898\u6d3b\u52a8\u542f\u5e55", results[0].title)
        assertEquals("https://newsa.html5.qq.com/v1/share-video?vid=679266844503057771", results[0].url)
        assertEquals("\u4f01\u9e45\u53f7\u00b7\u65b0\u534e\u793e\u89c6\u9891", results[0].source)
        assertEquals("sogou_news_video", results[0].module)
    }

    @Test
    fun duckDuckGoLiteParserExtractsStaticResults() {
        val results = DuckDuckGoLiteParser().parse(DUCK_HTML, maxResults = 3)

        assertEquals(1, results.size)
        assertEquals("Duck Result", results[0].title)
        assertEquals("https://duck.example/result", results[0].url)
        assertEquals("duckduckgo_lite", results[0].engine)
    }

    @Test
    fun baiduWebParserExtractsOrdinaryWebResults() {
        val results = BaiduWebParser().parse(BAIDU_HTML, maxResults = 3)

        assertEquals(1, results.size)
        assertEquals("Baidu Result", results[0].title)
        assertEquals("https://baidu.example/result", results[0].url)
        assertEquals("baidu_web", results[0].engine)
    }

    @Test
    fun bingNewsParserExtractsNewsResults() {
        val results = BingNewsResultParser().parse(BING_NEWS_HTML, maxResults = 3)

        assertEquals(1, results.size)
        assertEquals("Space News", results[0].title)
        assertEquals("https://news.example/space", results[0].url)
        assertEquals("bing_news", results[0].engine)
    }

    @Test
    fun parsersReturnEmptyForBlankAndAntiBotPages() {
        val antiBot = """
            <html><title>verify</title><body>
            <form id="captcha">Please complete the captcha to continue.</form>
            </body></html>
        """.trimIndent()

        assertTrue(BingResultParser().parse("", 5).isEmpty())
        assertTrue(SogouResultParser().parse(antiBot, 5, "https://www.sogou.com/web").isEmpty())
        assertTrue(DuckDuckGoLiteParser().parse(antiBot, 5).isEmpty())
        assertTrue(BaiduWebParser().parse(antiBot, 5).isEmpty())
        assertTrue(BingNewsResultParser().parse(antiBot, 5).isEmpty())
    }
}

internal val BING_HTML = """
    <html><body>
      <ol id="b_results">
        <li class="b_algo">
          <h2><a href="https://example.com/page">Example</a></h2>
          <div class="b_caption"><p>Example snippet for Bing.</p></div>
        </li>
      </ol>
    </body></html>
""".trimIndent()

internal val SOGOU_HTML = """
    <html><body>
      <div class="vrwrap">
        <h3><a href="https://example.cn/sogou">Sogou Example</a></h3>
        <p class="str_info">Sogou snippet.</p>
      </div>
    </body></html>
""".trimIndent()

internal val SOGOU_NEWS_VIDEO_HTML = """
    <html><body>
      <a href="./tc?url=https%3A%2F%2Fnewsa.html5.qq.com%2Fv1%2Fshare-video%3Fvid%3D679266844503057771&amp;linkid=cover_1">
        <div class="video-desc__videoDesc_812e">
          <div class="video-desc__videoTitle_812e click-sugg-title">
            <em>2026${"\u798f\u5dde"}</em>${"\u201c\u4e94\u4e00\u201d\u6587\u65c5\u4e3b\u9898\u6d3b\u52a8\u542f\u5e55"}
          </div>
          <span class="video-desc__descContent_812e">${"\u4f01\u9e45\u53f7\u00b7\u65b0\u534e\u793e\u89c6\u9891"}</span>
        </div>
      </a>
    </body></html>
""".trimIndent()

internal val DUCK_HTML = """
    <html><body>
      <div class="result">
        <a class="result__a" href="https://duck.example/result">Duck Result</a>
        <a class="result__snippet">Duck snippet.</a>
      </div>
    </body></html>
""".trimIndent()

internal val BAIDU_HTML = """
    <html><body>
      <div class="result c-container">
        <h3 class="t"><a href="https://baidu.example/result">Baidu Result</a></h3>
        <div class="c-abstract">Baidu ordinary web snippet.</div>
      </div>
    </body></html>
""".trimIndent()

internal val BING_NEWS_HTML = """
    <html><body>
      <div class="news-card newsitem">
        <a class="title" href="https://news.example/space">Space News</a>
        <div class="snippet">Space news snippet.</div>
        <div class="source">Example News</div>
      </div>
    </body></html>
""".trimIndent()
