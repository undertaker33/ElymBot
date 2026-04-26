package com.astrbot.android.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent
import com.astrbot.android.core.runtime.search.local.crawl.QueryAwareContentPruner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryAwareContentPrunerTest {
    private val pruner = QueryAwareContentPruner()

    @Test
    fun ranksCjkBigramsAndLatinTokensAboveUnrelatedParagraphs() {
        val paragraphs = listOf(
            "Unrelated paragraph about cooking recipes and weekend plans.",
            "ElymBot Android 本地 web_search 使用 SearXNG 结果，并通过 Crawl4AI lite 思路抽取正文。",
            "A technical paragraph about query aware pruning, BM25-lite scoring, and markdown output.",
        )

        val result = pruner.prune(
            paragraphs = paragraphs,
            query = "ElymBot Android Crawl4AI 正文抽取",
            intent = NaturalLanguageSearchIntent.REALTIME,
            currentDate = "2026-04-26",
            maxTextChars = 180,
        )

        assertTrue(result.selectedParagraphs.first().contains("ElymBot Android"))
        assertTrue(result.text.length <= 180)
        assertFalse(result.text.contains("cooking recipes"))
    }

    @Test
    fun intentAndCurrentDateBoostFreshNewsParagraphs() {
        val paragraphs = listOf(
            "Background from 2024 says the project had an older search fallback design.",
            "2026-04-26 latest news: Android web_search fallback now prefers SearXNG and local extraction.",
        )

        val result = pruner.prune(
            paragraphs = paragraphs,
            query = "latest Android web_search news",
            intent = NaturalLanguageSearchIntent.NEWS,
            currentDate = "2026-04-26",
            maxTextChars = 240,
        )

        assertTrue(result.selectedParagraphs.first().startsWith("2026-04-26 latest news"))
    }
}
