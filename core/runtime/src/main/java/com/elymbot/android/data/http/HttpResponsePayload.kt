package com.elymbot.android.data.http

data class HttpResponsePayload(
    val code: Int,
    val body: String,
    val headers: Map<String, List<String>>,
    val url: String,
)

enum class HttpFailureCategory {
    TIMEOUT,
    NETWORK,
}

class ElymBotHttpException(
    val category: HttpFailureCategory,
    override val message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
