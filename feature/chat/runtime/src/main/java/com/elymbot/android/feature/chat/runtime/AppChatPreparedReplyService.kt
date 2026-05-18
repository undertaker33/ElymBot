package com.elymbot.android.feature.chat.runtime

import com.elymbot.android.core.runtime.llm.LlmResponseSegmenter
import com.elymbot.android.feature.plugin.domain.runtime.PluginMessageEventResultAttachment
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2AfterSentDeliveredEntry
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostPreparedReply
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmPipelineResult
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class AppChatPreparedReplyService(
    private val chatDependencies: AppChatRuntimeBindings,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) {
    suspend fun prepareReply(
        result: PluginV2LlmPipelineResult,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
        ttsConfig: ConfigProfile?,
    ): PluginV2HostPreparedReply {
        val sendableResult = result.sendableResult
        val attachments = if (wantsTts && ttsProvider != null && ttsConfig != null) {
            buildVoiceReplyAttachments(
                response = sendableResult.text,
                provider = ttsProvider,
                voiceId = ttsConfig.ttsVoiceId,
                voiceStreamingEnabled = ttsConfig.voiceStreamingEnabled,
                readBracketedContent = ttsConfig.ttsReadBracketedContent,
            )
        } else {
            sendableResult.attachments.toConversationAttachments()
        }
        return PluginV2HostPreparedReply(
            text = sendableResult.text,
            attachments = attachments,
            deliveredEntries = listOf(
                PluginV2AfterSentDeliveredEntry(
                    entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                    entryType = "assistant",
                    textPreview = sendableResult.text.take(160),
                    attachmentCount = attachments.size,
                ),
            ),
        )
    }

    private fun List<PluginMessageEventResultAttachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
        }
    }

    private suspend fun buildVoiceReplyAttachments(
        response: String,
        provider: ProviderProfile,
        voiceId: String,
        voiceStreamingEnabled: Boolean,
        readBracketedContent: Boolean,
    ): List<ConversationAttachment> {
        if (!voiceStreamingEnabled) {
            return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                ?.let(::listOf) ?: emptyList()
        }
        val segments = LlmResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                ?.let(::listOf) ?: emptyList()
        }
        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(provider, segment, voiceId, readBracketedContent)
                ?: return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                    ?.let(::listOf) ?: emptyList()
            streamedAttachments += attachment
        }
        return streamedAttachments.toList()
    }

    private suspend fun synthesizeSingleVoiceReply(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment? {
        return withContext(ioDispatcher) {
            runCatching {
                chatDependencies.synthesizeSpeech(
                    provider = provider,
                    text = text,
                    voiceId = voiceId,
                    readBracketedContent = readBracketedContent,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                chatDependencies.log("Chat TTS failed: ${error.message ?: error.javaClass.simpleName}")
            }.getOrNull()
        }
    }
}



