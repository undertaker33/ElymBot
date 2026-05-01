package com.astrbot.android.core.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent
import javax.inject.Inject
import kotlin.math.ln

class QueryAwareContentPruner @Inject constructor() {
    fun prune(
        paragraphs: List<String>,
        query: String,
        intent: NaturalLanguageSearchIntent,
        currentDate: String,
        maxTextChars: Int,
    ): PrunedContent {
        if (maxTextChars <= 0) return PrunedContent(emptyList(), "")
        val queryTerms = termsFor(query).toSet()
        val documentFrequencies = queryTerms.associateWith { term ->
            paragraphs.count { paragraph -> termsFor(paragraph).contains(term) }.coerceAtLeast(1)
        }
        val scored = paragraphs.mapIndexed { index, paragraph ->
            val paragraphTerms = termsFor(paragraph)
            val score = scoreParagraph(
                paragraph = paragraph,
                paragraphTerms = paragraphTerms,
                queryTerms = queryTerms,
                documentFrequencies = documentFrequencies,
                documentCount = paragraphs.size.coerceAtLeast(1),
                intent = intent,
                currentDate = currentDate,
            )
            ScoredParagraph(index, paragraph, score)
        }

        val selected = scored
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<ScoredParagraph> { it.score }.thenBy { it.index })
            .ifEmpty { scored.take(1) }
            .map { it.text }

        val limited = limitParagraphs(selected, maxTextChars)
        return PrunedContent(
            selectedParagraphs = limited,
            text = limited.joinToString("\n\n"),
        )
    }

    private fun scoreParagraph(
        paragraph: String,
        paragraphTerms: List<String>,
        queryTerms: Set<String>,
        documentFrequencies: Map<String, Int>,
        documentCount: Int,
        intent: NaturalLanguageSearchIntent,
        currentDate: String,
    ): Double {
        if (queryTerms.isEmpty()) return 1.0
        val termCounts = paragraphTerms.groupingBy { it }.eachCount()
        val lengthNorm = 1.0 + paragraphTerms.size / 80.0
        var score = 0.0
        for (term in queryTerms) {
            val tf = termCounts[term] ?: continue
            val df = documentFrequencies[term] ?: 1
            val idf = ln(1.0 + (documentCount - df + 0.5) / (df + 0.5))
            score += (tf * idf + 1.0) / lengthNorm
        }
        val lower = paragraph.lowercase()
        if (intent == NaturalLanguageSearchIntent.NEWS || intent == NaturalLanguageSearchIntent.REALTIME) {
            if (currentDate.isNotBlank() && paragraph.contains(currentDate)) score += 2.5
            if (listOf("latest", "today", "now", "breaking", "最新", "今日", "今天", "实时").any { lower.contains(it) }) {
                score += 1.0
            }
        }
        return score
    }

    private fun limitParagraphs(paragraphs: List<String>, maxTextChars: Int): List<String> {
        val selected = mutableListOf<String>()
        var used = 0
        for (paragraph in paragraphs) {
            val separator = if (selected.isEmpty()) 0 else 2
            if (used + separator + paragraph.length <= maxTextChars) {
                selected += paragraph
                used += separator + paragraph.length
            } else if (selected.isEmpty()) {
                selected += paragraph.take(maxTextChars).trim()
                break
            }
        }
        return selected
    }

    private fun termsFor(text: String): List<String> {
        val terms = mutableListOf<String>()
        Regex("""[A-Za-z0-9][A-Za-z0-9._-]{1,}""")
            .findAll(text.lowercase())
            .forEach { terms += it.value }

        val cjkRuns = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF]+""").findAll(text)
        for (run in cjkRuns) {
            val value = run.value
            if (value.length == 1) {
                terms += value
            } else {
                for (index in 0 until value.length - 1) {
                    terms += value.substring(index, index + 2)
                }
            }
        }
        return terms
    }

    private data class ScoredParagraph(
        val index: Int,
        val text: String,
        val score: Double,
    )
}
