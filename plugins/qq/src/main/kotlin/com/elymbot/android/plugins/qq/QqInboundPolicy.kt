package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformHistoryVisibility
import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundProcessing
import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings

internal enum class QqInboundDropReason {
    SELF_MESSAGE,
    AT_ALL_IGNORED,
    NOT_ALLOWED,
    KEYWORD_BLOCKED,
    NO_TRIGGER,
}

internal sealed interface QqInboundDecision {
    data class Trigger(
        val envelope: PlatformInboundEnvelope,
        val event: OneBotInboundEvent,
        val rateLimitKey: String,
        val rateLimitRule: QqRateLimitRule?,
    ) : QqInboundDecision

    data class ContextOnly(
        val envelope: PlatformInboundEnvelope,
        val event: OneBotInboundEvent,
    ) : QqInboundDecision

    data class Dropped(val reason: QqInboundDropReason) : QqInboundDecision
}

/** Applies the accepted D07 rules before a normalized event reaches the host. */
internal class QqInboundPolicy {
    fun evaluate(
        inbound: OneBotInboundEvent,
        configuration: QqPlatformSettings,
    ): QqInboundDecision {
        if (configuration.ignoreSelfMessageEnabled && inbound.senderId == inbound.accountId) {
            return QqInboundDecision.Dropped(QqInboundDropReason.SELF_MESSAGE)
        }
        if (configuration.ignoreAtAllEventEnabled && inbound.mentionsAll) {
            return QqInboundDecision.Dropped(QqInboundDropReason.AT_ALL_IGNORED)
        }

        val isAdmin = inbound.senderId in configuration.adminUserIds
        if (!isAllowed(inbound, configuration, isAdmin)) {
            return QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED)
        }

        val normalizedCommand = QqCommandAliasNormalizer.normalize(inbound.text, configuration.commandAliases)
        val event = if (normalizedCommand == null || normalizedCommand == inbound.text) {
            inbound
        } else {
            inbound.withText(normalizedCommand)
        }
        if (configuration.keywordDetectionEnabled && QqKeywordDetector(configuration.keywordPatterns).matches(event.text)) {
            return QqInboundDecision.Dropped(QqInboundDropReason.KEYWORD_BLOCKED)
        }

        val wakeMatched = QqWakeWordPolicy.matches(
            text = event.text,
            wakeWords = configuration.wakeWords + configuration.triggerWords,
            adminOnlyEnabled = configuration.wakeWordsAdminOnlyEnabled,
            isAdmin = isAdmin,
        )
        val isCommand = normalizedCommand != null
        val isTriggered = when (inbound.conversationKind) {
            OneBotConversationKind.PRIVATE -> isCommand || !configuration.privateChatRequiresWakeWord || wakeMatched
            OneBotConversationKind.GROUP -> isCommand ||
                (inbound.mentionsSelf && (event.text.isNotBlank() || configuration.replyOnAtOnlyEnabled)) ||
                inbound.mentionsAll ||
                wakeMatched
        }

        if (!isTriggered) {
            return if (inbound.conversationKind == OneBotConversationKind.GROUP &&
                inbound.groupId?.let { it in configuration.publicGroupHistoryGroupIds } == true
            ) {
                QqInboundDecision.ContextOnly(
                    envelope = event.toEnvelope(
                        processing = PlatformInboundProcessing.CONTEXT_ONLY,
                        historyVisibility = PlatformHistoryVisibility.GROUP_PUBLIC,
                    ),
                    event = event,
                )
            } else {
                QqInboundDecision.Dropped(QqInboundDropReason.NO_TRIGGER)
            }
        }

