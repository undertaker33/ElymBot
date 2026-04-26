package com.astrbot.android.core.runtime.search.local.crawl

import javax.inject.Inject

class MarkdownLiteGenerator @Inject constructor() {
    fun generate(
        extracted: ExtractedContent,
        paragraphs: List<String>,
    ): MarkdownLiteDocument {
        val body = buildString {
            extracted.title.takeIf { it.isNotBlank() }?.let { title ->
                append("# ").append(title.cleanMarkdownLine()).append("\n\n")
            }
            paragraphs.map { it.removeExternalUrls().cleanMarkdownLine() }
                .filter { it.isNotBlank() }
                .forEach { paragraph ->
                    append(paragraph).append("\n\n")
                }
        }.trim()

        val metadata = buildMap {
            extracted.canonicalUrl?.let { put("url", it) }
            extracted.publishedAt?.let { put("publishedAt", it) }
            if (extracted.title.isNotBlank()) put("title", extracted.title)
        }
        return MarkdownLiteDocument(text = body, metadata = metadata)
    }

    private fun String.removeExternalUrls(): String {
        return replace(Regex("""https?://\S+"""), "")
    }

    private fun String.cleanMarkdownLine(): String {
        return replace(Regex("\\s+"), " ")
            .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            .trim()
    }
}
