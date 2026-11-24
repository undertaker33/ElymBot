package com.elymbot.android.plugins.qq

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class QqNapCatLoginStatus(
    val isLoggedIn: Boolean,
    val qrContent: String?,
    val accountId: String?,
    val coreReady: Boolean,
)

/** Implements the login API used by the validated ON-04 local NapCat WebUI. */
internal class QqNapCatLoginApi(
    private val secretFile: File,
    private val baseUrl: String = WEB_UI_BASE_URL,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    @Volatile
    private var credential: String? = null

    fun invalidateCredential() {
        credential = null
    }

    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        val url = URL(baseUrl)
        if (url.protocol != "http" || url.host != "127.0.0.1" || url.userInfo != null) return@withContext false
        val connection = runCatching { connectionFactory(url) }.getOrNull() ?: return@withContext false
        try {
            connection.connectTimeout = HEALTH_CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = HEALTH_READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            val code = connection.responseCode
            code in 200..399
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    suspend fun status(): QqNapCatLoginStatus = withContext(Dispatchers.IO) {
        val data = authorizedPost("QQLogin/CheckLoginStatus")
        QqNapCatLoginStatus(
            isLoggedIn = data.boolean("isLogin"),
            qrContent = data.string("qrcodeurl")?.takeIf(String::isNotBlank),
            accountId = data.string("uin")?.takeIf(String::isNotBlank),
            coreReady = data.boolean("coreReady"),
        )
    }

    suspend fun refreshQr() = withContext(Dispatchers.IO) {
        authorizedPost("QQLogin/RefreshQRcode")
        Unit
    }

    private fun authorizedPost(path: String): JsonObject {
        val currentCredential = credential ?: authenticate()
        return try {
            post(path, buildJsonObject { }, currentCredential)
                .getValue("data")
                .jsonObject
        } catch (failure: IOException) {
            credential = null
            throw failure
        }
    }

    private fun authenticate(): String {
        val webUiToken = readWebUiToken()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$webUiToken.napcat".toByteArray(Charsets.UTF_8))
            .toHex()
        val response = post("auth/login", buildJsonObject { put("hash", hash) }, token = null)
        val issuedCredential = response["data"]
            ?.jsonObject
            ?.string("Credential")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("NapCat authentication response is invalid")
        credential = issuedCredential
        return issuedCredential
    }

    private fun readWebUiToken(): String {
        if (!secretFile.isFile || java.nio.file.Files.isSymbolicLink(secretFile.toPath()) || secretFile.length() > MAX_SECRET_FILE_BYTES) {
            throw IOException("NapCat runtime secrets are unavailable")
        }
        val line = secretFile.readLines(Charsets.UTF_8)
            .firstOrNull { it.startsWith(SECRET_KEY_NAME) }
            ?: throw IOException("NapCat runtime secrets are unavailable")
        val value = line.removePrefix(SECRET_KEY_NAME).trim()
        if (value.isBlank() || value.length > MAX_SECRET_LENGTH || value.any(Char::isWhitespace)) {
            throw IOException("NapCat runtime secrets are invalid")
        }
        return value
    }

    private fun post(path: String, body: JsonObject, token: String?): JsonObject {
        val url = URL(baseUrl.trimEnd('/') + "/api/" + path)
        if (url.protocol != "http" || url.host != "127.0.0.1" || url.userInfo != null) {
            throw IOException("NapCat API endpoint is invalid")
        }
        val connection = connectionFactory(url)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            val requestBytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(requestBytes.size)
            connection.outputStream.use { it.write(requestBytes) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("NapCat API request failed")
            }
            val responseBytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (responseBytes.size() + read > MAX_RESPONSE_BYTES) {
                        throw IOException("NapCat API response exceeded its limit")
                    }
                    responseBytes.write(buffer, 0, read)
                }
            }
            val response = runCatching {
                Json.parseToJsonElement(responseBytes.toString(UTF8.name())).jsonObject
            }.getOrElse { throw IOException("NapCat API response is invalid", it) }
            if (response["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != 0) {
                throw IOException("NapCat API rejected the request")
            }
            return response
        } catch (failure: IOException) {
            throw failure
        } catch (failure: Exception) {
            throw IOException("NapCat API request failed", failure)
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun ByteArray.toHex(): String = joinToString("") { value -> "%02x".format(value) }

    private companion object {
        const val WEB_UI_BASE_URL = "http://127.0.0.1:6099/"
        const val SECRET_KEY_NAME = "NAPCAT_WEBUI_SECRET_KEY="
        const val MAX_SECRET_FILE_BYTES = 4 * 1024L
        const val MAX_SECRET_LENGTH = 256
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val READ_TIMEOUT_MILLIS = 5_000
        const val HEALTH_CONNECT_TIMEOUT_MILLIS = 1_000
        const val HEALTH_READ_TIMEOUT_MILLIS = 1_000
        const val MAX_RESPONSE_BYTES = 128 * 1024
        const val COPY_BUFFER_BYTES = 4 * 1024
        val UTF8: Charset = Charsets.UTF_8
    }
}
