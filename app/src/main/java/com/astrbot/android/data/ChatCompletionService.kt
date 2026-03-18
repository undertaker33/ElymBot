package com.astrbot.android.data

import android.content.Context
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
import java.io.File
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

object ChatCompletionService {
    data class SttProbeResult(
        val state: FeatureSupportState,
        val transcript: String,
    )

    private const val MULTIMODAL_PROMPT =
        "Briefly describe this image in one or two sentences. Mention the main landmark or tower, the city skyline or waterfront, and whether it is day or night."
    private const val PROBE_IMAGE_ASSET_NAME = "vl_probe_scene.jpg"
    private const val PROBE_IMAGE_MIME_TYPE = "image/jpeg"
    private const val PROBE_STT_AUDIO_ASSET_NAME = "TestVoice.wav"
    private const val PROBE_STT_AUDIO_MIME_TYPE = "audio/wav"
    private const val PROBE_STT_TEXT = "你好世界，hello world"
    private const val PROBE_TTS_TEXT = "AstrBot text to speech probe."
    private const val DEFAULT_TTS_MIME_TYPE = "audio/mpeg"
    private const val LANDMARK_SCORE = 0.5
    private const val SKYLINE_SCORE = 0.3
    private const val NIGHT_SCORE = 0.2
    private const val PROBE_PASS_SCORE = 0.5
    private const val BAILIAN_STT_MAX_BYTES = 10 * 1024 * 1024

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var probeImageBase64: String? = null

    @Volatile
    private var probeSttAudioBase64: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

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

    fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
        require(ProviderCapability.STT in provider.capabilities) { "STT checks are only available for STT models." }
        val attachment = ConversationAttachment(
            id = "stt-probe",
            type = "audio",
            mimeType = PROBE_STT_AUDIO_MIME_TYPE,
            fileName = PROBE_STT_AUDIO_ASSET_NAME,
            base64Data = requireProbeSttAudioBase64(),
        )
        val transcript = transcribeAudio(provider, attachment)
        val normalized = transcript.lowercase(Locale.US)
        RuntimeLogRepository.append("STT probe transcript: expected=$PROBE_STT_TEXT actual=${transcript.take(160)}")
        val chineseHits = listOf(
            normalized.contains("你好") || normalized.contains("你 好"),
            normalized.contains("世界"),
        ).count { it }
        val englishHits = listOf(
            normalized.contains("hello"),
            normalized.contains("world"),
        ).count { it }
        val totalHits = chineseHits + englishHits
        RuntimeLogRepository.append(
            "STT probe hits: chinese=$chineseHits english=$englishHits total=$totalHits transcript=${transcript.take(160)}",
        )
        val state = if (totalHits >= 3 || (totalHits >= 2 && chineseHits >= 1 && englishHits >= 1)) {
            FeatureSupportState.SUPPORTED
        } else {
            FeatureSupportState.UNSUPPORTED
        }
        return SttProbeResult(state = state, transcript = transcript)
    }

    fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        require(ProviderCapability.TTS in provider.capabilities) { "TTS checks are only available for TTS models." }
        val attachment = synthesizeSpeech(provider, PROBE_TTS_TEXT)
        val size = decodeAttachmentBytes(attachment).size
        RuntimeLogRepository.append("TTS probe bytes: provider=${provider.name} size=$size")
        return if (size >= 1024) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
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

    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        require(ProviderCapability.STT in provider.capabilities) { "This provider is not configured as an STT model." }
        RuntimeLogRepository.append(
            "STT route: provider=${provider.name} type=${provider.providerType.name} model=${provider.model} file=${attachment.fileName.ifBlank { "-" }} mime=${attachment.mimeType.ifBlank { "-" }}",
        )
        return when (provider.providerType) {
            ProviderType.WHISPER_API,
            ProviderType.XINFERENCE_STT,
            -> transcribeWithOpenAiStyle(provider, attachment)

            ProviderType.BAILIAN_STT -> transcribeWithBailianStt(provider, attachment)

            else -> throw IllegalStateException("STT routing is not implemented for ${provider.providerType.name}.")
        }
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String = "",
    ): ConversationAttachment {
        require(ProviderCapability.TTS in provider.capabilities) { "This provider is not configured as a TTS model." }
        RuntimeLogRepository.append(
            "TTS route: provider=${provider.name} type=${provider.providerType.name} model=${provider.model} voice=${voiceId.ifBlank { "-" }} chars=${text.length}",
        )
        return when (provider.providerType) {
            ProviderType.OPENAI_TTS -> synthesizeWithOpenAiTts(provider, text, voiceId)
            ProviderType.BAILIAN_TTS -> synthesizeWithBailianTts(provider, text, voiceId)
            ProviderType.MINIMAX_TTS -> synthesizeWithMiniMaxTts(provider, text, voiceId)
            else -> throw IllegalStateException("TTS routing is not implemented for ${provider.providerType.name}.")
        }
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
        val normalizedMessages = retainOnlyLatestUserAttachments(messages)
        val latestUserHasImages = normalizedMessages
            .lastOrNull { it.role == "user" }
            ?.attachments
            ?.any { it.type == "image" } == true
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
                val imageAttachments = message.attachments.filter { it.type == "image" }
                val captions = imageAttachments.mapIndexedNotNull { index, attachment ->
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

    private fun transcribeWithOpenAiStyle(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val audioBytes = resolveAttachmentBytes(attachment)
        val boundary = "AstrBotBoundary${System.currentTimeMillis()}"
        val connection = openMultipartConnection(provider.baseUrl.trimEnd('/') + "/audio/transcriptions", boundary).apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            connection.outputStream.use { output ->
                writeMultipartText(output, boundary, "model", provider.model)
                writeMultipartFile(
                    output = output,
                    boundary = boundary,
                    fieldName = "file",
                    fileName = attachment.fileName.ifBlank { "audio-input.mp3" },
                    mimeType = attachment.mimeType.ifBlank { "audio/mpeg" },
                    bytes = audioBytes,
                )
                finishMultipart(output, boundary)
            }
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            JSONObject(body).optString("text").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("STT response is empty.")
        } finally {
            connection.disconnect()
        }
    }

    private fun transcribeWithBailianStt(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        require(provider.model.isNotBlank()) { "STT model is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "input",
                JSONObject().apply {
                    put(
                        "messages",
                        JSONArray()
                            .put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put(
                                        "content",
                                        JSONArray().put(
                                            JSONObject().put("text", ""),
                                        ),
                                    )
                                },
                            )
                            .put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put(
                                        "content",
                                        JSONArray().put(
                                            JSONObject().put("audio", buildBailianSttAudioInput(attachment)),
                                        ),
                                    )
                                },
                            ),
                    )
                },
            )
            put(
                "parameters",
                JSONObject().apply {
                    put(
                        "asr_options",
                        JSONObject().apply {
                            put("enable_lid", true)
                            put("enable_itn", false)
                        },
                    )
                },
            )
        }
        val endpoint = provider.baseUrl.trimEnd('/') + "/services/aigc/multimodal-generation/generation"
        val connection = openConnection(endpoint, "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            executeJsonRequest(connection, payload) { body ->
                extractDashScopeMessageText(body)
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("STT response is empty.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun synthesizeWithOpenAiTts(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
    ): ConversationAttachment {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("input", text)
            put("voice", voiceId.ifBlank { "alloy" })
            put("format", "mp3")
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/audio/speech", "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            setRequestProperty("Accept", DEFAULT_TTS_MIME_TYPE)
        }
        return try {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val body = readBody(connection, responseCode)
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw IllegalStateException("TTS response is empty.")
            }
            createAudioAttachment(bytes = bytes, mimeType = DEFAULT_TTS_MIME_TYPE, fileExtension = "mp3")
        } finally {
            connection.disconnect()
        }
    }

    private fun synthesizeWithBailianTts(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
    ): ConversationAttachment {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val modelName = provider.model.trim()
        require(modelName.isNotBlank()) { "TTS model is empty." }
        if (!isQwenTtsModel(modelName)) {
            throw IllegalStateException(
                "DashScope CosyVoice/Sambert models require a WebSocket route that is not implemented on Android yet. Use a qwen3-tts-* model for now.",
            )
        }
        val payload = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put(
                "input",
                JSONObject().apply {
                    put("text", text)
                    put("voice", voiceId.ifBlank { "Cherry" })
                    put("language_type", inferDashScopeLanguageType(text))
                },
            )
        }
        val endpoint = provider.baseUrl.trimEnd('/') + "/services/aigc/multimodal-generation/generation"
        val connection = openConnection(endpoint, "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            setRequestProperty("X-DashScope-SSE", "enable")
        }
        return try {
            synthesizeWithDashScopeSse(connection, payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun synthesizeWithMiniMaxTts(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
    ): ConversationAttachment {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        require(provider.model.isNotBlank()) { "TTS model is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("text", text)
            put("stream", false)
            put("language_boost", "auto")
            put("output_format", "hex")
            put(
                "voice_setting",
                JSONObject().apply {
                    put("voice_id", voiceId.ifBlank { "Chinese (Mandarin)_Warm_Girl" })
                    put("speed", 1.0)
                    put("vol", 1.0)
                    put("pitch", 0)
                },
            )
            put(
                "audio_setting",
                JSONObject().apply {
                    put("sample_rate", 32000)
                    put("bitrate", 128000)
                    put("format", "mp3")
                    put("channel", 1)
                },
            )
        }
        val endpoint = buildMiniMaxTtsEndpoint(provider.baseUrl)
        val connection = openConnection(endpoint, "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            executeJsonRequest(connection, payload) { body ->
                val json = JSONObject(body)
                val statusCode = json.optJSONObject("base_resp")?.optInt("status_code", 0) ?: 0
                if (statusCode != 0) {
                    val statusMessage = json.optJSONObject("base_resp")?.optString("status_msg").orEmpty()
                    throw IllegalStateException("MiniMax TTS failed: ${statusMessage.ifBlank { "status=$statusCode" }}")
                }
                val hexAudio = json.optJSONObject("data")?.optString("audio").orEmpty()
                if (hexAudio.isBlank()) {
                    throw IllegalStateException("MiniMax TTS response is empty.")
                }
                createAudioAttachment(
                    bytes = hexToBytes(hexAudio),
                    mimeType = DEFAULT_TTS_MIME_TYPE,
                    fileExtension = "mp3",
                )
            }
        } finally {
            connection.disconnect()
        }
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
        val probeImageBase64 = requireProbeImageBase64()
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
                                    JSONObject().put("url", "data:$PROBE_IMAGE_MIME_TYPE;base64,$probeImageBase64"),
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
                evaluateProbeDescription(content)
            },
        )
    }

    private fun probeOllamaMultimodal(provider: ProviderProfile): FeatureSupportState {
        val probeImageBase64 = requireProbeImageBase64()
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", MULTIMODAL_PROMPT)
                        .put("images", JSONArray().put(probeImageBase64)),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = provider.baseUrl.trimEnd('/') + "/api/chat",
            payload = payload,
            configure = {},
            parser = { body ->
                val content = JSONObject(body).optJSONObject("message")?.optString("content").orEmpty()
                evaluateProbeDescription(content)
            },
        )
    }

    private fun probeGeminiMultimodal(provider: ProviderProfile): FeatureSupportState {
        require(provider.apiKey.isNotBlank()) { "API key cannot be empty." }
        val probeImageBase64 = requireProbeImageBase64()
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
                                    JSONObject().put("mimeType", PROBE_IMAGE_MIME_TYPE).put("data", probeImageBase64),
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
                evaluateProbeDescription(text)
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
                FeatureSupportState.UNKNOWN
            }
    }

    private fun evaluateProbeDescription(content: String): FeatureSupportState {
        val normalized = content.trim().lowercase()
        if (normalized.isBlank()) {
            RuntimeLogRepository.append("Multimodal probe judged unsupported: empty response")
            return FeatureSupportState.UNSUPPORTED
        }
        if (NEGATIVE_PROBE_PATTERNS.any { normalized.contains(it) }) {
            RuntimeLogRepository.append("Multimodal probe judged unsupported: model denied seeing image")
            return FeatureSupportState.UNSUPPORTED
        }

        var score = 0.0
        val hits = mutableListOf<String>()

        if (LANDMARK_PROBE_KEYWORDS.any { normalized.contains(it) }) {
            score += LANDMARK_SCORE
            hits += "landmark"
        }
        if (SKYLINE_PROBE_KEYWORDS.any { normalized.contains(it) }) {
            score += SKYLINE_SCORE
            hits += "skyline"
        }
        if (NIGHT_PROBE_KEYWORDS.any { normalized.contains(it) }) {
            score += NIGHT_SCORE
            hits += "night"
        }

        RuntimeLogRepository.append(
            "Multimodal probe scored: score=${String.format(Locale.US, "%.2f", score)} hits=${hits.joinToString(",")} response=${content.take(160)}",
        )
        return if (score >= PROBE_PASS_SCORE) {
            FeatureSupportState.SUPPORTED
        } else {
            FeatureSupportState.UNSUPPORTED
        }
    }

    private fun requireProbeImageBase64(): String {
        probeImageBase64?.let { return it }
        val context = appContext ?: throw IllegalStateException("ChatCompletionService is not initialized.")
        val encoded = context.assets.open(PROBE_IMAGE_ASSET_NAME).use { stream ->
            Base64.getEncoder().encodeToString(stream.readBytes())
        }
        probeImageBase64 = encoded
        return encoded
    }

    private fun requireProbeSttAudioBase64(): String {
        probeSttAudioBase64?.let { return it }
        val context = appContext ?: throw IllegalStateException("ChatCompletionService is not initialized.")
        val encoded = context.assets.open(PROBE_STT_AUDIO_ASSET_NAME).use { stream ->
            Base64.getEncoder().encodeToString(stream.readBytes())
        }
        probeSttAudioBase64 = encoded
        return encoded
    }

    private fun decodeAttachmentBytes(attachment: ConversationAttachment): ByteArray {
        attachment.base64Data.takeIf { it.isNotBlank() }?.let { encoded ->
            return Base64.getDecoder().decode(encoded)
        }
        attachment.remoteUrl.takeIf { it.isNotBlank() }?.let { remoteUrl ->
            val upgradedUrl = upgradeRemoteBinaryUrl(remoteUrl)
            return URL(upgradedUrl).openStream().use { it.readBytes() }
        }
        throw IllegalStateException("Audio attachment payload is empty.")
    }

    private fun upgradeRemoteBinaryUrl(remoteUrl: String): String {
        return if (remoteUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + remoteUrl.removePrefix("http://")
        } else {
            remoteUrl
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

    private fun openMultipartConnection(endpoint: String, boundary: String): HttpURLConnection {
        RuntimeLogRepository.append("HTTP multipart request: endpoint=$endpoint")
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
    }

    private fun buildOpenAiContent(message: ConversationMessage): Any {
        val imageAttachments = message.attachments.filter { it.type == "image" }
        if (imageAttachments.isEmpty()) return message.content
        return JSONArray().apply {
            if (message.content.isNotBlank()) {
                put(JSONObject().put("type", "text").put("text", message.content))
            }
            imageAttachments.forEach { attachment ->
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
            attachments.filter { it.type == "image" }.forEach { attachment ->
                put(resolveAttachmentBase64(attachment))
            }
        }
    }

    private fun buildGeminiParts(message: ConversationMessage): JSONArray {
        return JSONArray().apply {
            if (message.content.isNotBlank()) {
                put(JSONObject().put("text", message.content))
            }
            message.attachments.filter { it.type == "image" }.forEach { attachment ->
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
        return Base64.getEncoder().encodeToString(resolveAttachmentBytes(attachment))
    }

    private fun resolveAttachmentBytes(attachment: ConversationAttachment): ByteArray {
        if (attachment.base64Data.isNotBlank()) {
            return Base64.getDecoder().decode(attachment.base64Data)
        }
        val location = attachment.remoteUrl.takeIf { it.isNotBlank() }
            ?: attachment.fileName.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Attachment data is missing.")

        if (location.startsWith("data:", ignoreCase = true)) {
            return decodeDataUrl(location)
        }
        if (looksLikeHttpUrl(location)) {
            return downloadBytes(location)
        }
        if (location.startsWith("file://", ignoreCase = true)) {
            return File(URI(location)).readBytes()
        }

        val localFile = File(location)
        if (localFile.exists()) {
            return localFile.readBytes()
        }

        throw IllegalStateException("Attachment data is missing.")
    }

    private fun downloadBytes(url: String): ByteArray {
        val resolvedUrl = normalizeBinaryDownloadUrl(url)
        val connection = openConnection(resolvedUrl, "GET")
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode while downloading binary content.")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeBinaryDownloadUrl(url: String): String {
        return try {
            val parsed = URL(url)
            val host = parsed.host.orEmpty()
            val shouldUpgradeToHttps = parsed.protocol.equals("http", ignoreCase = true) &&
                host.isNotBlank() &&
                host != "127.0.0.1" &&
                host != "localhost" &&
                !host.endsWith(".local", ignoreCase = true)
            if (!shouldUpgradeToHttps) {
                url
            } else {
                val upgraded = URL("https", parsed.host, parsed.port, parsed.file).toString()
                RuntimeLogRepository.append("Binary download upgraded to HTTPS: $upgraded")
                upgraded
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun buildBailianSttAudioInput(attachment: ConversationAttachment): String {
        val bytes = resolveAttachmentBytes(attachment)
        if (bytes.size > BAILIAN_STT_MAX_BYTES) {
            throw IllegalStateException("Bailian STT audio exceeds the 10MB input limit.")
        }
        val mimeType = inferAudioMimeType(attachment, bytes)
        RuntimeLogRepository.append(
            "Bailian STT input prepared: mime=$mimeType bytes=${bytes.size}",
        )
        return "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun inferAudioMimeType(
        attachment: ConversationAttachment,
        bytes: ByteArray,
    ): String {
        val normalizedMime = attachment.mimeType.trim()
        if (normalizedMime.isNotBlank() && normalizedMime != "audio/mpeg") {
            return normalizedMime
        }

        val headerText = bytes.take(16).toByteArray().toString(StandardCharsets.US_ASCII)
        return when {
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(StandardCharsets.US_ASCII) == "WAVE" -> "audio/wav"
            headerText.startsWith("ID3") -> "audio/mpeg"
            headerText.startsWith("#!AMR") -> "audio/amr"
            headerText.startsWith("#!SILK_V3") -> "audio/silk"
            inferMimeTypeFromName(attachment.fileName).isNotBlank() -> inferMimeTypeFromName(attachment.fileName)
            inferMimeTypeFromName(attachment.remoteUrl).isNotBlank() -> inferMimeTypeFromName(attachment.remoteUrl)
            else -> "audio/mpeg"
        }
    }

    private fun inferMimeTypeFromName(name: String): String {
        val normalized = name.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (normalized) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "amr" -> "audio/amr"
            "ogg", "opus" -> "audio/ogg"
            "aac", "m4a" -> "audio/aac"
            "flac" -> "audio/flac"
            "silk" -> "audio/silk"
            else -> ""
        }
    }

    private fun extractDashScopeMessageText(body: String): String {
        val content = JSONObject(body)
            .optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")

        return when (content) {
            is JSONArray -> {
                buildList {
                    for (index in 0 until content.length()) {
                        val item = content.opt(index)
                        when (item) {
                            is JSONObject -> item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                            is String -> item.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.joinToString(separator = "\n").trim()
            }

            is String -> content.trim()
            else -> ""
        }
    }

    private fun decodeDataUrl(dataUrl: String): ByteArray {
        val encoded = dataUrl.substringAfter("base64,", "")
        if (encoded.isBlank()) {
            throw IllegalStateException("Attachment data is missing.")
        }
        return Base64.getDecoder().decode(encoded)
    }

    private fun looksLikeHttpUrl(value: String): Boolean {
        return value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)
    }

    private fun createAudioAttachment(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): ConversationAttachment {
        return ConversationAttachment(
            id = java.util.UUID.randomUUID().toString(),
            type = "audio",
            mimeType = mimeType,
            fileName = "tts-${System.currentTimeMillis()}.$fileExtension",
            base64Data = Base64.getEncoder().encodeToString(bytes),
        )
    }

    private fun synthesizeWithDashScopeSse(
        connection: HttpURLConnection,
        payload: JSONObject,
    ): ConversationAttachment {
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val body = readBody(connection, responseCode)
            throw IllegalStateException("HTTP $responseCode: $body")
        }

        val audioBytes = ByteArrayOutputStream()
        var finalAudioUrl = ""
        connection.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val payloadLine = line.removePrefix("data:").trim()
                if (!line.startsWith("data:") || payloadLine.isBlank() || payloadLine == "[DONE]") {
                    return@forEach
                }
                val json = runCatching { JSONObject(payloadLine) }.getOrNull() ?: return@forEach
                val audio = json.optJSONObject("output")?.optJSONObject("audio") ?: return@forEach
                val chunk = audio.optString("data")
                if (chunk.isNotBlank()) {
                    audioBytes.write(Base64.getDecoder().decode(chunk))
                }
                val url = audio.optString("url")
                if (url.isNotBlank()) {
                    finalAudioUrl = url
                }
            }
        }

        val resolvedBytes = when {
            audioBytes.size() > 0 -> {
                RuntimeLogRepository.append(
                    "DashScope TTS stream assembled from PCM chunks: bytes=${audioBytes.size()} sampleRate=24000 channels=1",
                )
                wrapPcm16MonoAsWav(
                    pcmBytes = audioBytes.toByteArray(),
                    sampleRate = 24_000,
                )
            }
            finalAudioUrl.isNotBlank() -> downloadBytes(finalAudioUrl)
            else -> throw IllegalStateException("DashScope TTS response did not return audio data.")
        }
        return createAudioAttachment(
            bytes = resolvedBytes,
            mimeType = "audio/wav",
            fileExtension = "wav",
        )
    }

    private fun inferDashScopeLanguageType(text: String): String {
        return if (text.any { it.code in 0x4E00..0x9FFF }) "Chinese" else "Auto"
    }

    private fun wrapPcm16MonoAsWav(
        pcmBytes: ByteArray,
        sampleRate: Int,
    ): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val dataSize = pcmBytes.size
        val totalSize = 44 + dataSize

        return ByteBuffer.allocate(totalSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(StandardCharsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(StandardCharsets.US_ASCII))
                put("fmt ".toByteArray(StandardCharsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign)
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(StandardCharsets.US_ASCII))
                putInt(dataSize)
                put(pcmBytes)
            }
            .array()
    }

    private fun isQwenTtsModel(model: String): Boolean {
        val normalized = model.trim().lowercase()
        return normalized.startsWith("qwen") && normalized.contains("tts")
    }

    private fun buildMiniMaxTtsEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/t2a_v2")) normalized else "$normalized/t2a_v2"
    }

    private fun hexToBytes(hex: String): ByteArray {
        val normalized = hex.trim()
        require(normalized.length % 2 == 0) { "MiniMax TTS returned invalid hex audio." }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun writeMultipartText(
        output: java.io.OutputStream,
        boundary: String,
        fieldName: String,
        value: String,
    ) {
        output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(value.toByteArray(StandardCharsets.UTF_8))
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeMultipartFile(
        output: java.io.OutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n".toByteArray(StandardCharsets.UTF_8),
        )
        output.write("Content-Type: $mimeType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun finishMultipart(
        output: java.io.OutputStream,
        boundary: String,
    ) {
        output.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun ProviderProfile.hasMultimodalSupport(): Boolean {
        return multimodalProbeSupport == FeatureSupportState.SUPPORTED ||
            multimodalRuleSupport == FeatureSupportState.SUPPORTED ||
            inferMultimodalRuleSupport(providerType, model) == FeatureSupportState.SUPPORTED
    }

    private val NEGATIVE_PROBE_PATTERNS = listOf(
        "can't see the image",
        "cannot see the image",
        "can't view the image",
        "cannot view the image",
        "can't access the image",
        "cannot access the image",
        "unable to see the image",
        "unable to view the image",
        "i can't see",
        "i cannot see",
        "i can't view",
        "i cannot view",
        "i do not see an image",
        "i don't see an image",
        "i don't see the image",
        "i do not see the image",
        "no image provided",
        "image was not provided",
        "没有看到图片",
        "无法看到图片",
        "看不到图片",
        "无法查看图片",
        "没有图像",
        "未提供图片",
    )

    private val LANDMARK_PROBE_KEYWORDS = listOf(
        "oriental pearl",
        "pearl tower",
        "tv tower",
        "observation tower",
        "tower",
        "shanghai",
        "东方明珠",
        "明珠塔",
        "电视塔",
        "塔",
        "上海",
    )

    private val SKYLINE_PROBE_KEYWORDS = listOf(
        "skyline",
        "cityscape",
        "skyscraper",
        "waterfront",
        "river",
        "buildings",
        "city",
        "天际线",
        "城市",
        "高楼",
        "建筑",
        "江边",
        "江景",
        "河边",
        "夜景",
    )

    private val NIGHT_PROBE_KEYWORDS = listOf(
        "night",
        "nighttime",
        "evening",
        "lit",
        "illuminated",
        "lights",
        "夜晚",
        "晚上",
        "夜间",
        "灯光",
        "亮灯",
    )
}
