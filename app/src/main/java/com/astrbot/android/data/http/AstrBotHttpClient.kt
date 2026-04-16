package com.astrbot.android.data.http

import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface AstrBotHttpClient {
    fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload

    fun executeBytes(requestSpec: HttpRequestSpec): ByteArray

    suspend fun executeStream(
        requestSpec: HttpRequestSpec,
        onLine: suspend (String) -> Unit,
    )

    fun executeMultipart(
        requestSpec: HttpRequestSpec,
        parts: List<MultipartPartSpec>,
    ): HttpResponsePayload
}

class OkHttpAstrBotHttpClient(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val logger: (String) -> Unit = RuntimeLogRepository::append,
) : AstrBotHttpClient {
    override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload {
        return executeInternal(requestSpec)
    }

    override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP binary request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        val request = buildRequest(requestSpec)
        val client = buildClient(requestSpec)
        return try {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: byteArrayOf()
                if (!response.isSuccessful) {
                    val errorBody = sanitizeErrorBody(bytes.toString(StandardCharsets.UTF_8))
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                logger("HTTP binary response code: code=${response.code} endpoint=$sanitizedUrl")
                bytes
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    override suspend fun executeStream(
        requestSpec: HttpRequestSpec,
        onLine: suspend (String) -> Unit,
    ) {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP streaming request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        val request = buildRequest(requestSpec)
        val client = buildClient(requestSpec)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body?.charStream()?.readText().orEmpty()
                    val errorBody = sanitizeErrorBody(rawBody)
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                logger("HTTP streaming response code: code=${response.code} endpoint=$sanitizedUrl")
                response.body?.charStream()?.useLines { lines ->
                    lines.forEach { line -> kotlinx.coroutines.runBlocking { onLine(line) } }
                }
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    override fun executeMultipart(
        requestSpec: HttpRequestSpec,
        parts: List<MultipartPartSpec>,
    ): HttpResponsePayload {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { part ->
                    when (part) {
                        is MultipartPartSpec.Text -> addFormDataPart(part.name, part.value)
                        is MultipartPartSpec.File -> addFormDataPart(
                            part.name,
                            part.fileName,
                            part.bytes.toRequestBody(part.contentType.toMediaTypeOrNull()),
                        )
                    }
                }
            }
            .build()
        return executeInternal(
            requestSpec.copy(
                body = null,
                contentType = multipartBody.contentType().toString(),
            ),
            requestOverride = Request.Builder().url(requestSpec.url).apply {
                requestSpec.headers.forEach { (name, value) -> header(name, value) }
            }.method(requestSpec.method.value, multipartBody).build(),
        )
    }

    private fun executeInternal(
        requestSpec: HttpRequestSpec,
        requestOverride: Request? = null,
    ): HttpResponsePayload {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")

        val request = requestOverride ?: buildRequest(requestSpec)
        val client = buildClient(requestSpec)

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                logger("HTTP response code: code=${response.code} endpoint=$sanitizedUrl")
                HttpResponsePayload(
                    code = response.code,
                    body = body,
                    headers = response.headers.toMultimap(),
                    url = requestSpec.url,
                )
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    private fun buildRequest(requestSpec: HttpRequestSpec): Request {
        val builder = Request.Builder().url(requestSpec.url)
        requestSpec.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = requestSpec.body
        val requestBody = when {
            body == null && requestSpec.method == HttpMethod.GET -> null
            body == null -> ByteArray(0).toRequestBody(requestSpec.contentType?.toMediaTypeOrNull())
            else -> body.toByteArray(StandardCharsets.UTF_8).toRequestBody(requestSpec.contentType?.toMediaTypeOrNull())
        }
        return builder.method(requestSpec.method.value, requestBody).build()
    }

    private fun buildClient(requestSpec: HttpRequestSpec): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(requestSpec.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(requestSpec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun mapFailure(
        sanitizedUrl: String,
        error: Throwable,
    ): AstrBotHttpException {
        val category = when (error) {
            is SocketTimeoutException, is InterruptedIOException -> HttpFailureCategory.TIMEOUT
            is AstrBotHttpException -> error.category
            else -> HttpFailureCategory.NETWORK
        }
        logger(
            "HTTP request failed: endpoint=$sanitizedUrl category=$category reason=${error.message ?: error.javaClass.simpleName}",
        )
        return if (error is AstrBotHttpException) {
            error
        } else {
            AstrBotHttpException(
                category = category,
                message = error.message ?: "HTTP request failed",
                cause = error,
            )
        }
    }

    internal fun sanitizeErrorBody(raw: String): String {
        if (raw.isBlank()) return "(empty)"
        val truncated = raw.take(1000)
        val sensitiveKeys = Regex(
            """("(?:api_key|apikey|authorization|token|secret|password|key)")\s*:\s*"[^"]*"""",
            RegexOption.IGNORE_CASE,
        )
        return sensitiveKeys.replace(truncated) { match ->
            "${match.groupValues[1]}: \"***\""
        }
    }

    private fun sanitizeUrlForLogs(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val base = url.substring(0, queryStart)
        val query = url.substring(queryStart + 1)
        val sanitizedQuery = query.split("&")
            .filter { it.isNotBlank() }
            .joinToString("&") { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@joinToString pair
                val key = pair.substring(0, separator)
                val value = pair.substring(separator + 1)
                if (key.equals("key", ignoreCase = true) || key.equals("api_key", ignoreCase = true)) {
                    "$key=***"
                } else {
                    "$key=$value"
                }
            }
        return "$base?$sanitizedQuery"
    }
}
