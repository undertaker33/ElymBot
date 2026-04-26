package com.astrbot.android.runtime.search.local

import com.astrbot.android.core.runtime.search.local.FreshnessValidator
import com.astrbot.android.core.runtime.search.local.LocalSearchIntent
import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import com.astrbot.android.core.runtime.search.local.LocalSearchRelevanceScorer
import com.astrbot.android.core.runtime.search.local.PortalPlaceholderFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchRelevanceTest {
    private val placeholderFilter = PortalPlaceholderFilter()
    private val scorer = LocalSearchRelevanceScorer(FreshnessValidator(), placeholderFilter)

    @Test
    fun todayNewsRejectsPortalIntroDownloadEncyclopediaTopicAndOldYearResults() {
        val rejected = listOf(
            result("Tencent News App download", "https://app.example.com", "download client for more news"),
            result("OpenAI encyclopedia", "https://baike.example.com", "OpenAI company encyclopedia introduction"),
            result("OpenAI news topic collection", "https://topic.example.com", "historical news topic collection"),
            result("OpenAI released GPT-4 news 2023-03-15", "https://old.example.com", "old news review"),
        )

        val assessment = scorer.assess("OpenAI 今日新闻", LocalSearchIntent.NEWS, rejected)

        assertFalse(assessment.accepted)
    }

    @Test
    fun todayNewsAcceptsFreshArticleWithNewsAndTimelinessSignals() {
        val assessment = scorer.assess(
            "OpenAI 今日新闻",
            LocalSearchIntent.NEWS,
            listOf(result("OpenAI 今日发布新模型新闻", "https://news.example.com/a", "今天 10:30 更新：OpenAI 发布最新消息。")),
        )

        assertTrue(assessment.accepted)
    }

    @Test
    fun weatherRequiresLocationAndStrongWeatherSignal() {
        assertFalse(
            scorer.assess(
                "上海天气",
                LocalSearchIntent.WEATHER,
                listOf(result("天气频道", "https://weather.example.com", "全国天气预报入口")),
            ).accepted,
        )
        assertTrue(
            scorer.assess(
                "上海天气",
                LocalSearchIntent.WEATHER,
                listOf(result("上海天气预报", "https://weather.example.com/shanghai", "上海今天晴，气温 22C，湿度45%，东北风")),
            ).accepted,
        )
    }

    @Test
    fun newsAcceptsFreshLocalVideoModules() {
        val assessment = scorer.assess(
            "\u798f\u5dde \u672c\u5730\u65b0\u95fb 2026\u5e744\u6708",
            LocalSearchIntent.NEWS,
            listOf(
                result(
                    "2026\u798f\u5dde\u201c\u4e94\u4e00\u201d\u6587\u65c5\u4e3b\u9898\u6d3b\u52a8\u542f\u5e55",
                    "https://newsa.html5.qq.com/v1/share-video?vid=1",
                    "\u4f01\u9e45\u53f7\u00b7\u65b0\u534e\u793e\u89c6\u9891",
                ).copy(module = "sogou_news_video"),
            ),
        )

        assertTrue(assessment.accepted)
    }

    @Test
    fun portalPlaceholderFilterRemovesSearchPlaceholders() {
        assertTrue(placeholderFilter.isPlaceholder("OpenAI 今日新闻", result("OpenAI 今日新闻", "https://bing.com/search?q=x", "Bing")))
        assertTrue(placeholderFilter.isNewsPortalPlaceholder(result("天气 App 下载", "https://example.com/app", "下载客户端")))
    }

    private fun result(title: String, url: String, snippet: String) = LocalSearchResult(
        title = title,
        url = url,
        snippet = snippet,
        engine = "bing",
        module = "news",
        source = "test",
    )
}
