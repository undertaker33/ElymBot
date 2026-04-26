package com.astrbot.android.core.runtime.search.local.parser

import com.astrbot.android.core.runtime.search.local.LocalSearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal const val MAX_LOCAL_SEARCH_SNIPPET_LENGTH = 700

internal fun parseDocument(html: String): Document = Jsoup.parse(html)

internal fun Element.firstText(vararg selectors: String): String {
    return selectors.asSequence()
        .mapNotNull { selector -> selectFirst(selector)?.text()?.trim() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
}

internal fun Element.firstHref(vararg selectors: String): String {
    return selectors.asSequence()
        .mapNotNull { selector -> selectFirst(selector)?.attr("href")?.trim() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
}

internal fun String.snippetLimit(): String = trim().take(MAX_LOCAL_SEARCH_SNIPPET_LENGTH)

internal fun List<LocalSearchResult>.distinctLocalResults(): List<LocalSearchResult> {
    return distinctBy { "${it.engine}|${it.url}|${it.title}" }
}

internal fun absoluteOrRawUrl(
    baseUrl: String,
    href: String,
): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    return runCatching { java.net.URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
}

internal object HtmlParserUtils {
    fun parseSearchDocument(html: String): Document? {
        if (html.isBlank()) return null
        val lower = html.lowercase()
        if ("captcha" in lower || "\u9a8c\u8bc1" in lower || "\u5b89\u5168\u68c0\u67e5" in lower) return null
        return Jsoup.parse(html)
    }

    fun cleanText(value: String?): String {
        return value.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun firstText(vararg elements: Element?): String {
        return elements.asSequence()
            .map { element -> cleanText(element?.text()) }
            .firstOrNull(String::isNotBlank)
            .orEmpty()
    }

    fun snippet(value: String): String = cleanText(value).take(MAX_LOCAL_SEARCH_SNIPPET_LENGTH)

    fun normalizeDuckDuckGoUrl(url: String): String {
        val raw = url.trim()
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(raw)?.groupValues?.getOrNull(1)
        return if (uddg.isNullOrBlank()) {
            raw
        } else {
            runCatching { java.net.URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(raw)
        }
    }
}
