package com.astrbot.android.data.http

enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
}

data class HttpRequestSpec(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    val connectTimeoutMs: Long = 30_000L,
    val readTimeoutMs: Long = 60_000L,
)

sealed class MultipartPartSpec {
    data class Text(
        val name: String,
        val value: String,
    ) : MultipartPartSpec()

    data class File(
        val name: String,
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
    ) : MultipartPartSpec()
}
