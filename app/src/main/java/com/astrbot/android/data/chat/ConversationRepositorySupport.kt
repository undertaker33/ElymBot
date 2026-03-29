package com.astrbot.android.data.chat

import com.astrbot.android.data.db.ConversationDao
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import java.io.File

internal fun sortConversationSessions(sessions: List<ConversationSession>): List<ConversationSession> {
    return sessions.sortedWith(
        compareByDescending<ConversationSession> { it.pinned }
            .thenByDescending { it.messages.maxOfOrNull { message -> message.timestamp } ?: 0L },
    )
}

internal fun loadLegacyConversationSessions(
    file: File,
    defaultTitle: String,
    onFailure: (Throwable) -> Unit,
): List<ConversationSession> {
    return runCatching {
        val array = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until array.length()) {
                add(
                    array.optJSONObject(index)?.toConversationSession(
                        defaultTitle = defaultTitle,
                    ) ?: continue,
                )
            }
        }
    }.onFailure(onFailure).getOrDefault(emptyList())
}

internal fun mergeImportedConversationSessions(
    defaultSessionId: String,
    existingSessions: List<ConversationSession>,
    legacySessions: List<ConversationSession>,
    defaultSessionsProvider: () -> List<ConversationSession>,
): List<ConversationSession> {
    val merged = LinkedHashMap<String, ConversationSession>()
    mergeDefaultConversationSession(defaultSessionId, existingSessions, defaultSessionsProvider).forEach { session ->
        merged[session.id] = session
    }
    legacySessions.forEach { session ->
        val current = merged[session.id]
        merged[session.id] = when {
            current == null -> session
            session.messages.size > current.messages.size -> session
            session.messages.size == current.messages.size &&
                (session.messages.maxOfOrNull { it.timestamp } ?: 0L) >
                (current.messages.maxOfOrNull { it.timestamp } ?: 0L) -> session
            else -> current
        }
    }
    return mergeDefaultConversationSession(
        defaultSessionId = defaultSessionId,
        loadedSessions = merged.values.filterNot { it.id == defaultSessionId }.let(::sortConversationSessions),
        defaultSessionsProvider = defaultSessionsProvider,
    )
}

internal fun mergeDefaultConversationSession(
    defaultSessionId: String,
    loadedSessions: List<ConversationSession>,
    defaultSessionsProvider: () -> List<ConversationSession>,
): List<ConversationSession> {
    val withoutDefault = loadedSessions.filterNot { it.id == defaultSessionId }
    return defaultSessionsProvider() + withoutDefault
}

internal object ConversationDaoPlaceholder {
    val instance = object : ConversationDao {
        override fun observeConversations() = flowOf(emptyList<ConversationEntity>())
        override suspend fun listConversations(): List<ConversationEntity> = emptyList()
        override suspend fun upsert(entity: ConversationEntity) = Unit
        override suspend fun upsertAll(entities: List<ConversationEntity>) = Unit
        override suspend fun deleteById(sessionId: String) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun deleteMissing(ids: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}
