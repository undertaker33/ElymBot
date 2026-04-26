package com.astrbot.android.runtime.search.local

import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import com.astrbot.android.core.runtime.search.local.LocalSearchResultMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchResultMergerTest {
    private val merger = LocalSearchResultMerger()

    @Test
    fun stripsTrackingParametersAndMergesEquivalentUrls() {
        val results = merger.merge(
            listOf(
                result("Kotlin Coroutines Guide", "https://example.com/guide?utm_source=x&foo=1", "bing"),
                result("Kotlin coroutines guide", "https://example.com/guide?foo=1&utm_medium=y", "sogou"),
            ),
        )

        assertEquals(1, results.size)
        assertEquals("https://example.com/guide?foo=1", results.single().url)
        assertTrue(results.single().metadata.getValue("engines").contains("sogou"))
    }

    @Test
    fun dedupesNearDuplicateTitles() {
        val results = merger.merge(
            listOf(
                result("OpenAI releases new model today", "https://news.example.com/a", "bing", module = "news"),
                result("OpenAI release new model today", "https://news.example.com/b", "sogou", module = "organic"),
                result("Different source on OpenAI", "https://other.example.com/c", "sogou"),
            ),
        )

        assertEquals(2, results.size)
        assertEquals("bing", results.first().engine)
    }

    private fun result(
        title: String,
        url: String,
        engine: String,
        module: String = "organic",
    ) = LocalSearchResult(
        title = title,
        url = url,
        snippet = "$title snippet",
        engine = engine,
        module = module,
        source = "source-$engine",
        scoreHints = mapOf("intent" to if (module == "news") 0.9 else 0.6),
    )
}
