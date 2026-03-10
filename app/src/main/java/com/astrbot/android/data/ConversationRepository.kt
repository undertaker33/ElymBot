package com.astrbot.android.data

import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "新对话"

    private val _sessions = MutableStateFlow(
        listOf(
            ConversationSession(
                id = DEFAULT_SESSION_ID,
                title = DEFAULT_SESSION_TITLE,
                personaId = "default",
                providerId = "",
                maxContextMessages = 12,
                messages = listOf(
                    ConversationMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "对话已就绪。配置好模型后就可以开始聊天。",
                        timestamp = System.currentTimeMillis(),
                    ),
                ),
            ),
        ),
    )

    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()

    fun session(sessionId: String = DEFAULT_SESSION_ID): ConversationSession {
        return _sessions.value.firstOrNull { it.id == sessionId } ?: createMissingSession(sessionId)
    }

    fun createSession(title: String = DEFAULT_SESSION_TITLE): ConversationSession {
        val created = ConversationSession(
            id = UUID.randomUUID().toString(),
            title = title,
            personaId = "default",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        _sessions.value = listOf(created) + _sessions.value
        RuntimeLogRepository.append("Conversation created: session=${created.id}")
        return created
    }

    fun deleteSession(sessionId: String) {
        val before = _sessions.value.size
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        if (_sessions.value.size != before) {
            RuntimeLogRepository.append("Conversation deleted: session=$sessionId")
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val cleaned = title.trim().ifBlank { DEFAULT_SESSION_TITLE }
        _sessions.value = _sessions.value.map { item ->
            if (item.id == sessionId) item.copy(title = cleaned) else item
        }
        RuntimeLogRepository.append("Conversation renamed: session=$sessionId title=$cleaned")
    }

    fun buildContextPreview(sessionId: String): String {
        val session = session(sessionId)
        return session.messages
            .takeLast(session.maxContextMessages)
            .joinToString(separator = "\n") { "${it.role}: ${it.content}" }
    }

    fun appendMessage(sessionId: String, role: String, content: String) {
        val currentSession = session(sessionId)
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) {
                item.copy(messages = item.messages + message)
            } else {
                item
            }
        }
        RuntimeLogRepository.append(
            "Conversation message appended: session=$sessionId role=$role chars=${content.length}",
        )
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        val currentSession = session(sessionId)
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) item.copy(messages = messages) else item
        }
        RuntimeLogRepository.append("Conversation replaced: session=$sessionId messages=${messages.size}")
    }

    fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
    ) {
        val currentSession = session(sessionId)
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) {
                item.copy(
                    providerId = providerId,
                    personaId = personaId,
                )
            } else {
                item
            }
        }
        RuntimeLogRepository.append(
            "Conversation binding updated: session=$sessionId provider=${providerId.ifBlank { "none" }} persona=${personaId.ifBlank { "none" }}",
        )
    }

    private fun createMissingSession(sessionId: String): ConversationSession {
        val created = ConversationSession(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            personaId = "default",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        _sessions.value = listOf(created) + _sessions.value
        RuntimeLogRepository.append("Conversation created: session=$sessionId")
        return created
    }
}
