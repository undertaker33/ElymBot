package com.astrbot.android.core.runtime.search.local.engine

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import java.net.URLEncoder

internal const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun webSearchGetRequest(
    url: String,
    acceptLanguage: String,
): RuntimeNetworkRequest {
    return RuntimeNetworkRequest(
        capability = RuntimeNetworkCapability.WEB_SEARCH,
        method = "GET",
        url = url,
        headers = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept-Language" to acceptLanguage,
        ),
        timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
    )
}

internal fun acceptLanguage(locale: String?): String {
    return when {
        locale.isNullOrBlank() -> "zh-CN,zh;q=0.9,en;q=0.8"
        locale.startsWith("en", ignoreCase = true) -> "$locale,en;q=0.9,zh-CN;q=0.7,zh;q=0.6"
        locale.startsWith("zh", ignoreCase = true) -> "$locale,zh;q=0.9,en;q=0.8"
        else -> "$locale,zh-CN;q=0.8,zh;q=0.7,en;q=0.6"
    }
}

