package com.astrbot.android.runtime.qq

object QqSessionKeyFactory {
    fun build(
        botId: String,
        messageType: String,
        groupId: String,
        userId: String,
        isolated: Boolean,
    ): String {
        return if (messageType == "group") {
            if (isolated) {
                // When group isolation is enabled, the session key must include the sender.
                // This keeps context and persona state independent per member, matching desktop behavior.
                "qq-${botId}-group-${groupId}-user-${userId}"
            } else {
                "qq-${botId}-group-${groupId}"
            }
        } else {
            "qq-${botId}-private-${userId}"
        }
    }
}
