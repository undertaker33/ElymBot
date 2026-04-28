package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult

class DuckDuckGoLiteParser {
    fun parse(html: String, maxResults: Int): List<LocalSearchResult> {
        val doc = HtmlParserUtils.parseSearchDocument(html) ?: return emptyList()
        val results = mutableListOf<LocalSearchResult>()
        val items = doc.select("div.result, tr:has(a.result-link), tr:has(a.result__a)")

        for (item in items) {
            if (results.size >= maxResults) break
            val link = item.selectFirst("a.result__a[href], a.result-link[href], a[href]") ?: continue
            val title = HtmlParserUtils.cleanText(link.text())
            val url = HtmlParserUtils.normalizeDuckDuckGoUrl(link.attr("href"))
            val snippet = HtmlParserUtils.snippet(
                HtmlParserUtils.firstText(
                    item.selectFirst(".result__snippet"),
                    item.selectFirst(".result-snippet"),
                    item.selectFirst("td.result-snippet"),
                ),
            )
            if (title.isNotBlank() && url.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet,
                    engine = "duckduckgo_lite",
                    module = "duckduckgo_lite_result",
                    rank = results.size + 1,
                )
            }
        }

        return results.distinctBy { it.url }.take(maxResults)
    }
}
