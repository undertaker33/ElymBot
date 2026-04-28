package com.astrbot.android.core.runtime.search.local.crawl

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject

class HtmlContentExtractor @Inject constructor() {
    fun extract(html: String, baseUrl: String): ExtractedContent {
        val document = Jsoup.parse(html, baseUrl)
        document.select(REMOVE_SELECTORS).remove()

        val title = document.selectFirst("meta[property=og:title], meta[name=twitter:title]")
            ?.attr("content")
            ?.cleanText()
            ?.takeIf { it.isNotBlank() }
            ?: document.title().cleanText()

        val canonicalUrl = document.selectFirst("link[rel=canonical]")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }

        val publishedAt = document.selectFirst(PUBLISHED_SELECTORS)
            ?.let { element ->
                element.attr("content").ifBlank { element.attr("datetime") }.ifBlank { element.text() }
            }
            ?.cleanText()
            ?.takeIf { it.isNotBlank() }

        val root = bestRoot(document.body())
        val paragraphs = collectParagraphs(root).ifEmpty {
            collectParagraphs(document.body())
        }

        return ExtractedContent(
            title = title,
            canonicalUrl = canonicalUrl,
            publishedAt = publishedAt,
            paragraphs = paragraphs.distinct(),
        )
    }

    private fun bestRoot(body: Element?): Element {
        if (body == null) return Element("body")
        val semantic = body.select("article, main").maxByOrNull { paragraphScore(it) }
        if (semantic != null && paragraphScore(semantic) > 0) return semantic
        return body.select("section, div").maxByOrNull { paragraphScore(it) } ?: body
    }

    private fun paragraphScore(element: Element): Int {
        return element.select("p, li").sumOf { it.text().cleanText().length }
    }

    private fun collectParagraphs(root: Element): List<String> {
        return root.select("p, li")
            .map { it.text().cleanText() }
            .filter { text ->
                text.length >= 40 || (text.length >= 12 && text.any { char -> char.code > 127 })
            }
            .filterNot { text -> BOILERPLATE_PATTERNS.any { it.containsMatchIn(text) } }
    }

    private fun String.cleanText(): String = replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val REMOVE_SELECTORS =
            "script, style, noscript, svg, canvas, iframe, form, nav, footer, header, aside, " +
                "button, input, select, textarea, template, [aria-hidden=true], " +
                ".nav, .navbar, .footer, .header, .sidebar, .comment, .comments, .advertisement, .ads"

        private const val PUBLISHED_SELECTORS =
            "meta[property=article:published_time], meta[name=pubdate], meta[name=date], " +
                "time[datetime], [itemprop=datePublished]"

        private val BOILERPLATE_PATTERNS = listOf(
            Regex("""^\s*(share|subscribe|copyright|all rights reserved)\b""", RegexOption.IGNORE_CASE),
            Regex("""^\s*(登录|注册|分享|版权所有)"""),
        )
    }
}
