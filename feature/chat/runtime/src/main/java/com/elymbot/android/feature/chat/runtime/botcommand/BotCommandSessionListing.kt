package com.elymbot.android.feature.chat.runtime.botcommand

import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BotCommandSessionListing {
    private const val PAGE_SIZE = 10
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun render(
        sessions: List<ConversationSession>,
        personas: List<PersonaProfile>,
        currentSessionId: String,
        page: Int,
        languageTag: String,
    ): String {
        val language = BotCommandResources.resolveLanguage(languageTag)
        val normalizedPage = page.coerceAtLeast(1)
        val totalPages = (sessions.size.coerceAtLeast(1) + PAGE_SIZE - 1) / PAGE_SIZE
        val pageIndex = normalizedPage.coerceAtMost(totalPages)
        val startIndex = (pageIndex - 1) * PAGE_SIZE
        val pageItems = sessions.drop(startIndex).take(PAGE_SIZE)
        val currentSession = sessions.firstOrNull { it.id == currentSessionId }

        return buildString {
            appendLine(
                when (language) {
                    BotCommandResources.resolveLanguage("zh") -> "\u4f1a\u8bdd\u5217\u8868\uff1a"
                    else -> "Conversation list:"
                },
            )
            pageItems.forEachIndexed { index, session ->
                appendLine("${startIndex + index + 1}. ${session.title} (${session.id})")
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "  \u4eba\u683c\uff1a${personaName(session.personaId, personas, languageTag)}"
                        else -> "  Persona: ${personaName(session.personaId, personas, languageTag)}"
                    },
                )
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "  \u4e0a\u6b21\u804a\u5929\uff1a${lastChatTime(session, languageTag)}"
                        else -> "  Last chat: ${lastChatTime(session, languageTag)}"
                    },
                )
            }
            currentSession?.let { session ->
                appendLine()
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "\u5f53\u524d\u5bf9\u8bdd\uff1a${session.title} (${session.id})"
                        else -> "Current conversation: ${session.title} (${session.id})"
                    },
                )
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "\u4f1a\u8bdd\u7c7b\u578b\uff1a${sessionTypeLabel(session, languageTag)}"
                        else -> "Session type: ${sessionTypeLabel(session, languageTag)}"
                    },
                )
            }
            if (totalPages > 1) {
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "*\u8f93\u5165 /ls $pageIndex \u8df3\u8f6c\u81f3\u7b2c $pageIndex \u9875"
                        else -> "*Enter /ls $pageIndex to jump to page $pageIndex"
                    },
                )
            }
        }.trim()
    }

    private fun personaName(
        personaId: String,
        personas: List<PersonaProfile>,
        languageTag: String,
    ): String {
        return personas.firstOrNull { it.id == personaId }?.name ?: when (BotCommandResources.resolveLanguage(languageTag)) {
            BotCommandResources.resolveLanguage("zh") -> "\u672a\u8bbe\u7f6e"
            else -> "Not set"
        }
    }

    private fun lastChatTime(
        session: ConversationSession,
        languageTag: String,
    ): String {
        val lastTimestamp = session.messages.maxOfOrNull { it.timestamp } ?: return when (BotCommandResources.resolveLanguage(languageTag)) {
            BotCommandResources.resolveLanguage("zh") -> "\u65e0\u6d88\u606f"
            else -> "No messages"
        }
        return Instant.ofEpochMilli(lastTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }
}

internal fun sessionTypeLabel(
    session: ConversationSession,
    languageTag: String,
): String {
    val isChinese = !languageTag.startsWith("en", ignoreCase = true)
    return when (session.messageType) {
        MessageType.FriendMessage -> if (isChinese) "QQ\u79c1\u804a" else "QQ private chat"
        MessageType.GroupMessage -> {
            if (session.originSessionId.contains(":user:")) {
                if (isChinese) "\u7fa4\u804a\u9694\u79bb" else "Isolated group chat"
            } else {
                if (isChinese) "QQ\u7fa4\u804a" else "QQ group chat"
            }
        }

        MessageType.OtherMessage -> if (isChinese) "\u5e94\u7528\u5185\u804a\u5929" else "In-app chat"
    }
}

internal fun umoSessionToken(session: ConversationSession): String {
    return when (session.messageType) {
        MessageType.FriendMessage -> session.originSessionId.substringAfter("friend:", session.originSessionId)
        MessageType.GroupMessage -> {
            val groupId = session.originSessionId.substringAfter("group:", session.originSessionId).substringBefore(":")
            val isolatedUserId = session.originSessionId.substringAfter("user:", "")
            if (isolatedUserId.isBlank()) groupId else "${isolatedUserId}_$groupId"
        }

        MessageType.OtherMessage -> session.originSessionId
    }
}

