package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import org.jsoup.nodes.Document

class BingResultParser {
    fun parse(
        html: String,
        maxResults: Int,
        engineId: String = "bing",
        searchUrl: String = "https://www.bing.com/search",
    ): List<LocalSearchResult> {
        val doc = parseDocument(html)
        val results = mutableListOf<LocalSearchResult>()
        extractWeatherCard(doc, searchUrl, engineId)?.let(results::add)
        if (results.size < maxResults) extractAnswerCard(doc, searchUrl, engineId)?.let(results::add)

        val items = doc.select("li.b_algo, div.b_algo").ifEmpty {
            doc.select("li:has(h2 a[href]), div:has(h2 a[href])")
        }
        for (item in items) {
            if (results.size >= maxResults) break
            val link = sequenceOf(
                item.selectFirst(".b_algoheader a[href]"),
                item.selectFirst("h2 a[href]"),
                item.selectFirst("a.tilk[href]"),
                item.selectFirst("a[href]"),
            ).filterNotNull().firstOrNull() ?: continue
            val href = link.attr("href").trim()
            val title = sequenceOf(
                item.selectFirst(".b_algoheader h2")?.text()?.trim(),
                item.selectFirst("h2")?.text()?.trim(),
                link.text().trim(),
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
            val snippet = sequenceOf(
                item.selectFirst(".b_caption p"),
                item.selectFirst(".b_caption .b_lineclamp3"),
                item.selectFirst(".b_caption"),
                item.selectFirst("p"),
            ).firstOrNull()?.text().orEmpty().snippetLimit()
            if (title.isNotBlank() && href.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = href,
                    snippet = snippet,
                    engine = engineId,
                    module = if (engineId == "bing_news") "bing_news_item" else "bing_b_algo",
                    rank = results.size + 1,
                )
            }
        }
        return results.distinctLocalResults().take(maxResults)
    }

    private fun extractWeatherCard(
        doc: Document,
        searchUrl: String,
        engineId: String,
    ): LocalSearchResult? {
        val weather = sequenceOf(
            doc.selectFirst("[data-tag^='wea']"),
            doc.selectFirst("#wtr_snc"),
            doc.selectFirst(".wtr_hero"),
            doc.selectFirst("#wtr_card"),
            doc.selectFirst(".b_ans:has(.wtr_currTemp)"),
        ).firstOrNull() ?: return null
        val location = weather.firstText(".wtr_locTitle", ".wtr_loc")
        val temp = weather.firstText(".wtr_currTemp", ".wtr-currTemp")
        val condition = weather.firstText(".wtr_condi", ".wtr_cond")
        if (location.isBlank() && temp.isBlank() && condition.isBlank()) return null
        return LocalSearchResult(
            title = "${location.ifBlank { "\u5929\u6c14" }}\u5929\u6c14",
            url = searchUrl,
            snippet = listOf(temp, condition, weather.firstText(".wtr_fctxt"))
                .filter(String::isNotBlank)
                .joinToString("\uff0c")
                .snippetLimit(),
            engine = engineId,
            module = "bing_weather_card",
            rank = 1,
            scoreHints = mapOf("intent" to 1.0),
        )
    }

    private fun extractAnswerCard(
        doc: Document,
        searchUrl: String,
        engineId: String,
    ): LocalSearchResult? {
        for (container in doc.select("div.b_ans, li.b_ans")) {
            if (container.selectFirst(".wtr_currTemp, .wtr_hero, .wtr_condi, [data-tag^='wea']") != null) continue
            val rich = container.selectFirst(".b_rich, .b_vPanel, .b_promoteText") ?: continue
            val title = rich.firstText("h2", ".b_entityTitle").ifBlank { container.firstText("h2") }
            val snippet = rich.firstText(".b_factrow", ".b_paractl", "p")
            if (title.isNotBlank() && snippet.isNotBlank()) {
                return LocalSearchResult(
                    title = title,
                    url = searchUrl,
                    snippet = snippet.snippetLimit(),
                    engine = engineId,
                    module = "bing_answer_card",
                    rank = 1,
                )
            }
        }
        return null
    }
}
