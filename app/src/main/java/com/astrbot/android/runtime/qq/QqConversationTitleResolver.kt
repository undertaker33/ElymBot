package com.astrbot.android.runtime.qq

object QqConversationTitleResolver {
    fun build(
        messageType: String,
        groupId: String,
        userId: String,
        senderName: String,
    ): String {
        val displayName = senderName.trim().ifBlank { userId }
        return when (messageType) {
            "group" -> "QQG ${groupId.ifBlank { "unknown" }} $displayName"
            else -> "QQP $displayName"
        }
    }
}
