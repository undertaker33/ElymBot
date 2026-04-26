package com.astrbot.android.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.local.crawl.HtmlContentExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentExtractorTest {
    private val extractor = HtmlContentExtractor()

    @Test
    fun removesUnsafeAndBoilerplateElementsWhileExtractingMetadata() {
        val html = """
            <html>
              <head>
                <title>Canonical Title</title>
                <link rel="canonical" href="/story/main">
                <meta property="article:published_time" content="2026-04-26T08:30:00Z">
              </head>
              <body>
                <header>site header should vanish</header>
                <nav>menu should vanish</nav>
                <article>
                  <h1>Readable Story</h1>
                  <p>第一段正文包含 ElymBot 本地搜索 和 Crawl4AI lite 的说明。</p>
                  <p>Second paragraph has enough useful article words for extraction and scoring.</p>
                  <script>window.secret = 'do-not-keep'</script>
                  <iframe src="https://tracker.example"></iframe>
                </article>
                <footer>copyright boilerplate</footer>
              </body>
            </html>
        """.trimIndent()

        val extracted = extractor.extract(html, "https://example.com/news/raw")

        assertEquals("Canonical Title", extracted.title)
        assertEquals("https://example.com/story/main", extracted.canonicalUrl)
        assertEquals("2026-04-26T08:30:00Z", extracted.publishedAt)
        assertTrue(extracted.paragraphs.any { it.contains("ElymBot 本地搜索") })
        assertTrue(extracted.paragraphs.any { it.contains("Second paragraph") })
        assertFalse(extracted.paragraphs.joinToString("\n").contains("site header"))
        assertFalse(extracted.paragraphs.joinToString("\n").contains("do-not-keep"))
        assertFalse(extracted.paragraphs.joinToString("\n").contains("copyright boilerplate"))
    }

    @Test
    fun fallsBackToDenseParagraphsWhenArticleTagIsMissing() {
        val html = """
            <main>
              <div class="promo">short promo</div>
              <section>
                <p>Dense paragraph about Android native web search fallback with enough terms to be selected.</p>
                <p>Another dense paragraph explaining local extraction, markdown text and query aware pruning.</p>
              </section>
            </main>
        """.trimIndent()

        val extracted = extractor.extract(html, "https://example.com/docs")

        assertEquals(2, extracted.paragraphs.size)
        assertTrue(extracted.paragraphs.first().startsWith("Dense paragraph"))
    }
}
