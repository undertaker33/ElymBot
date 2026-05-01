package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SogouResultParser {
    fun parse(
        html: String,
        maxResults: Int,
        searchUrl: String,
    ): List<LocalSearchResult> {
        val doc = parseDocument(html)
        val results = mutableListOf<LocalSearchResult>()
        extractWeatherCard(doc, searchUrl)?.let(results::add)
        results += extractSupListResults(html)
        results += extractVideoResults(doc, searchUrl, maxResults - results.size)
        extractAnswerSummaryResult(html, searchUrl)?.let(results::add)

        for (item in doc.select("div.vrwrap, div.rb")) {
            if (results.size >= maxResults) break
            val link = item.selectFirst("h3 a[href], .vr-title a[href], a[href]") ?: continue
            val href = link.attr("href").trim()
            val title = sequenceOf(
                item.selectFirst("h3")?.text()?.trim(),
                item.selectFirst(".vr-title")?.text()?.trim(),
                link.text().trim(),
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
            val snippet = sequenceOf(
                item.selectFirst("p.str_info"),
                item.selectFirst("div.str-text-info"),
                item.selectFirst(".text-layout"),
                item.selectFirst("p"),
            ).firstOrNull()?.text().orEmpty().snippetLimit()
            if (title.isNotBlank() && href.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = href,
                    snippet = snippet,
                    engine = "sogou",
                    module = "sogou_rb",
                    rank = results.size + 1,
                )
            }
        }
        return results.distinctLocalResults().take(maxResults)
    }

    private fun extractVideoResults(
        doc: Document,
        searchUrl: String,
        remaining: Int,
    ): List<LocalSearchResult> {
        if (remaining <= 0) return emptyList()
        val items = doc.select("a:has(.click-sugg-title), a:has(div[class*=videoTitle])")
        val results = mutableListOf<LocalSearchResult>()
        for (item in items) {
            if (results.size >= remaining) break
            val title = item.firstText(".click-sugg-title", "div[class*=videoTitle]")
                .replace(Regex("\\s+"), " ")
                .trim()
            val source = item.firstText("span[class*=descContent]", "div[class*=source]")
                .replace(Regex("\\s+"), " ")
                .trim()
            val href = item.attr("href").trim()
            val targetUrl = extractRedirectTargetUrl(href)
                .ifBlank { absoluteOrRawUrl(searchUrl, href) }
            if (title.isNotBlank() && targetUrl.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = targetUrl,
                    snippet = listOf(source, extractPublishDate(item))
                        .filter(String::isNotBlank)
                        .joinToString("\uff0c")
                        .snippetLimit(),
                    source = source,
                    engine = "sogou",
                    module = "sogou_news_video",
                    rank = results.size + 1,
                )
            }
        }
        return results
    }

    private fun extractWeatherCard(
        doc: Document,
        searchUrl: String,
    ): LocalSearchResult? {
        val root = doc.selectFirst("div.weather210208, div.weather210208-wrap") ?: return null
        val location = doc.firstText(".location-tab li", ".location-module li")
        val temp = root.firstText(".temperature p", ".js_shikuang p")
        val condition = root.firstText(".wind-box .weath", ".detail")
        if (location.isBlank() && temp.isBlank() && condition.isBlank()) return null
        return LocalSearchResult(
            title = "${location.ifBlank { "\u5929\u6c14" }}\u5929\u6c14",
            url = searchUrl,
            snippet = listOf(temp, condition).filter(String::isNotBlank).joinToString("\uff0c").snippetLimit(),
            engine = "sogou",
            module = "sogou_weather_card",
            rank = 1,
            scoreHints = mapOf("intent" to 1.0),
        )
    }

    private fun extractSupListResults(html: String): List<LocalSearchResult> {
        val arrayText = extractJsonArrayByKey(html, "supList") ?: return emptyList()
        val array = runCatching { JSONArray(arrayText) }.getOrNull() ?: return emptyList()
        val results = mutableListOf<LocalSearchResult>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("sup_title").trim().ifBlank { item.optString("sup_source").trim() }
            val url = item.optString("sup_url").trim().replace("\\/", "/")
            val snippet = item.optString("sup_passage").trim().snippetLimit()
            val source = item.optString("sup_source").trim()
            if (title.isNotBlank() && url.isNotBlank() && snippet.isNotBlank()) {
                results += LocalSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet,
                    source = source,
                    engine = "sogou",
                    module = "sogou_sup_list",
                    rank = index + 1,
                )
            }
        }
        return results
    }

    private fun extractAnswerSummaryResult(
        html: String,
        searchUrl: String,
    ): LocalSearchResult? {
        val escaped = Regex("\"answer_summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val summary = decodeJsonString(escaped).trim()
        if (summary.isBlank()) return null
        return LocalSearchResult(
            title = "\u641c\u72d7\u641c\u7d22\u6458\u8981",
            url = searchUrl,
            snippet = summary.snippetLimit(),
            engine = "sogou",
            module = "sogou_answer_summary",
        )
    }

    private fun extractJsonArrayByKey(
        html: String,
        key: String,
    ): String? {
        val keyIndex = html.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val arrayStart = html.indexOf('[', keyIndex)
        if (arrayStart < 0) return null
        var index = arrayStart
        var depth = 0
        var inString = false
        var escaping = false
        while (index < html.length) {
            val ch = html[index]
            if (escaping) {
                escaping = false
            } else if (ch == '\\') {
                escaping = true
            } else if (ch == '"') {
                inString = !inString
            } else if (!inString) {
                when (ch) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return html.substring(arrayStart, index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun decodeJsonString(value: String): String {
        return runCatching { JSONObject("""{"v":"$value"}""").getString("v") }
            .getOrDefault(value)
    }

    private fun extractRedirectTargetUrl(href: String): String {
        val raw = Regex("""[?&]url=([^&]+)""")
            .find(href)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (raw.isBlank()) return ""
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun extractPublishDate(item: Element): String {
        val expose = item.selectFirst("[data-expose-item]")?.attr("data-expose-item").orEmpty()
        return Regex("""publishDateTime=([^&"]+)""")
            .find(expose)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { raw -> runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw) }
            .orEmpty()
    }
}
