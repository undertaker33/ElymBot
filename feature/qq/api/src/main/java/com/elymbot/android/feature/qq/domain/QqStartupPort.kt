package com.elymbot.android.feature.qq.domain

import com.elymbot.android.model.chat.ConversationAttachment

interface QqLoginStateBootstrapper {
    fun ensureReady()
}

interface QqStartupPort {
    fun initialize()
    fun start()
}

interface QqScheduledMessageSender {
    fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
        botId: String = "",
    ): QqSendResult
}
