package com.astrbot.android.runtime.qq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QqReplyFormatterTest {
    @Test
    fun builds_group_reply_decorations_in_expected_order() {
        val result = QqReplyFormatter.buildDecoration(
            messageType = "group",
            messageId = "30001",
            senderUserId = "10001",
            replyTextPrefix = "[Bot] ",
            quoteSenderMessageEnabled = true,
            mentionSenderEnabled = true,
        )

        assertEquals("[Bot] ", result.textPrefix)
        assertEquals("30001", result.quoteMessageId)
        assertEquals("10001", result.mentionUserId)
    }

    @Test
    fun private_reply_does_not_force_quote_or_mention() {
        val result = QqReplyFormatter.buildDecoration(
            messageType = "private",
            messageId = "30001",
            senderUserId = "10001",
            replyTextPrefix = "",
            quoteSenderMessageEnabled = true,
            mentionSenderEnabled = true,
        )

        assertNull(result.quoteMessageId)
        assertNull(result.mentionUserId)
    }
}
