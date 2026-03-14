package com.astrbot.android.data

import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.SavedQqAccount
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object NapCatLoginService {
    private const val WEB_UI_TOKEN = "astrbot_android_webui"
    private var credential: String = ""

    data class NewDeviceQrCodeResult(
        val qrUrl: String,
        val bytesToken: String,
    )

    data class NewDeviceQrPollResult(
        val guaranteeStatus: Int,
        val successToken: String,
    )

    data class LoginActionResult(
        val completed: Boolean,
        val needCaptcha: Boolean = false,
        val captchaUrl: String = "",
        val needNewDevice: Boolean = false,
        val newDeviceJumpUrl: String = "",
        val newDeviceSig: String = "",
    )

    private data class ApiEnvelope(
        val code: Int,
        val message: String,
        val data: Any?,
    )

    fun clearCredential() {
        credential = ""
    }

    fun fetchLoginState(baseUrl: String): NapCatLoginState {
        val savedAccounts = runCatching { getQuickLoginAccounts(baseUrl) }.getOrDefault(emptyList())
        val quickLoginAccount = runCatching { getQuickLoginAccount(baseUrl) }.getOrDefault("")
        val statusData = requireObject(
            requireSuccess(
                authorizedPostResponse(baseUrl, "/QQLogin/CheckLoginStatus", JSONObject()),
            ),
        )
        val isLogin = statusData.optBoolean("isLogin", false)
        val isOffline = statusData.optBoolean("isOffline", false)
        val qrCodeUrl = statusData.optString("qrcodeurl")
        val loginError = statusData.optString("loginError")

        var uin = ""
        var nick = ""
        var avatarUrl = ""
        if (isLogin) {
            val infoData = requireObject(
                requireSuccess(
                    authorizedPostResponse(baseUrl, "/QQLogin/GetQQLoginInfo", JSONObject()),
                ),
            )
            uin = infoData.optString("uin")
            nick = infoData.optString("nick")
            avatarUrl = infoData.optString("avatarUrl")
        }

        val statusText = when {
            isLogin -> "QQ logged in"
            isOffline -> "QQ session is offline"
            qrCodeUrl.isNotBlank() -> "Scan the QR code with QQ to log in"
            loginError.isNotBlank() -> loginError
            else -> "Waiting for login QR code"
        }

        return NapCatLoginState(
            bridgeReady = true,
            authenticated = credential.isNotBlank(),
            isLogin = isLogin,
            isOffline = isOffline,
            qrCodeUrl = qrCodeUrl,
            quickLoginUin = quickLoginAccount,
            savedAccounts = savedAccounts,
            loginError = loginError,
            uin = uin,
            nick = nick,
            avatarUrl = avatarUrl,
            statusText = statusText,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    fun getQuickLoginAccount(baseUrl: String): String {
        val data = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginQQ", JSONObject()),
        )
        return unwrapValue(data)
    }

    fun getQuickLoginAccounts(baseUrl: String): List<SavedQqAccount> {
        val data = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginListNew", JSONObject()),
        )
        val parsed = parseQuickLoginAccountList(data)
        if (parsed.isNotEmpty()) {
            return parsed
        }

        val legacyData = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginList", JSONObject()),
        )
        return parseQuickLoginAccountList(legacyData)
    }

    fun setQuickLoginAccount(baseUrl: String, uin: String) {
        requireSuccess(
            authorizedPostResponse(
                baseUrl = baseUrl,
                path = "/QQLogin/SetQuickLoginQQ",
                payload = JSONObject().apply {
                    put("uin", uin.trim())
                },
            ),
        )
    }

    fun quickLogin(baseUrl: String, uin: String) {
        requireSuccess(
            authorizedPostResponse(
                baseUrl = baseUrl,
                path = "/QQLogin/SetQuickLogin",
                payload = JSONObject().apply {
                    put("uin", uin.trim())
                },
            ),
        )
    }

    fun refreshQrCode(baseUrl: String) {
        requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/RefreshQRcode", JSONObject()),
        )
    }

    fun passwordLogin(baseUrl: String, uin: String, password: String): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/PasswordLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun captchaLogin(
        baseUrl: String,
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/CaptchaLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
                put("ticket", ticket.trim())
                put("randstr", randstr.trim())
                put("sid", sid.trim())
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun newDeviceLogin(
        baseUrl: String,
        uin: String,
        password: String,
        newDeviceSig: String,
    ): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/NewDeviceLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
                put("newDevicePullQrCodeSig", newDeviceSig)
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun getNewDeviceQRCode(
        baseUrl: String,
        uin: String,
        jumpUrl: String,
    ): NewDeviceQrCodeResult {
        val data = requireObject(
            requireSuccess(
                authorizedPostResponse(
                    baseUrl = baseUrl,
                    path = "/QQLogin/GetNewDeviceQRCode",
                    payload = JSONObject().apply {
                        put("uin", uin.trim())
                        put("jumpUrl", jumpUrl.trim())
                    },
                ),
            ),
        )
        return NewDeviceQrCodeResult(
            qrUrl = data.optString("str_url").trim(),
            bytesToken = data.optString("bytes_token").trim(),
        )
    }

    fun pollNewDeviceQRCode(
        baseUrl: String,
        uin: String,
        bytesToken: String,
    ): NewDeviceQrPollResult {
        val data = requireObject(
            requireSuccess(
                authorizedPostResponse(
                    baseUrl = baseUrl,
                    path = "/QQLogin/PollNewDeviceQR",
                    payload = JSONObject().apply {
                        put("uin", uin.trim())
                        put("bytesToken", bytesToken.trim())
                    },
                ),
            ),
        )
        return NewDeviceQrPollResult(
            guaranteeStatus = data.optInt("uint32_guarantee_status", 0),
            successToken = data.optString("str_nt_succ_token").trim(),
        )
    }

    private fun parseLoginActionResult(data: Any?): LoginActionResult {
        val payload = requireObject(data)
        val needCaptcha = payload.optBoolean("needCaptcha", false)
        val needNewDevice = payload.optBoolean("needNewDevice", false)

        return when {
            needCaptcha -> LoginActionResult(
                completed = false,
                needCaptcha = true,
                captchaUrl = payload.optString("proofWaterUrl"),
            )

            needNewDevice -> LoginActionResult(
                completed = false,
                needNewDevice = true,
                newDeviceJumpUrl = payload.optString("jumpUrl"),
                newDeviceSig = payload.optString("newDevicePullQrCodeSig"),
            )

            else -> LoginActionResult(completed = true)
        }
    }

    private fun authorizedPostResponse(
        baseUrl: String,
        path: String,
        payload: JSONObject,
        retryUnauthorized: Boolean = true,
    ): ApiEnvelope {
        val response = postJson(
            endpoint = normalizeApiBaseUrl(baseUrl) + path,
            payload = payload,
            authorization = ensureCredential(baseUrl),
        )
        val envelope = ApiEnvelope(
            code = response.optInt("code", 1),
            message = response.optString("message").ifBlank {
                if (response.optInt("code", 1) == 0) "success" else "Unknown error"
            },
            data = response.opt("data").takeUnless { it == null || it == JSONObject.NULL },
        )

        if (
            envelope.code != 0 &&
            retryUnauthorized &&
            envelope.message.contains("Unauthorized", ignoreCase = true)
        ) {
            clearCredential()
            return authorizedPostResponse(baseUrl, path, payload, retryUnauthorized = false)
        }
        return envelope
    }

    private fun ensureCredential(baseUrl: String): String {
        if (credential.isNotBlank()) return credential

        val loginResponse = postJson(
            endpoint = normalizeApiBaseUrl(baseUrl) + "/auth/login",
            payload = JSONObject().apply {
                put("hash", sha256("$WEB_UI_TOKEN.napcat"))
            },
            authorization = null,
        )
        if (loginResponse.optInt("code", 1) != 0) {
            throw IllegalStateException(loginResponse.optString("message").ifBlank { "WebUI login failed" })
        }

        credential = loginResponse
            .optJSONObject("data")
            ?.optString("Credential")
            .orEmpty()
        if (credential.isBlank()) {
            throw IllegalStateException("Missing WebUI credential")
        }
        return credential
    }

    private fun postJson(
        endpoint: String,
        payload: JSONObject,
        authorization: String?,
    ): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (!authorization.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $authorization")
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (body.isBlank()) {
                return JSONObject().apply {
                    put("code", if (responseCode in 200..299) 0 else 1)
                    put("message", "Empty response")
                    put("data", JSONObject())
                }
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun requireSuccess(response: ApiEnvelope): Any? {
        if (response.code != 0) {
            throw IllegalStateException(response.message.ifBlank { "Unknown error" })
        }
        return response.data
    }

    private fun requireObject(value: Any?): JSONObject {
        return value as? JSONObject ?: JSONObject()
    }

    private fun unwrapValue(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }.trim()
    }

    private fun parseQuickLoginAccountList(value: Any?): List<SavedQqAccount> {
        val accounts = mutableListOf<SavedQqAccount>()
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    when (val item = value.opt(index)) {
                        is JSONObject -> {
                            val uin = item.optString("uin").trim()
                            if (uin.isNotBlank()) {
                                accounts += SavedQqAccount(
                                    uin = uin,
                                    nickName = item.optString("nickName").trim(),
                                    avatarUrl = item.optString("faceUrl").trim(),
                                )
                            }
                        }

                        is String -> {
                            val uin = item.trim()
                            if (uin.isNotBlank()) {
                                accounts += SavedQqAccount(uin = uin)
                            }
                        }
                    }
                }
            }

            is JSONObject -> {
                val array = value.optJSONArray("list")
                if (array != null) {
                    return parseQuickLoginAccountList(array)
                }
            }
        }
        return accounts.distinctBy { it.uin }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    private fun normalizeApiBaseUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        return if (normalized.endsWith("/api")) normalized else "$normalized/api"
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
