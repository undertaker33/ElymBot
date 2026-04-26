package com.astrbot.android.core.runtime.search.local.crawl

import javax.inject.Inject

class DefaultContentCrawlerLite @Inject constructor(
    private val safetyPolicy: UrlSafetyPolicy,
    private val fetchClient: ContentFetchClient,
    private val extractor: HtmlContentExtractor,
    private val pruner: QueryAwareContentPruner,
    private val markdownGenerator: MarkdownLiteGenerator,
) : ContentCrawlerLite {
    override suspend fun crawl(request: ContentCrawlRequest): ContentCrawlResponse {
        val pages = mutableListOf<CrawledPage>()
        val diagnostics = mutableListOf<CrawlDiagnostic>()

        for (candidate in request.candidates.take(request.maxPages.coerceAtLeast(0))) {
            val diagnosticUrl = safetyPolicy.sanitizeForDiagnostics(candidate.url)
            val safety = safetyPolicy.isAllowed(candidate.url)
            if (!safety.allowed) {
                diagnostics += CrawlDiagnostic(
                    url = diagnosticUrl,
                    status = CrawlDiagnosticStatus.BLOCKED_URL,
                    reason = safety.reason,
                )
                continue
            }

            when (val fetch = fetchClient.fetch(candidate.url, request.maxBytes)) {
                is ContentFetchResult.Failure -> {
                    diagnostics += CrawlDiagnostic(
                        url = diagnosticUrl,
                        status = fetch.status,
                        reason = fetch.reason.redactSensitiveValues(),
                        traceId = fetch.traceId,
                        durationMs = fetch.durationMs,
                    )
                }
                is ContentFetchResult.Success -> {
                    val extracted = runCatching { extractor.extract(fetch.content.text, candidate.url) }
                    if (extracted.isFailure) {
                        diagnostics += CrawlDiagnostic(
                            url = diagnosticUrl,
                            status = CrawlDiagnosticStatus.PARSE_ERROR,
                            reason = extracted.exceptionOrNull()?.message.orEmpty().redactSensitiveValues(),
                            traceId = fetch.content.traceId,
                            durationMs = fetch.content.durationMs,
                        )
                        continue
                    }
                    val content = extracted.getOrThrow()
                    val pruned = pruner.prune(
                        paragraphs = content.paragraphs,
                        query = request.query,
                        intent = request.intent,
                        currentDate = request.currentDate,
                        maxTextChars = request.maxTextChars,
                    )
                    if (pruned.text.isBlank()) {
                        diagnostics += CrawlDiagnostic(
                            url = diagnosticUrl,
                            status = CrawlDiagnosticStatus.EMPTY_CONTENT,
                            reason = "empty_extracted_content",
                            traceId = fetch.content.traceId,
                            durationMs = fetch.content.durationMs,
                        )
                        continue
                    }
                    val pageUrl = content.canonicalUrl ?: candidate.url
                    val markdown = markdownGenerator.generate(
                        extracted = content.copy(
                            title = content.title.ifBlank { candidate.title },
                            canonicalUrl = pageUrl,
                            publishedAt = content.publishedAt ?: candidate.publishedAt,
                        ),
                        paragraphs = pruned.selectedParagraphs,
                    )
                    pages += CrawledPage(
                        title = content.title.ifBlank { candidate.title },
                        url = pageUrl,
                        canonicalUrl = content.canonicalUrl,
                        publishedAt = content.publishedAt ?: candidate.publishedAt,
                        text = pruned.text,
                        markdown = markdown.text,
                        metadata = candidate.metadata + markdown.metadata + mapOf(
                            "source" to candidate.source,
                            "charset" to fetch.content.charset,
                        ).filterValues { it.isNotBlank() },
                    )
                    diagnostics += CrawlDiagnostic(
                        url = diagnosticUrl,
                        status = CrawlDiagnosticStatus.SUCCESS,
                        reason = "ok",
                        traceId = fetch.content.traceId,
                        durationMs = fetch.content.durationMs,
                    )
                }
            }
        }

        return ContentCrawlResponse(
            query = request.query,
            pages = pages,
            diagnostics = diagnostics,
        )
    }

    private fun String.redactSensitiveValues(): String {
        return replace(Regex("""(?i)(authorization|api[-_]?key|cookie|token)=([^&\s]+)"""), "$1=<redacted>")
            .take(240)
    }
}
