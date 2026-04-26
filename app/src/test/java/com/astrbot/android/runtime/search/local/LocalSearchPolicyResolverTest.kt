package com.astrbot.android.runtime.search.local

import com.astrbot.android.core.runtime.search.local.LocalSearchIntent
import com.astrbot.android.core.runtime.search.local.LocalSearchPolicyResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchPolicyResolverTest {
    private val resolver = LocalSearchPolicyResolver()

    @Test
    fun generalCjkQueryPrefersCjkFirstOrderAndAllowsLastResort() {
        val policy = resolver.resolve("今天 北京 有什么 展览")

        assertEquals(LocalSearchIntent.GENERAL, policy.intent)
        assertEquals(listOf("sogou", "bing", "baidu_web_lite", "duckduckgo_lite"), policy.engineOrder)
        assertTrue(policy.allowLowRelevanceFallback)
        assertEquals(2, policy.maxConcurrentEngines)
    }

    @Test
    fun newsEnglishQueryPrefersNewsEngineAndRejectsLowRelevanceFallback() {
        val policy = resolver.resolve("latest OpenAI news today")

        assertEquals(LocalSearchIntent.NEWS, policy.intent)
        assertEquals(listOf("bing_news", "duckduckgo_lite", "bing"), policy.engineOrder)
        assertFalse(policy.allowLowRelevanceFallback)
        assertTrue(policy.requiresRelevantResults)
    }

    @Test
    fun weatherCjkQueryRejectsLowRelevanceFallback() {
        val policy = resolver.resolve("上海 明天 天气")

        assertEquals(LocalSearchIntent.WEATHER, policy.intent)
        assertEquals(listOf("sogou", "bing", "baidu_web_lite"), policy.engineOrder)
        assertFalse(policy.allowLowRelevanceFallback)
        assertTrue(policy.requiresRelevantResults)
    }

    @Test
    fun weatherEngineRequestDropsAbsoluteDateToKeepWeatherCardsAvailable() {
        val request = resolver.requestFor(
            query = "\u798f\u5dde\u5929\u6c14 2026\u5e744\u670826\u65e5",
            intent = LocalSearchIntent.WEATHER,
            maxResults = 3,
            locale = "zh-CN",
        )
        val isoDateRequest = resolver.requestFor(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5 2026-04-27",
            intent = LocalSearchIntent.WEATHER,
            maxResults = 3,
            locale = "zh-CN",
        )

        assertEquals("\u798f\u5dde\u5929\u6c14", request.query)
        assertEquals("\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5", isoDateRequest.query)
    }
}
