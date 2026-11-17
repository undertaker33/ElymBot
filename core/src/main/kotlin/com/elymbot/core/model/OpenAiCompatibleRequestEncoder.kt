package com.elymbot.core.model

import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ModelToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

internal object OpenAiCompatibleRequestEncoder {
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val imageMediaTypeRegex = Regex("image/[A-Za-z0-9.+-]+")

    fun visionProbeImageBytes(): ByteArray = VisionProbeImage.pngBytes()

    fun buildRequest(
        request: ModelRequest,
        streaming: Boolean,
        forcedToolChoice: String? = null,
    ): String {
        validateRequest(request)
        return buildJsonObject {
            put("model", request.model.modelId)
            put("messages", buildJsonArray { request.messages.forEach { add(serializeMessage(it)) } })
            put("stream", streaming)
            request.maxOutputTokens?.let { put("max_completion_tokens", it) }
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray { request.tools.forEach { add(serializeTool(it)) } })
                if (forcedToolChoice != null) put("tool_choice", forcedToolChoice)
            }
        }.toString()
    }


    private fun validateRequest(request: ModelRequest) {
        if (request.model.serviceId.isBlank() || request.model.modelId.isBlank()) {
            throw ModelRequestException.Protocol("The model reference is incomplete.")
        }
        if (request.messages.isEmpty()) throw ModelRequestException.Protocol("A model request has no messages.")
        if (request.maxOutputTokens != null && request.maxOutputTokens <= 0) {
            throw ModelRequestException.Protocol("The maximum output token count must be positive.")
        }
        for (message in request.messages) {
            when (message.role) {
                ModelRole.SYSTEM, ModelRole.USER -> if (message.toolCallId != null || message.toolCalls.isNotEmpty()) {
                    throw ModelRequestException.Protocol("A user or system message contains tool-call fields.")
                }
                ModelRole.ASSISTANT -> if (message.toolCallId != null) {
                    throw ModelRequestException.Protocol("An assistant message contains a tool-result ID.")
                }
                ModelRole.TOOL -> if (message.toolCallId.isNullOrBlank() || message.toolCalls.isNotEmpty()) {
                    throw ModelRequestException.Protocol("A tool result is missing its call ID.")
                }
            }
        }
        if (request.tools.map(ModelToolDefinition::name).any(String::isBlank)) {
            throw ModelRequestException.Protocol("A tool definition has no name.")
        }
        if (request.tools.map(ModelToolDefinition::name).toSet().size != request.tools.size) {
            throw ModelRequestException.Protocol("A model request contains duplicate tool names.")
        }
    }

    private fun serializeMessage(message: ModelMessage): JsonObject {
        val role = when (message.role) {
            ModelRole.SYSTEM -> "system"
            ModelRole.USER -> "user"
            ModelRole.ASSISTANT -> "assistant"
            ModelRole.TOOL -> "tool"
        }
        val content = serializeContent(message)
        return buildJsonObject {
            put("role", role)
            content?.let { put("content", it) }
            if (message.role == ModelRole.TOOL) put("tool_call_id", message.toolCallId.orEmpty())
            if (message.role == ModelRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
                put("tool_calls", buildJsonArray { message.toolCalls.forEach { add(serializeToolCall(it)) } })
            }
        }
    }

    private fun serializeContent(message: ModelMessage): JsonElement? {
        val hasImage = message.content.any { it is ModelContentPart.Image }
        if (!hasImage) {
            return JsonPrimitive(message.content.joinToString(separator = "") { (it as ModelContentPart.Text).value })
        }
        if (message.role != ModelRole.USER) {
            throw ModelRequestException.Protocol("Only user messages can contain image parts.")
        }
        return buildJsonArray {
            message.content.forEach { part ->
                when (part) {
                    is ModelContentPart.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", part.value)
                    })
                    is ModelContentPart.Image -> {
                        if (!part.mediaType.matches(imageMediaTypeRegex)) {
                            throw ModelRequestException.Protocol("An image message has an invalid media type.")
                        }
                        val encoded = Base64.getEncoder().encodeToString(part.bytes)
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:" + part.mediaType + ";base64," + encoded)
                            })
                        })
                    }
                }
            }
        }
    }

    private fun serializeTool(definition: ModelToolDefinition): JsonObject {
        val schema = OpenAiCompatibleResponseDecoder.parseObject(definition.parametersJsonSchema, "tool parameters schema")
        return buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", definition.name)
                put("description", definition.description)
                put("parameters", schema)
            })
        }
    }

    private fun serializeToolCall(call: ModelToolCall): JsonObject {
        if (call.callId.isBlank() || call.name.isBlank()) {
            throw ModelRequestException.Protocol("An assistant tool call is missing its ID or name.")
        }
        return buildJsonObject {
            put("id", call.callId)
            put("type", "function")
            put("function", buildJsonObject {
                put("name", call.name)
                put("arguments", call.argumentsJson)
            })
        }
    }


    private class VisionProbeImage {
        companion object {
            private const val WIDTH = 96
            private const val HEIGHT = 64
            private val colors = arrayOf(
                byteArrayOf(0xF2.toByte(), 0x25, 0x32),
                byteArrayOf(0x14, 0x64, 0xE8.toByte()),
                byteArrayOf(0x14, 0x8A.toByte(), 0x32),
                byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00),
            )

            fun pngBytes(): ByteArray {
                val rows = ByteArrayOutputStream()
                for (y in 0 until HEIGHT) {
                    rows.write(0)
                    for (x in 0 until WIDTH) {
                        val quadrant = (if (y < HEIGHT / 2) 0 else 2) + if (x < WIDTH / 2) 0 else 1
                        rows.write(colors[quadrant])
                    }
                }
                val compressed = ByteArrayOutputStream().also { target ->
                    DeflaterOutputStream(target).use { it.write(rows.toByteArray()) }
                }.toByteArray()
                val image = ByteArrayOutputStream()
                image.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                val header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
                    .putInt(WIDTH).putInt(HEIGHT).put(8).put(2).put(0).put(0).put(0).array()
                writeChunk(image, "IHDR", header)
                writeChunk(image, "IDAT", compressed)
                writeChunk(image, "IEND", ByteArray(0))
                return image.toByteArray()
            }

            private fun writeChunk(output: ByteArrayOutputStream, name: String, data: ByteArray) {
                output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
                val type = name.toByteArray(StandardCharsets.US_ASCII)
                output.write(type)
                output.write(data)
                val checksum = CRC32().apply { update(type); update(data) }.value.toInt()
                output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array())
            }
        }
    }

}
