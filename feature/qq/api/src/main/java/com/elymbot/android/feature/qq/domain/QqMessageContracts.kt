package com.elymbot.android.feature.qq.domain

import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.MessageType

data class IncomingQqMessage(
    val selfId: String,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val messageType: MessageType,
    val attachments: List<ConversationAttachment> = emptyList(),
    val mentionsSelf: Boolean = false,
    val mentionsAll: Boolean = false,
    val rawPayload: String,
)

data class QqConversationTarget(
    val sessionId: String,
    val title: String,
    val messageType: MessageType,
)

data class QqReplyPayload(
    val conversationId: String,
    val messageType: MessageType,
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
)

data class QqSendResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorSummary: String = "",
) {
    companion object {
        fun success(receiptIds: List<String> = emptyList()): QqSendResult {
            return QqSendResult(
                success = true,
                receiptIds = receiptIds.filter(String::isNotBlank),
            )
        }

        fun failure(errorSummary: String): QqSendResult {
            return QqSendResult(
                success = false,
                errorSummary = errorSummary,
            )
        }
    }
}
