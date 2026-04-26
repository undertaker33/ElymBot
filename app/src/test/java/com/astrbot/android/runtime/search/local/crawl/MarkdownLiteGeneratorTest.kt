package com.astrbot.android.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.local.crawl.ExtractedContent
import com.astrbot.android.core.runtime.search.local.crawl.MarkdownLiteGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteGeneratorTest {
    private val generator = MarkdownLiteGenerator()

    @Test
    fun emitsReadableTextWithoutExternalLinksInBody() {
        val markdown = generator.generate(
            extracted = ExtractedContent(
                title = "Story Title",
                canonicalUrl = "https://example.com/story",
                publishedAt = "2026-04-26",
                paragraphs = listOf(
                    "Read the full post at https://tracking.example/path and keep only prose.",
                    "Second paragraph keeps plain content.",
                ),
            ),
            paragraphs = listOf(
                "Read the full post at https://tracking.example/path and keep only prose.",
                "Second paragraph keeps plain content.",
            ),
        )

        assertTrue(markdown.text.startsWith("# Story Title"))
        assertFalse(markdown.text.contains("https://tracking.example/path"))
        assertTrue(markdown.metadata["url"] == "https://example.com/story")
    }
}
