package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.model.chat.ConversationAttachment

interface ScheduledMessageDeliveryPort {
    suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult
}

data class ScheduledMessageDeliveryRequest(
    val platform: String,
    val conversationId: String,
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val botId: String,
)

data class ScheduledMessageDeliveryResult(
    val success: Boolean,
    val deliveredMessageCount: Int,
    val receiptIds: List<String> = emptyList(),
    val errorCode: String = "",
    val errorSummary: String = "",
)
