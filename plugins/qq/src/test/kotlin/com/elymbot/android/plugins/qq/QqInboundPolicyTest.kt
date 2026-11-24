package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.PlatformHistoryVisibility
import com.elymbot.core.contract.PlatformInboundProcessing
import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqInboundPolicyTest {
    private val parser = OneBotMessageParser()
    private val policy = QqInboundPolicy()

    @Test
    fun admittedUntriggeredGroupMessageBecomesSharedContextOnlyMaterial() {
        val decision = policy.evaluate(
            inbound = groupMessage("ordinary text"),
            configuration = configuration(
                whitelistEnabled = true,
                allowedGroupIds = setOf("3000"),
                publicGroupHistoryGroupIds = setOf("3000"),
            ),
        )

        assertTrue(decision is QqInboundDecision.ContextOnly)
        val envelope = (decision as QqInboundDecision.ContextOnly).envelope
        assertEquals("group:3000", envelope.message.origin.conversationId)
        assertEquals(null, envelope.message.target)
        assertEquals(PlatformInboundProcessing.CONTEXT_ONLY, envelope.processing)
        assertEquals(PlatformHistoryVisibility.GROUP_PUBLIC, envelope.historyVisibility)
    }

    @Test
    fun mentionedGroupTriggerKeepsPerMemberRouteAndOriginalGroupTarget() {
        val decision = policy.evaluate(
            inbound = groupMessage("hello", mentionSelf = true),
            configuration = configuration(publicGroupHistoryGroupIds = setOf("3000")),
        )

        assertTrue(decision is QqInboundDecision.Trigger)
        val envelope = (decision as QqInboundDecision.Trigger).envelope
        assertEquals("group:3000:member:2000", envelope.message.origin.conversationId)
        assertEquals("group:3000", envelope.message.target?.conversationId)
        assertEquals(PlatformInboundProcessing.TRIGGER_AGENT, envelope.processing)
        assertEquals(PlatformHistoryVisibility.GROUP_PUBLIC, envelope.historyVisibility)
    }

    @Test
    fun privateWakeRequirementAndCommandAliasAreAppliedBeforeHostHandoff() {
        val private = privateMessage("hello")
        assertEquals(
            QqInboundDecision.Dropped(QqInboundDropReason.NO_TRIGGER),
            policy.evaluate(private, configuration(privateChatRequiresWakeWord = true)),
        )

        val wakeDecision = policy.evaluate(
            privateMessage("assistant, please help"),
            configuration(privateChatRequiresWakeWord = true, wakeWords = listOf("assistant")),
        )
        assertTrue(wakeDecision is QqInboundDecision.Trigger)

        val commandDecision = policy.evaluate(
            privateMessage("/hi topic"),
            configuration(
                whitelistEnabled = true,
                adminUserIds = setOf("2000"),
                adminPrivateBypassWhitelistEnabled = true,
                privateChatRequiresWakeWord = true,
                commandAliases = mapOf("hi" to "help"),
            ),
        ) as QqInboundDecision.Trigger
        assertEquals("/help topic", commandDecision.event.text)
        assertEquals("/help topic", (commandDecision.envelope.message.segments.single() as AgentMessageSegment.Text).value)
    }

    @Test
    fun keywordBlockedMessageNeverEntersPublicGroupHistory() {
        val decision = policy.evaluate(
            inbound = groupMessage("do not retain this"),
            configuration = configuration(
                keywordDetectionEnabled = true,
                keywordPatterns = listOf("retain this"),
                publicGroupHistoryGroupIds = setOf("3000"),
            ),
        )
        assertEquals(QqInboundDecision.Dropped(QqInboundDropReason.KEYWORD_BLOCKED), decision)
    }

    @Test
    fun whitelistDenialCannotBeOverriddenByWakeWordForNonAdmin() {
        val decision = policy.evaluate(
            inbound = groupMessage("wake"),
            configuration(
                whitelistEnabled = true,
                wakeWords = listOf("wake"),
                publicGroupHistoryGroupIds = setOf("3000"),
            ),
        )
        assertEquals(QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED), decision)
    }

    @Test
    fun typedGroupMemberAllowEntryDoesNotBecomePrivateAllowEntry() {
        val groupDecision = policy.evaluate(
            inbound = groupMessage("wake", mentionSelf = true),
            configuration = configuration(
                whitelistEnabled = true,
                allowedGroupMembers = setOf(QqGroupMember(userId = "2000", groupId = "3000")),
            ),
        )
        val privateDecision = policy.evaluate(
            inbound = privateMessage("wake"),
            configuration = configuration(
                whitelistEnabled = true,
                allowedGroupMembers = setOf(QqGroupMember(userId = "2000", groupId = "3000")),
            ),
        )

        assertTrue(groupDecision is QqInboundDecision.Trigger)
        assertEquals(QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED), privateDecision)
    }

    @Test
    fun configuredAdminBypassAndAtOnlySettingRemainIndependentRules() {
        val adminDecision = policy.evaluate(
            inbound = groupMessage("wake"),
            configuration = configuration(
                whitelistEnabled = true,
                adminUserIds = setOf("2000"),
                adminGroupBypassWhitelistEnabled = true,
                wakeWordsAdminOnlyEnabled = true,
                wakeWords = listOf("wake"),
            ),
        )
        assertTrue(adminDecision is QqInboundDecision.Trigger)

        val atOnlyDecision = policy.evaluate(
            inbound = groupMessage("", mentionSelf = true),
            configuration = configuration(replyOnAtOnlyEnabled = true),
        )
        assertTrue(atOnlyDecision is QqInboundDecision.Trigger)
    }

    private fun configuration(
        ignoreSelfMessageEnabled: Boolean = true,
        ignoreAtAllEventEnabled: Boolean = true,
        whitelistEnabled: Boolean = false,
        allowedUserIds: Set<String> = emptySet(),
        allowedPrivateUserIds: Set<String> = emptySet(),
        allowedGroupIds: Set<String> = emptySet(),
        allowedGroupMembers: Set<QqGroupMember> = emptySet(),
        adminUserIds: Set<String> = emptySet(),
        adminGroupBypassWhitelistEnabled: Boolean = false,
        adminPrivateBypassWhitelistEnabled: Boolean = false,
        privateChatRequiresWakeWord: Boolean = false,
        replyOnAtOnlyEnabled: Boolean = false,
        wakeWordsAdminOnlyEnabled: Boolean = false,
        wakeWords: List<String> = emptyList(),
        triggerWords: List<String> = emptyList(),
        keywordDetectionEnabled: Boolean = false,
        keywordPatterns: List<String> = emptyList(),
        publicGroupHistoryGroupIds: Set<String> = emptySet(),
        commandAliases: Map<String, String> = emptyMap(),
        privateRateLimit: QqPlatformRateLimitSettings? = null,
        groupRateLimit: QqPlatformRateLimitSettings? = null,
    ) = QqPlatformSettings(
        ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
        ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
        whitelistEnabled = whitelistEnabled,
        allowedUserIds = allowedUserIds,
        allowedPrivateUserIds = allowedPrivateUserIds,
        allowedGroupIds = allowedGroupIds,
        allowedGroupMembers = allowedGroupMembers,
        adminUserIds = adminUserIds,
        adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
        adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
        privateChatRequiresWakeWord = privateChatRequiresWakeWord,
        replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
        wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
        wakeWords = wakeWords,
        triggerWords = triggerWords,
        keywordDetectionEnabled = keywordDetectionEnabled,
        keywordPatterns = keywordPatterns,
        publicGroupHistoryGroupIds = publicGroupHistoryGroupIds,
        commandAliases = commandAliases,
        privateRateLimit = privateRateLimit,
        groupRateLimit = groupRateLimit,
    )

    private fun groupMessage(
        text: String,
        mentionSelf: Boolean = false,
    ): OneBotInboundEvent {
        val segments = buildString {
            append("[{\"type\":\"text\",\"data\":{\"text\":\"$text\"}}")
            if (mentionSelf) append(", {\"type\":\"at\",\"data\":{\"qq\":\"1000\"}}")
            append(']')
        }
        return parseEvent(
            """{"post_type":"message","message_type":"group","self_id":"1000","user_id":"2000","group_id":"3000","message_id":"4000","message":$segments}""",
        )
    }

    private fun privateMessage(text: String): OneBotInboundEvent = parseEvent(
        """{"post_type":"message","message_type":"private","self_id":"1000","user_id":"2000","message_id":"5000","message":[{"type":"text","data":{"text":"$text"}}]}""",
    )

    private fun parseEvent(payload: String): OneBotInboundEvent =
        (parser.parse(payload) as OneBotPayloadParseResult.Message).event
}
