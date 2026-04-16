package com.astrbot.android.runtime.context

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.runtime.plugin.PluginProviderMessageDto
import com.astrbot.android.runtime.plugin.PluginProviderMessagePartDto
import com.astrbot.android.runtime.plugin.PluginProviderMessageRole
import java.util.Locale

/**
 * Shared converters between [ConversationMessage] and [PluginProviderMessageDto].
 * Previously duplicated in ChatViewModel and OneBotBridgeServer.
 */
object MessageConverters {

    fun List<ConversationMessage>.toPluginProviderMessages(): List<PluginProviderMessageDto> {
        return map { message ->
            val role = when (message.role.lowercase(Locale.US)) {
                "system" -> PluginProviderMessageRole.SYSTEM
                "assistant" -> PluginProviderMessageRole.ASSISTANT
                else -> PluginProviderMessageRole.USER
            }
            val parts = mutableListOf<PluginProviderMessagePartDto>()
            message.content.takeIf { it.isNotBlank() }?.let {
                parts += PluginProviderMessagePartDto.TextPart(it)
            }
            message.attachments.forEach { attachment ->
                val uri = attachment.remoteUrl.ifBlank {
                    attachment.base64Data.takeIf(String::isNotBlank)?.let { base64 ->
                        "data:${attachment.mimeType};base64,$base64"
                    } ?: "attachment://${attachment.id}"
                }
                parts += PluginProviderMessagePartDto.MediaRefPart(
                    uri = uri,
                    mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                )
            }
            if (parts.isEmpty()) {
                parts += PluginProviderMessagePartDto.TextPart("[empty]")
            }
            PluginProviderMessageDto(
                role = role,
                parts = parts,
            )
        }
    }

    fun List<PluginProviderMessageDto>.toConversationMessages(
        requestId: String,
    ): List<ConversationMessage> {
        return mapIndexed { index, message ->
            val text = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                .joinToString(separator = "\n") { it.text }
            val attachments = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.MediaRefPart>()
                .mapIndexed { attachmentIndex, part ->
                    ConversationAttachment(
                        id = "$requestId-$index-$attachmentIndex",
                        type = if (part.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = part.mimeType,
                        remoteUrl = part.uri,
                    )
                }
            val toolCallId = if (message.role == PluginProviderMessageRole.TOOL) {
                extractHostToolCallId(message.metadata)
            } else {
                null
            }
            ConversationMessage(
                id = toolCallId ?: "$requestId-$index",
                role = message.role.wireValue,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
            )
        }
    }

    private fun extractHostToolCallId(metadata: Map<String, *>?): String? {
        val host = metadata?.get("__host") as? Map<*, *> ?: return null
        return (host["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}
