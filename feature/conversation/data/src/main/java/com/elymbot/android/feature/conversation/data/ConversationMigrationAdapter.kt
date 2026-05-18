package com.elymbot.android.feature.conversation.data

import com.elymbot.android.model.chat.MessageSessionRef
import com.elymbot.android.model.chat.defaultSessionRefFor

@Suppress("UNUSED_PARAMETER")
fun migrateLegacySessionIdentity(
    sessionId: String,
    sessionTitle: String,
): MessageSessionRef {
    return defaultSessionRefFor(sessionId)
}

