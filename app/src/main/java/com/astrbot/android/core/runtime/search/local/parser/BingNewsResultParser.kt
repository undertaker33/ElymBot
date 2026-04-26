package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult

class BingNewsResultParser {
    fun parse(html: String, maxResults: Int): List<LocalSearchResult> {
        val doc = HtmlParserUtils.parseSearchDocument(html) ?: return emptyList()
        val results = mutableListOf<LocalSearchResult>()
        val items = doc.select(".news-card, .newsitem, div:has(a.title[href]), div:has(a[href][aria-label])")

        for (item in items) {
            if (results.size >= maxResults) break
            val link = item.selectFirst("a.title[href], a[href][aria-label], a[href]") ?: continue
            val title = HtmlParserUtils.cleanText(
                link.attr("aria-label").ifBlank { link.text() },
            )
            val url = link.attr("href").trim()
            val snippet = HtmlParserUtils.snippet(
                HtmlParserUtils.firstText(
                    item.selectFirst(".snippet"),
                    item.selectFirst(".news_snip"),
                    item.selectFirst(".caption"),
                    item.selectFirst("p"),
                ),
            )
            if (title.isNotBlank() && url.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet,
                    engine = "bing_news",
                    module = "bing_news_card",
                    source = HtmlParserUtils.firstText(
                        item.selectFirst(".source"),
                        item.selectFirst(".provider"),
                        item.selectFirst(".news-source"),
                    ),
                    publishedAt = HtmlParserUtils.firstText(item.selectFirst("time"), item.selectFirst(".time")),
                    rank = results.size + 1,
                )
            }
        }

        return results.distinctBy { it.url }.take(maxResults)
    }
}
