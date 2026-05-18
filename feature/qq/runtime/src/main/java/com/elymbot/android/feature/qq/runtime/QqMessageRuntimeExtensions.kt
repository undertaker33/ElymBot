package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.qq.domain.IncomingQqMessage
import com.elymbot.android.model.chat.MessageType

internal val IncomingQqMessage.groupIdOrBlank: String
    get() = if (messageType == MessageType.GroupMessage) conversationId else ""