        val historyVisibility = when {
            inbound.conversationKind == OneBotConversationKind.PRIVATE -> PlatformHistoryVisibility.PRIVATE
            inbound.groupId?.let { it in configuration.publicGroupHistoryGroupIds } == true ->
                PlatformHistoryVisibility.GROUP_PUBLIC
            else -> PlatformHistoryVisibility.GROUP_MEMBER
        }
        return QqInboundDecision.Trigger(
            envelope = event.toEnvelope(
                processing = PlatformInboundProcessing.TRIGGER_AGENT,
                historyVisibility = historyVisibility,
            ),
            event = event,
            rateLimitKey = buildRateLimitKey(event),
            rateLimitRule = when (inbound.conversationKind) {
                OneBotConversationKind.PRIVATE -> configuration.privateRateLimit?.toQqRateLimitRule()
                OneBotConversationKind.GROUP -> configuration.groupRateLimit?.toQqRateLimitRule()
            },
        )
    }

    private fun isAllowed(
        inbound: OneBotInboundEvent,
        configuration: QqPlatformSettings,
        isAdmin: Boolean,
    ): Boolean {
        if (!configuration.whitelistEnabled) return true
        val adminBypassEnabled = isAdmin && when (inbound.conversationKind) {
            OneBotConversationKind.PRIVATE -> configuration.adminPrivateBypassWhitelistEnabled
            OneBotConversationKind.GROUP -> configuration.adminGroupBypassWhitelistEnabled
        }
        if (adminBypassEnabled) return true

        if (inbound.conversationKind == OneBotConversationKind.PRIVATE) {
            return inbound.senderId in configuration.allowedPrivateUserIds ||
                inbound.senderId in configuration.allowedUserIds
        }
        val groupId = inbound.groupId ?: return false
        return inbound.senderId in configuration.allowedUserIds ||
            groupId in configuration.allowedGroupIds ||
            QqGroupMember(userId = inbound.senderId, groupId = groupId) in configuration.allowedGroupMembers
    }

    private fun buildRateLimitKey(inbound: OneBotInboundEvent): String = buildString {
        append(inbound.accountId)
        append(':')
        append(inbound.conversationKind.name.lowercase())
        append(':')
        inbound.groupId?.let {
            append(it)
            append(':')
        }
        append(inbound.senderId)
    }
}

private fun QqPlatformRateLimitSettings.toQqRateLimitRule() = QqRateLimitRule(
    windowMillis = windowMillis,
    maxMessages = maxMessages,
    strategy = when (strategy) {
        QqPlatformRateLimitStrategy.DROP -> QqRateLimitStrategy.DROP
        QqPlatformRateLimitStrategy.STASH -> QqRateLimitStrategy.STASH
    },
    maxStashedMessages = maxStashedMessages,
    maxStashAgeMillis = maxStashAgeMillis,
)

internal object QqCommandAliasNormalizer {
    private val builtInCommands = setOf("help", "persona", "checkpoint", "reset", "mark")
    private val slashCommand = Regex("^/([^\\s]+)(.*)$", RegexOption.IGNORE_CASE)

    /** Maps configured slash aliases to the Core command names while retaining arguments. */
    fun normalize(text: String, aliases: Map<String, String>): String? {
        val match = slashCommand.matchEntire(text.trim()) ?: return null
        val alias = match.groupValues[1].lowercase()
        val configured = aliases.entries.firstOrNull { it.key.trim().trimStart('/').equals(alias, true) }
            ?.value
            ?.trim()
            ?.trimStart('/')
        val canonical = configured ?: alias.takeIf { it in builtInCommands } ?: return null
        if (canonical.isBlank()) return null
        return "/$canonical${match.groupValues[2]}"
    }
}

internal object QqWakeWordPolicy {
    fun matches(
        text: String,
        wakeWords: List<String>,
        adminOnlyEnabled: Boolean,
        isAdmin: Boolean,
    ): Boolean {
        if (adminOnlyEnabled && !isAdmin) return false
        val normalizedText = text.lowercase()
        return wakeWords.asSequence()
            .map { it.trim().lowercase() }
            .filter(String::isNotEmpty)
            .any { wakeWord -> normalizedText.contains(wakeWord) }
    }
}

internal class QqKeywordDetector(patterns: List<String>) {
    private val regexes = patterns.mapNotNull { pattern ->
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun matches(text: String): Boolean = regexes.any { it.containsMatchIn(text) }
}
