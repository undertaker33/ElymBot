package com.elymbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationRepository: ConversationRepositoryPort,
) : ViewModel() {
    val sessions: StateFlow<List<ConversationSession>> = conversationRepository.sessions

    fun contextPreview(sessionId: String): String {
        return conversationRepository.contextPreview(sessionId)
    }

    fun session(sessionId: String = conversationRepository.defaultSessionId): ConversationSession {
        return conversationRepository.session(sessionId)
    }

    fun appendMessage(sessionId: String, role: String, content: String) {
        conversationRepository.appendMessage(sessionId, role, content)
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        conversationRepository.replaceMessages(sessionId, messages)
    }
}

