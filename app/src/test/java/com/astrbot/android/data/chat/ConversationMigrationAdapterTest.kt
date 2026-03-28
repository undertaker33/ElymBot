package com.astrbot.android.data.chat

import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMigrationAdapterTest {
    @Test
    fun `message type keeps stable desktop-aligned wire values`() {
        assertEquals("friend", MessageType.FriendMessage.wireValue)
        assertEquals("group", MessageType.GroupMessage.wireValue)
        assertEquals("other", MessageType.OtherMessage.wireValue)
        assertEquals(MessageType.FriendMessage, MessageType.fromWireValue("friend"))
        assertEquals(MessageType.GroupMessage, MessageType.fromWireValue("group"))
        assertEquals(MessageType.OtherMessage, MessageType.fromWireValue("other"))
        assertNull(MessageType.fromWireValue("private"))
    }

    @Test
    fun `message session ref carries platform type and origin id`() {
        val ref = MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group:12345",
        )

        assertEquals("qq", ref.platformId)
        assertEquals(MessageType.GroupMessage, ref.messageType)
        assertEquals("group:12345", ref.originSessionId)
        assertEquals("qq:group:group:12345", ref.unifiedOrigin)
    }

    @Test
    fun `legacy qq private session id migrates into explicit session fields`() {
        val migrated = migrateLegacySessionIdentity(
            sessionId = "qq-qq-main-private-10001",
            sessionTitle = "QQP 小明",
        )

        assertEquals("qq", migrated.platformId)
        assertEquals(MessageType.FriendMessage, migrated.messageType)
        assertEquals("friend:10001", migrated.originSessionId)
    }

    @Test
    fun `legacy local session id falls back to app other message semantics`() {
        val migrated = migrateLegacySessionIdentity(
            sessionId = "local-session",
            sessionTitle = "新对话",
        )

        assertEquals("app", migrated.platformId)
        assertEquals(MessageType.OtherMessage, migrated.messageType)
        assertEquals("local-session", migrated.originSessionId)
    }
}
