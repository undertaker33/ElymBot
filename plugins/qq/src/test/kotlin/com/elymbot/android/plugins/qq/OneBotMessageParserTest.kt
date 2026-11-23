package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.PlatformHistoryVisibility
import com.elymbot.core.contract.PlatformInboundProcessing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotMessageParserTest {
    private val parser = OneBotMessageParser()

    @Test
    fun parsesGroupRoutesReplyAndFiveMessageSegmentKinds() {
        val result = parser.parse(
            """{"post_type":"message","message_type":"group","self_id":1000,"user_id":2000,"group_id":3000,"message_id":4000,"time":123,"sender":{"role":"admin"},"message":[{"type":"text","data":{"text":"hello"}},{"type":"at","data":{"qq":"1000"}},{"type":"reply","data":{"id":3999}},{"type":"image","data":{"file":"image-token","url":"https://media.invalid/image","name":"photo.png","size":12}},{"type":"record","data":{"file":"audio-token"}},{"type":"video","data":{"file":"video-token"}},{"type":"file","data":{"file":"file-token","name":"notes.txt"}}]}""",
        )

        assertTrue(result is OneBotPayloadParseResult.Message)
        val event = (result as OneBotPayloadParseResult.Message).event
        assertEquals("group:3000:member:2000", event.message.origin.conversationId)
        assertEquals("group:3000", event.message.target?.conversationId)
        assertEquals("1000", event.message.origin.accountId)
        assertEquals("2000", event.message.origin.actorId)
        assertEquals("hello", event.text)
        assertTrue(event.mentionsSelf)
        assertFalse(event.mentionsAll)
        assertEquals("3999", event.message.reference?.messageId)
        assertEquals(123_000L, event.message.createdAtEpochMillis)
        assertEquals(5, event.message.segments.size)
        assertTrue(event.message.segments[0] is AgentMessageSegment.Text)
        assertTrue(event.message.segments[1] is AgentMessageSegment.Image)
        assertTrue(event.message.segments[2] is AgentMessageSegment.Audio)
        assertTrue(event.message.segments[3] is AgentMessageSegment.Video)
        assertTrue(event.message.segments[4] is AgentMessageSegment.File)
        assertEquals(4, event.mediaSources.size)
        assertEquals("photo.png", event.mediaSources.first().displayName)
        assertEquals("image/*", (event.message.segments[1] as AgentMessageSegment.Image).resource.mediaType)
    }

    @Test
    fun convertsPublicMaterialToSharedContextRouteAndKeepsTriggerRouteMemberIsolated() {
        val event = parseGroup("hello")

        val publicMaterial = event.toEnvelope(
            processing = PlatformInboundProcessing.CONTEXT_ONLY,
            historyVisibility = PlatformHistoryVisibility.GROUP_PUBLIC,
        )
        assertEquals("group:3000", publicMaterial.message.origin.conversationId)
        assertEquals(null, publicMaterial.message.target)
        assertEquals(PlatformInboundProcessing.CONTEXT_ONLY, publicMaterial.processing)

        val trigger = event.toEnvelope(
            processing = PlatformInboundProcessing.TRIGGER_AGENT,
            historyVisibility = PlatformHistoryVisibility.GROUP_MEMBER,
        )
        assertEquals("group:3000:member:2000", trigger.message.origin.conversationId)
        assertEquals("group:3000", trigger.message.target?.conversationId)
        assertEquals(PlatformInboundProcessing.TRIGGER_AGENT, trigger.processing)
    }

    @Test
    fun parsesRawCqAtAndMediaReferencesWithoutExposingMediaUrlInCoreReference() {
        val result = parser.parse(
            """{"post_type":"message","message_type":"private","self_id":"1000","user_id":"2000","message_id":"4000","message":"[CQ:at,qq=1000] see [CQ:image,file=image-token,url=https://media.invalid/image]"}""",
        )
        val event = (result as OneBotPayloadParseResult.Message).event
        val image = event.message.segments.filterIsInstance<AgentMessageSegment.Image>().single()

        assertTrue(event.mentionsSelf)
        assertEquals("see", event.text)
        assertFalse(image.resource.resourceId.contains("media.invalid"))
        assertEquals("https://media.invalid/image", event.mediaSources.single().downloadUrl)
    }

    @Test
    fun rejectsMessageEventsWithoutStableMessageIdentityAndIgnoresNonMessageEvents() {
        assertEquals(
            OneBotPayloadParseResult.Invalid(OneBotPayloadError.MISSING_MESSAGE_ID),
            parser.parse("""{"post_type":"message","message_type":"private","self_id":"1000","user_id":"2000"}"""),
        )
        assertEquals(
            OneBotPayloadParseResult.Ignored,
            parser.parse("""{"post_type":"notice","self_id":"1000"}"""),
        )
    }

    private fun parseGroup(text: String): OneBotInboundEvent {
        val payload = """{"post_type":"message","message_type":"group","self_id":"1000","user_id":"2000","group_id":"3000","message_id":"4000","message":[{"type":"text","data":{"text":"$text"}}]}"""
        return (parser.parse(payload) as OneBotPayloadParseResult.Message).event
    }
}
