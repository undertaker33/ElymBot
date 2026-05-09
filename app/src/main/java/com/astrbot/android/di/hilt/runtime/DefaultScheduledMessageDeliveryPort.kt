package com.astrbot.android.di.hilt.runtime

import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.astrbot.android.feature.cron.runtime.ScheduledMessageDeliveryPort
import com.astrbot.android.feature.cron.runtime.ScheduledMessageDeliveryRequest
import com.astrbot.android.feature.cron.runtime.ScheduledMessageDeliveryResult
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
import javax.inject.Inject

internal class DefaultScheduledMessageDeliveryPort @Inject constructor(
    private val conversationPort: ConversationRepositoryPort,
    private val qqScheduledMessageSender: QqScheduledMessageSender,
) : ScheduledMessageDeliveryPort {
    override suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        return when (normalizePlatform(request.platform)) {
            RuntimePlatform.QQ_ONEBOT -> deliverToQq(request)
            RuntimePlatform.APP_CHAT -> deliverToAppChat(request)
            null -> ScheduledMessageDeliveryResult(
                success = false,
                deliveredMessageCount = 0,
                errorCode = "unsupported_platform",
                errorSummary = "Unsupported scheduled message platform: ${request.platform}",
            )
        }
    }

    private fun deliverToAppChat(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        val sessionId = request.conversationId.ifBlank { conversationPort.defaultSessionId }
        val messageId = conversationPort.appendMessage(
            sessionId = sessionId,
            role = "assistant",
            content = request.text,
            attachments = request.attachments,
        )
        return ScheduledMessageDeliveryResult(
            success = true,
            deliveredMessageCount = 1,
            receiptIds = listOf(messageId),
        )
    }

    private fun deliverToQq(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        val result = qqScheduledMessageSender.sendScheduledMessage(
            conversationId = request.conversationId,
            text = request.text,
            attachments = request.attachments,
            botId = request.botId,
        )
        return ScheduledMessageDeliveryResult(
            success = result.success,
            deliveredMessageCount = if (result.success) 1 else 0,
            receiptIds = result.receiptIds,
            errorCode = if (result.success) "" else "qq_delivery_failed",
            errorSummary = result.errorSummary,
        ).also {
            if (result.success) {
                deliverToAppChat(request)
            }
        }
    }

    private fun normalizePlatform(platform: String): RuntimePlatform? {
        return when (platform.trim().lowercase()) {
            "app",
            RuntimePlatform.APP_CHAT.wireValue,
            -> RuntimePlatform.APP_CHAT
            "qq",
            "onebot",
            RuntimePlatform.QQ_ONEBOT.wireValue,
            -> RuntimePlatform.QQ_ONEBOT
            else -> null
        }
    }
}
