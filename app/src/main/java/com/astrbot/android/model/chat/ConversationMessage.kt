package com.astrbot.android.model.chat

data class ConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachments: List<ConversationAttachment> = emptyList(),
)
