package com.astrbot.android.data

import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.runtime.RuntimeLogRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object ChatCompletionService {
    fun fetchModels(
        baseUrl: String,
        apiKey: String,
        providerType: ProviderType,
    ): List<String> {
        require(
            providerType == ProviderType.OPENAI_COMPATIBLE || providerType == ProviderType.DEEPSEEK,
        ) {
            "当前仅支持 OpenAI 兼容和 DeepSeek 的模型发现"
        }
        require(baseUrl.isNotBlank()) {
            "Base URL 不能为空"
        }
        require(apiKey.isNotBlank()) {
            "API Key 不能为空"
        }

        val endpoint = baseUrl.trimEnd('/') + "/models"
        RuntimeLogRepository.append("Fetch models: type=${providerType.name} endpoint=$endpoint")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                RuntimeLogRepository.append("Fetch models failed: HTTP $responseCode")
                throw IllegalStateException("HTTP $responseCode: $body")
            }

            val data = JSONObject(body).optJSONArray("data")
                ?: throw IllegalStateException("模型列表响应缺少 data 字段")
            val models = buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
            RuntimeLogRepository.append("Fetch models success: count=${models.size}")
            models
        } catch (error: Exception) {
            RuntimeLogRepository.append(
                "Fetch models error: ${error.message ?: error.javaClass.simpleName}",
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun sendChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String? = null,
    ): String {
        require(
            provider.providerType == ProviderType.OPENAI_COMPATIBLE || provider.providerType == ProviderType.DEEPSEEK,
        ) {
            "当前仅支持 OpenAI 兼容和 DeepSeek 对话请求"
        }
        require(provider.apiKey.isNotBlank()) {
            "Provider API Key 为空"
        }

        val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().apply {
                    if (!systemPrompt.isNullOrBlank()) {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            },
                        )
                    }
                    messages.forEach { message ->
                        put(
                            JSONObject().apply {
                                put("role", message.role)
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
        }

        RuntimeLogRepository.append(
            "Chat request: provider=${provider.name} model=${provider.model} messages=${messages.size}",
        )
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                RuntimeLogRepository.append("Chat request failed: HTTP $responseCode")
                throw IllegalStateException("HTTP $responseCode: $body")
            }

            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
                ?: throw IllegalStateException("响应中缺少 choices 字段")
            val first = choices.optJSONObject(0)
                ?: throw IllegalStateException("模型响应为空")
            val message = first.optJSONObject("message")
                ?: throw IllegalStateException("响应中缺少 message 字段")
            val content = message.optString("content").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("模型返回了空内容")
            RuntimeLogRepository.append("Chat response success: provider=${provider.name} chars=${content.length}")
            content
        } catch (error: Exception) {
            RuntimeLogRepository.append(
                "Chat request error: ${error.message ?: error.javaClass.simpleName}",
            )
            throw error
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
}
