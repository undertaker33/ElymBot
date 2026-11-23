package com.elymbot.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreContractsTest {
    @Test
    fun agentMessageCarriesIdentityReferenceAndFiveTypedMediaKinds() {
        val origin = MessageRoute(
            channelId = "qq",
            conversationId = "group-7",
            accountId = "bot-account",
            actorId = "user-9",
        )
        val target = MessageRoute("qq", "group-7", accountId = "bot-account")
        val resource = MediaResourceReference("asset-1", "application/octet-stream", "attachment.bin", 3)
        val message = AgentMessage(
            messageId = "platform-message-11",
            createdAtEpochMillis = 1_800_000_000_000,
            origin = origin,
            target = target,
            reference = MessageReference(target, "quoted-message-4"),
            segments = listOf(
                AgentMessageSegment.Text("question"),
                AgentMessageSegment.Image(resource.copy(resourceId = "image-1", mediaType = "image/jpeg")),
                AgentMessageSegment.Audio(resource.copy(resourceId = "audio-1", mediaType = "audio/ogg")),
                AgentMessageSegment.Video(resource.copy(resourceId = "video-1", mediaType = "video/mp4")),
                AgentMessageSegment.File(resource),
            ),
        )

        assertEquals("platform-message-11", message.messageId)
        assertEquals("user-9", message.origin.actorId)
        assertEquals("group-7", message.target?.conversationId)
        assertEquals("quoted-message-4", message.reference?.messageId)
        assertEquals(5, message.segments.size)
        assertTrue(message.segments[1] is AgentMessageSegment.Image)
        assertTrue(message.segments[2] is AgentMessageSegment.Audio)
        assertTrue(message.segments[3] is AgentMessageSegment.Video)
        assertTrue(message.segments[4] is AgentMessageSegment.File)
    }

    @Test
    fun replySubmissionOrdinalsDisambiguateRepeatedProviderCallIds() {
        val first = ReplySubmissionContext(
            sessionId = "main",
            executionId = "execution-1",
            attemptId = "attempt-1",
            turnSequence = 12,
            toolStepOrdinal = 0,
            toolCallOrdinal = 0,
            toolCallId = "provider-call-1",
        )
        val later = first.copy(toolStepOrdinal = 1)
        val result: ReplySubmissionResult = ReplySubmissionResult.Accepted(listOf("reply-message-1"))

        assertNotEquals(first, later)
        assertEquals(first.toolCallId, later.toolCallId)
        assertTrue(result is ReplySubmissionResult.Accepted)
    }
}
