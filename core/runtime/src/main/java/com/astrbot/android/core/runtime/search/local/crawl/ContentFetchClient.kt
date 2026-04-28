package com.astrbot.android.core.runtime.search.local.crawl

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import java.nio.charset.Charset
import javax.inject.Inject

class ContentFetchClient @Inject constructor(
    private val transport: RuntimeNetworkTransport,
) {
    suspend fun fetch(url: String, maxBytes: Int): ContentFetchResult {
        val request = RuntimeNetworkRequest(
            capability = RuntimeNetworkCapability.WEB_SEARCH,
            method = "GET",
            url = url,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1",
                "User-Agent" to "ElymBot-Android-WebSearch/1.0",
            ),
            timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            followRedirects = false,
        )
        return try {
            val response = transport.execute(request)
            val contentType = response.headers.firstHeader("content-type")
            if (!isAllowedContentType(contentType)) {
                return ContentFetchResult.Failure(
                    status = CrawlDiagnosticStatus.UNSUPPORTED_CONTENT_TYPE,
                    reason = "unsupported_content_type",
                    traceId = response.traceId,
                    durationMs = response.durationMs,
                )
            }
            if (response.bodyBytes.size > maxBytes) {
                return ContentFetchResult.Failure(
                    status = CrawlDiagnosticStatus.TOO_LARGE,
                    reason = "body_exceeds_max_bytes",
                    traceId = response.traceId,
                    durationMs = response.durationMs,
                )
            }
            val charset = charsetFrom(contentType, response.bodyBytes)
            ContentFetchResult.Success(
                FetchedContent(
                    url = url,
                    contentType = contentType,
                    charset = charset.name(),
                    text = response.bodyBytes.toString(charset),
                    traceId = response.traceId,
                    durationMs = response.durationMs,
                ),
            )
        } catch (e: RuntimeNetworkException) {
            ContentFetchResult.Failure(
                status = CrawlDiagnosticStatus.FETCH_FAILED,
                reason = e.failure.summary,
            )
        }
    }

    private fun isAllowedContentType(contentType: String?): Boolean {
        val normalized = contentType?.substringBefore(';')?.trim()?.lowercase()
        return normalized == null ||
            normalized == "text/html" ||
            normalized == "application/xhtml+xml" ||
            normalized == "text/plain"
    }

    private fun charsetFrom(contentType: String?, bytes: ByteArray): Charset {
        val headerCharset = contentType
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', '\'')
            ?.toCharsetOrNull()
        if (headerCharset != null) return headerCharset

        val head = bytes.copyOfRange(0, minOf(bytes.size, 4096)).toString(Charsets.ISO_8859_1)
        val metaCharset = Regex("""<meta\s+[^>]*charset=["']?\s*([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(head)
            ?.groupValues
            ?.getOrNull(1)
            ?.toCharsetOrNull()
        if (metaCharset != null) return metaCharset

        val httpEquivCharset = Regex("""content=["'][^"']*charset=([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(head)
            ?.groupValues
            ?.getOrNull(1)
            ?.toCharsetOrNull()
        return httpEquivCharset ?: Charsets.UTF_8
    }

    private fun String.toCharsetOrNull(): Charset? = runCatching { Charset.forName(this) }.getOrNull()

    private fun Map<String, List<String>>.firstHeader(name: String): String? {
        return entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
    }
}
