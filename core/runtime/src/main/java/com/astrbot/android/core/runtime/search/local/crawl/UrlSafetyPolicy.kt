package com.astrbot.android.core.runtime.search.local.crawl

import java.net.IDN
import java.net.URI
import javax.inject.Inject

class UrlSafetyPolicy @Inject constructor() {
    fun isAllowed(url: String): UrlSafetyDecision {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
            ?: return UrlSafetyDecision(false, "invalid_url")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlSafetyDecision(false, "blocked_scheme")
        }
        val host = uri.host?.trim('[', ']')?.takeIf { it.isNotBlank() }
            ?: return UrlSafetyDecision(false, "missing_host")
        val asciiHost = runCatching { IDN.toASCII(host).lowercase() }.getOrDefault(host.lowercase())
        if (asciiHost == "localhost" || asciiHost.endsWith(".localhost")) {
            return UrlSafetyDecision(false, "blocked_localhost")
        }
        if (isBlockedIpv4(asciiHost) || isBlockedIpv6(asciiHost)) {
            return UrlSafetyDecision(false, "blocked_private_address")
        }
        return UrlSafetyDecision(true)
    }

    fun sanitizeForDiagnostics(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.take(120)
        val scheme = uri.scheme ?: return url.take(120)
        val host = uri.host ?: return "$scheme://<invalid-host>"
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: ""
        return "$scheme://$host$port$path".take(240)
    }

    private fun isBlockedIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            first == 127 ||
            first == 0 ||
            first == 169 && second == 254 ||
            first == 172 && second in 16..31 ||
            first == 192 && second == 168
    }

    private fun isBlockedIpv6(host: String): Boolean {
        val lower = host.lowercase()
        if (!lower.contains(':')) return false
        return lower == "::1" ||
            lower.startsWith("fc") ||
            lower.startsWith("fd") ||
            lower.startsWith("fe8") ||
            lower.startsWith("fe9") ||
            lower.startsWith("fea") ||
            lower.startsWith("feb")
    }
}
