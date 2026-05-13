package com.astrbot.android.feature.conversation.data

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

object FeatureConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "\u65B0\u5BF9\u8BDD"

    @Volatile
    private var delegate: FeatureConversationRepositoryStore? = null

    internal fun installDelegate(store: FeatureConversationRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureConversationRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureConversationRepository test facade was accessed before FeatureConversationRepositoryStore was installed."
        }
    }

    val sessions: StateFlow<List<ConversationSession>>
        get() = repository().sessions

    val isReady: StateFlow<Boolean>
        get() = repository().isReady

    fun setSelectedBotIdProvider(provider: () -> String) = repository().setSelectedBotIdProvider(provider)

    fun session(sessionId: String = DEFAULT_SESSION_ID): ConversationSession = repository().session(sessionId)

    fun createSession(
        title: String = DEFAULT_SESSION_TITLE,
        botId: String = repository().currentSelectedBotId(),
    ): ConversationSession = repository().createSession(title, botId)

    fun deleteSession(sessionId: String) = repository().deleteSession(sessionId)

    fun deleteSessionsForBot(botId: String) = repository().deleteSessionsForBot(botId)

    fun renameSession(sessionId: String, title: String) = repository().renameSession(sessionId, title)

    fun syncSystemSessionTitle(sessionId: String, title: String) = repository().syncSystemSessionTitle(sessionId, title)

    fun toggleSessionPinned(sessionId: String) = repository().toggleSessionPinned(sessionId)

    fun buildContextPreview(sessionId: String): String = repository().buildContextPreview(sessionId)

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String = repository().appendMessage(sessionId, role, content, attachments)

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    ) = repository().updateMessage(sessionId, messageId, content, attachments)

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) =
        repository().replaceMessages(sessionId, messages)

    fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    ) = repository().updateSessionBindings(sessionId, providerId, personaId, botId)

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    ) = repository().updateSessionServiceFlags(sessionId, sessionSttEnabled, sessionTtsEnabled)

    fun syncPersistenceForBot(botId: String, persistConversationLocally: Boolean) =
        repository().syncPersistenceForBot(botId, persistConversationLocally)

    fun snapshotSessions(): List<ConversationSession> = repository().snapshotSessions()

    fun restoreSessions(restoredSessions: List<ConversationSession>) = repository().restoreSessions(restoredSessions)

    suspend fun restoreSessionsDurable(restoredSessions: List<ConversationSession>) =
        repository().restoreSessionsDurable(restoredSessions)

    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview =
        repository().previewImportedSessions(importedSessions)

    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult = repository().importSessions(importedSessions, overwriteDuplicates)

    suspend fun importSessionsDurable(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult = repository().importSessionsDurable(importedSessions, overwriteDuplicates)
}
