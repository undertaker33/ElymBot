package com.astrbot.android.core.db.backup

import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

data class ConversationImportPreview(
    val totalSessions: Int,
    val duplicateSessions: List<ConversationSession>,
    val newSessions: List<ConversationSession>,
)

data class ConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)

interface ConversationBackupDataPort {
    val isReady: StateFlow<Boolean>
    val sessions: StateFlow<List<ConversationSession>>
    val defaultSessionTitle: String

    fun selectedBotId(): String
    fun snapshotSessions(): List<ConversationSession>
    fun restoreSessions(restoredSessions: List<ConversationSession>)
    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview
    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult

    suspend fun importSessionsDurable(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult
}
