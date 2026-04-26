package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult

class BaiduWebParser {
    fun parse(html: String, maxResults: Int): List<LocalSearchResult> {
        val doc = HtmlParserUtils.parseSearchDocument(html) ?: return emptyList()
        val results = mutableListOf<LocalSearchResult>()
        val items = doc.select("div.result, div.c-container, div.result-op")

        for (item in items) {
            if (results.size >= maxResults) break
            val link = item.selectFirst("h3.t a[href], h3 a[href], a[href]") ?: continue
            val title = HtmlParserUtils.cleanText(link.text())
            val url = link.attr("href").trim()
            val snippet = HtmlParserUtils.snippet(
                HtmlParserUtils.firstText(
                    item.selectFirst(".c-abstract"),
                    item.selectFirst(".content-right_8Zs40"),
                    item.selectFirst(".c-span-last"),
                    item.selectFirst("span.content"),
                    item.selectFirst("p"),
                ),
            )
            if (title.isNotBlank() && url.isNotBlank() && !looksLikeAiGeneratedModule(item.className())) {
                results += LocalSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet,
                    engine = "baidu_web",
                    module = "baidu_web_result",
                    source = HtmlParserUtils.firstText(item.selectFirst(".c-showurl"), item.selectFirst(".c-color-gray")),
                    rank = results.size + 1,
                )
            }
        }

        return results.distinctBy { it.url }.take(maxResults)
    }

    private fun looksLikeAiGeneratedModule(className: String): Boolean {
        val normalized = className.lowercase()
        return "ai" in normalized && ("answer" in normalized || "summary" in normalized)
    }
}
