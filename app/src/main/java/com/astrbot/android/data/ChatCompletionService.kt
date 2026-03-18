package com.astrbot.android.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConversationAttachment
import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.supportsChatCompletions
import com.astrbot.android.model.usesOpenAiStyleChatApi
import com.astrbot.android.runtime.RuntimeLogRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

object ChatCompletionService {
    private const val MULTIMODAL_PROMPT = "Describe the main subject of this image in one short sentence."
    private const val TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9VE3d2QAAAAASUVORK5CYII="

    fun fetchModels(baseUrl: String, apiKey: String, providerType: ProviderType): List<String> {
        require(baseUrl.isNotBlank()) { "Base URL cannot be empty." }
        return when {
            providerType.usesOpenAiStyleChatApi() -> fetchOpenAiStyleModels(baseUrl, apiKey)
            providerType == ProviderType.OLLAMA -> fetchOllamaModels(baseUrl)
            providerType == ProviderType.GEMINI -> fetchGeminiModels(baseUrl, apiKey)
            else -> throw IllegalStateException("Pull models is not supported for ${providerType.name}.")
        }
    }

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return inferMultimodalRuleSupport(provider.providerType, provider.model)
    }

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        require(ProviderCapability.CHAT in provider.capabilities) { "Multimodal checks are only available for chat models." }
        require(provider.providerType.supportsChatCompletions()) { "This provider does not support chat completions." }

        val result = when {
            provider.providerType.usesOpenAiStyleChatApi() -> probeOpenAiStyleMultimodal(provider)
            provider.providerType == ProviderType.OLLAMA -> probeOllamaMultimodal(provider)
            provider.providerType == ProviderType.GEMINI -> probeGeminiMultimodal(provider)
            else -> FeatureSupportState.UNKNOWN
        }
        RuntimeLogRepository.append("Multimodal probe: provider=${provider.name} result=${result.name}")
        return result
    }

    fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String? = null,
        config: ConfigProfile? = null,
        availableProviders: List<ProviderProfile> = emptyList(),
    ): String {
        val preparedMessages = prepareMessagesForConfig(
            provider = provider,
            messages = messages,
            config = config,
            availableProviders = availableProviders,
        )
        return sendChat(
            provider = provider,
            messages = preparedMessages,
            systemPrompt = systemPrompt,
        )
    }

    fun sendChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String? = null,
    ): String {
        require(provider.providerType.supportsChatCompletions()) { "This provider type does not support chat requests." }
        require(provider.capabilities.contains(ProviderCapability.CHAT)) { "This provider is not configured as a chat model." }

        return when {
            provider.providerType.usesOpenAiStyleChatApi() -> sendOpenAiStyleChat(provider, messages, systemPrompt)
            provider.providerType == ProviderType.OLLAMA -> sendOllamaChat(provider, messages, systemPrompt)
            provider.providerType == ProviderType.GEMINI -> sendGeminiChat(provider, messages, systemPrompt)
            else -> throw IllegalStateException("Chat routing is not implemented for ${provider.providerType.name}.")
        }
    }

    private fun prepareMessagesForConfig(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): List<ConversationMessage> {
        val latestUserIndex = messages.indexOfLast { it.role == "user" }
        val normalizedMessages = retainOnlyLatestUserAttachments(messages)
        val latestUserHasImages = normalizedMessages
            .lastOrNull { it.role == "user" }
            ?.attachments
            ?.isNotEmpty() == true
        if (!latestUserHasImages) {
            return normalizedMessages
        }
        if (config?.imageCaptionTextEnabled == true) {
            val captionProvider = resolveCaptionProvider(
                chatProvider = provider,
                config = config,
                availableProviders = availableProviders,
            )
            if (captionProvider == null) {
                RuntimeLogRepository.append("Image route: caption mode enabled but no multimodal caption provider available, attachments stripped")
                return normalizedMessages.map { it.copy(attachments = emptyList()) }
            }
            RuntimeLogRepository.append("Image route: caption text mode using provider=${captionProvider.name}")
            return buildCaptionedMessages(
                messages = normalizedMessages,
                captionProvider = captionProvider,
                prompt = config.imageCaptionPrompt,
            )
        }

        if (provider.hasMultimodalSupport()) {
            RuntimeLogRepository.append("Image route: direct multimodal chat provider=${provider.name}")
            return normalizedMessages
        }

        RuntimeLogRepository.append("Image route: chat provider has no multimodal support and caption mode is off, attachments stripped")
        return normalizedMessages.map { it.copy(attachments = emptyList()) }
    }

    private fun retainOnlyLatestUserAttachments(messages: List<ConversationMessage>): List<ConversationMessage> {
        val lastUserMessageIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserMessageIndex == -1) {
            return messages.map { it.copy(attachments = emptyList()) }
        }
        return messages.mapIndexed { index, message ->
            if (message.attachments.isEmpty()) {
                message
            } else if (message.role == "user" && index == lastUserMessageIndex) {
                message
            } else {
                message.copy(attachments = emptyList())
            }
        }
    }

    private fun resolveCaptionProvider(
        chatProvider: ProviderProfile,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
    ): ProviderProfile? {
        val multimodalProviders = availableProviders.filter {
            it.enabled &&
                ProviderCapability.CHAT in it.capabilities &&
                it.hasMultimodalSupport()
        }
        return multimodalProviders.firstOrNull { it.id == config.defaultVisionProviderId }
            ?: chatProvider.takeIf {
                it.enabled &&
                    ProviderCapability.CHAT in it.capabilities &&
                    it.hasMultimodalSupport()
            }
            ?: multimodalProviders.firstOrNull()
    }

    private fun buildCaptionedMessages(
        messages: List<ConversationMessage>,
        captionProvider: ProviderProfile,
        prompt: String,
    ): List<ConversationMessage> {
        val resolvedPrompt = prompt.ifBlank { "Describe the image in detail before sending it to the chat model." }
        return messages.map { message ->
            if (message.role != "user" || message.attachments.isEmpty()) {
                message.copy(attachments = emptyList())
            } else {
                val captions = message.attachments.mapIndexedNotNull { index, attachment ->
                    captionAttachment(
                        provider = captionProvider,
                        attachment = attachment,
                        prompt = resolvedPrompt,
                        attachmentIndex = index + 1,
                    )
                }
                message.copy(
                    content = buildCaptionedContent(message.content, captions),
                    attachments = emptyList(),
                )
            }
        }
    }

    private fun buildCaptionedContent(
        originalText: String,
        captions: List<String>,
    ): String {
        val trimmedText = originalText.trim()
        if (captions.isEmpty()) return trimmedText
        val captionBlock = captions.joinToString(separator = "\n")
        return buildString {
            if (trimmedText.isNotBlank()) {
                append(trimmedText)
                append("\n\n")
            }
            append(captionBlock)
        }.trim()
    }

    private fun captionAttachment(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
        prompt: String,
        attachmentIndex: Int,
    ): String? {
        val caption = when {
            provider.providerType.usesOpenAiStyleChatApi() -> captionWithOpenAiStyle(provider, attachment, prompt)
            provider.providerType == ProviderType.OLLAMA -> captionWithOllama(provider, attachment, prompt)
            provider.providerType == ProviderType.GEMINI -> captionWithGemini(provider, attachment, prompt)
            else -> null
        }?.trim().orEmpty()
        return caption.takeIf { it.isNotBlank() }?.let { "[Image $attachmentIndex] $it" }
    }

    private fun fetchOpenAiStyleModels(baseUrl: String, apiKey: String): List<String> {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        val connection = openConnection(baseUrl.trimEnd('/') + "/models", "GET").apply {
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("data") ?: JSONArray() }
    }

    private fun fetchOllamaModels(baseUrl: String): List<String> {
        val connection = openConnection(baseUrl.trimEnd('/') + "/api/tags", "GET")
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("models") ?: JSONArray() }
    }

    private fun fetchGeminiModels(baseUrl: String, apiKey: String): List<String> {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        val endpoint = baseUrl.trimEnd('/') + "/models?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        val connection = openConnection(endpoint, "GET")
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("models") ?: JSONArray() }
            .map { it.removePrefix("models/") }
    }

    private fun readModelList(connection: HttpURLConnection, arrayProvider: (String) -> JSONArray): List<String> {
        return try {
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            val array = arrayProvider(body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("name") }
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        } finally {
            connection.disconnect()
        }
    }

    private fun probeOpenAiStyleMultimodal(provider: ProviderProfile): FeatureSupportState {
        require(provider.apiKey.isNotBlank()) { "API key cannot be empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", MULTIMODAL_PROMPT))
                            .put(
                                JSONObject().put("type", "image_url").put(
                                    "image_url",
                                    JSONObject().put("url", "data:image/png;base64,$TEST_IMAGE_BASE64"),
                                ),
                            ),
                    ),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions",
            payload = payload,
            configure = { connection -> connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}") },
            parser = { body ->
                val content = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun probeOllamaMultimodal(provider: ProviderProfile): FeatureSupportState {
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", MULTIMODAL_PROMPT)
                        .put("images", JSONArray().put(TEST_IMAGE_BASE64)),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = provider.baseUrl.trimEnd('/') + "/api/chat",
            payload = payload,
            configure = {},
            parser = { body ->
                val content = JSONObject(body).optJSONObject("message")?.optString("content").orEmpty()
                if (content.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun probeGeminiMultimodal(provider: ProviderProfile): FeatureSupportState {
        require(provider.apiKey.isNotBlank()) { "API key cannot be empty." }
        val modelName = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = provider.baseUrl.trimEnd('/') + "/$modelName:generateContent?key=" +
            URLEncoder.encode(provider.apiKey, StandardCharsets.UTF_8.name())
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", MULTIMODAL_PROMPT))
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject().put("mimeType", "image/png").put("data", TEST_IMAGE_BASE64),
                                ),
                            ),
                    ),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = endpoint,
            payload = payload,
            configure = {},
            parser = { body ->
                val text = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                if (text.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun sendOpenAiStyleChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().apply {
                    if (!systemPrompt.isNullOrBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    }
                    messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role)
                                .put("content", buildOpenAiContent(message)),
                        )
                    }
                },
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/chat/completions", "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun sendOllamaChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
    ): String {
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    if (!systemPrompt.isNullOrBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    }
                    messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role)
                                .put("content", message.content.ifBlank { "Describe this image." })
                                .put("images", buildOllamaImages(message.attachments)),
                        )
                    }
                },
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/api/chat", "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body).optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun sendGeminiChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val modelName = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = provider.baseUrl.trimEnd('/') + "/$modelName:generateContent?key=" +
            URLEncoder.encode(provider.apiKey, StandardCharsets.UTF_8.name())
        val payload = JSONObject().apply {
            if (!systemPrompt.isNullOrBlank()) {
                put(
                    "system_instruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
            }
            put(
                "contents",
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", if (message.role == "assistant") "model" else "user")
                                .put("parts", buildGeminiParts(message)),
                        )
                    }
                },
            )
        }
        val connection = openConnection(endpoint, "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun captionWithOpenAiStyle(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
        prompt: String,
    ): String? {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", prompt))
                                .put(buildOpenAiImagePart(attachment)),
                        ),
                ),
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/chat/completions", "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        }
    }

    private fun captionWithOllama(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
        prompt: String,
    ): String? {
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                        .put("images", JSONArray().put(resolveAttachmentBase64(attachment))),
                ),
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/api/chat", "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body).optJSONObject("message")?.optString("content")
        }
    }

    private fun captionWithGemini(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
        prompt: String,
    ): String? {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val modelName = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = provider.baseUrl.trimEnd('/') + "/$modelName:generateContent?key=" +
            URLEncoder.encode(provider.apiKey, StandardCharsets.UTF_8.name())
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", attachment.mimeType.ifBlank { "image/jpeg" })
                                        .put("data", resolveAttachmentBase64(attachment)),
                                ),
                            ),
                    ),
                ),
            )
        }
        val connection = openConnection(endpoint, "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }
    }

    private fun executeProbeRequest(
        endpoint: String,
        payload: JSONObject,
        configure: (HttpURLConnection) -> Unit,
        parser: (String) -> FeatureSupportState,
    ): FeatureSupportState {
        val connection = openConnection(endpoint, "POST")
        configure(connection)
        return runCatching { executeJsonRequest(connection, payload, parser) }
            .getOrElse { error ->
                RuntimeLogRepository.append("Multimodal probe error: ${error.message ?: error.javaClass.simpleName}")
                FeatureSupportState.UNSUPPORTED
            }
    }

    private fun <T> executeJsonRequest(connection: HttpURLConnection, payload: JSONObject, parser: (String) -> T): T {
        return try {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            parser(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        RuntimeLogRepository.append("HTTP request: method=$method endpoint=$endpoint")
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
        }
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
    }

    private fun buildOpenAiContent(message: ConversationMessage): Any {
        if (message.attachments.isEmpty()) return message.content
        return JSONArray().apply {
            if (message.content.isNotBlank()) {
                put(JSONObject().put("type", "text").put("text", message.content))
            }
            message.attachments.forEach { attachment ->
                put(buildOpenAiImagePart(attachment))
            }
        }
    }

    private fun buildOpenAiImagePart(attachment: ConversationAttachment): JSONObject {
        val imageUrl = "data:${attachment.mimeType.ifBlank { "image/jpeg" }};base64,${resolveAttachmentBase64(attachment)}"
        return JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", imageUrl))
    }

    private fun buildOllamaImages(attachments: List<ConversationAttachment>): JSONArray {
        return JSONArray().apply {
            attachments.forEach { attachment ->
                put(resolveAttachmentBase64(attachment))
            }
        }
    }

    private fun buildGeminiParts(message: ConversationMessage): JSONArray {
        return JSONArray().apply {
            if (message.content.isNotBlank()) {
                put(JSONObject().put("text", message.content))
            }
            message.attachments.forEach { attachment ->
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", attachment.mimeType.ifBlank { "image/jpeg" })
                            .put("data", resolveAttachmentBase64(attachment)),
                    ),
                )
            }
        }
    }

    private fun resolveAttachmentBase64(attachment: ConversationAttachment): String {
        if (attachment.base64Data.isNotBlank()) return attachment.base64Data
        val remoteUrl = attachment.remoteUrl.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Image attachment data is missing.")
        val connection = openConnection(remoteUrl, "GET")
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode while downloading image.")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            Base64.getEncoder().encodeToString(bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun ProviderProfile.hasMultimodalSupport(): Boolean {
        return multimodalProbeSupport == FeatureSupportState.SUPPORTED ||
            multimodalRuleSupport == FeatureSupportState.SUPPORTED ||
            inferMultimodalRuleSupport(providerType, model) == FeatureSupportState.SUPPORTED
    }
}
