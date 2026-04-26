package com.astrbot.android.core.runtime.search.local

import java.net.URI
import javax.inject.Inject
import kotlin.math.max

class LocalSearchResultMerger @Inject constructor() {
    fun merge(results: List<LocalSearchResult>): List<LocalSearchResult> {
        val merged = mutableListOf<LocalSearchResult>()
        val seenUrls = mutableMapOf<String, Int>()

        for (result in results) {
            val canonicalUrl = canonicalizeUrl(result.url)
            val duplicateIndex = seenUrls[canonicalUrl] ?: merged.indexOfFirst { existing ->
                existing.title.isNotBlank() && titleSimilarity(existing.title, result.title) >= TITLE_DUPLICATE_THRESHOLD
            }.takeIf { it >= 0 }

            if (duplicateIndex != null) {
                val existing = merged[duplicateIndex]
                merged[duplicateIndex] = existing.copy(
                    snippet = listOf(existing.snippet, result.snippet).maxByOrNull { it.length }.orEmpty(),
                    source = existing.source.ifBlank { result.source },
                    metadata = existing.metadata + result.metadata + mapOf(
                        "engines" to listOf(existing.engine, result.engine)
                            .filter(String::isNotBlank)
                            .distinct()
                            .joinToString(","),
                    ),
                    scoreHints = existing.scoreHints + result.scoreHints,
                )
            } else {
                seenUrls[canonicalUrl] = merged.size
                merged += result.copy(
                    url = canonicalUrl.ifBlank { result.url },
                )
            }
        }

        return merged.sortedWith(
            compareByDescending<LocalSearchResult> { result -> result.scoreHints["intent"] ?: 0.0 }
                .thenBy { result -> if (result.rank <= 0) Int.MAX_VALUE else result.rank }
                .thenByDescending { result -> result.snippet.length },
        )
    }

    internal fun canonicalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            if (scheme.isBlank() || host.isBlank()) return@runCatching url.trim()
            val query = uri.rawQuery
                ?.split('&')
                ?.filterNot { part ->
                    val key = part.substringBefore('=').lowercase()
                    key.startsWith("utm_") || key in trackingParams
                }
                ?.joinToString("&")
                ?.takeIf(String::isNotBlank)
            URI(scheme, uri.userInfo, host, uri.port, uri.rawPath, query, null).toString()
        }.getOrDefault(url.trim())
    }

    private fun titleSimilarity(
        left: String,
        right: String,
    ): Double {
        val leftTokens = titleTokens(left)
        val rightTokens = titleTokens(right)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val intersection = leftTokens.intersect(rightTokens).size
        val union = max(leftTokens.union(rightTokens).size, 1)
        return intersection.toDouble() / union
    }

    private fun titleTokens(title: String): Set<String> {
        val cjk = title.replace(Regex("[\\p{Punct}\\s]+"), "")
            .windowed(size = 2, step = 1, partialWindows = false)
            .filter { token -> token.any { it.code > 127 } }
        val latin = Regex("[a-zA-Z0-9]{2,}")
            .findAll(title.lowercase())
            .map { it.value }
            .toList()
        return (cjk + latin).toSet()
    }
}

private val trackingParams = setOf(
    "spm",
    "from",
    "source",
    "seid",
    "sa",
    "ved",
    "usg",
    "fbclid",
    "gclid",
)

private const val TITLE_DUPLICATE_THRESHOLD = 0.6
