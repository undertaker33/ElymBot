package com.astrbot.android.model.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelRelocationTest {
    @Test
    fun `chat entities are available from dedicated chat package`() {
        val attachment = ConversationAttachment(
            id = "att-1",
            type = "audio",
            fileName = "voice.wav",
        )
        val message = ConversationMessage(
            id = "msg-1",
            role = "user",
            content = "hello",
            timestamp = 1L,
            attachments = listOf(attachment),
        )
        val session = ConversationSession(
            id = "session-1",
            title = "Test",
            botId = "bot",
            personaId = "persona",
            providerId = "provider",
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group:12345",
            maxContextMessages = 12,
            messages = listOf(message),
        )

        assertEquals("att-1", session.messages.single().attachments.single().id)
        assertEquals("session-1", session.id)
        assertEquals("qq", session.platformId)
        assertEquals(MessageType.GroupMessage, session.messageType)
        assertEquals("group:12345", session.originSessionId)
    }

    @Test
    fun `conversation session defaults to app other message semantics`() {
        val session = ConversationSession(
            id = "local-session",
            title = "Local",
            botId = "bot",
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )

        assertEquals("app", session.platformId)
        assertEquals(MessageType.OtherMessage, session.messageType)
        assertEquals("local-session", session.originSessionId)
    }
}
