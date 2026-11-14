package com.elymbot.android.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ElymBotDataRepository(
    context: Context,
    val database: ElymBotDatabase = ElymBotDatabase.open(context.applicationContext),
    val assets: PrivateAssetStore = PrivateAssetStore(context.applicationContext),
    val credentials: CredentialVault = CredentialVault(database.credentialDao()),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val initializationMutex = Mutex()
    @Volatile private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return
            database.withTransaction {
                val personas = database.personaDao()
                val availablePersonas = personas.getAll()
                val activePersona = availablePersonas.firstOrNull()
                val conversations = database.conversationDao()
                var main = conversations.getMain()
                if (main == null && activePersona != null) {
                    val sessionId = MAIN_SESSION_ID
                    val checkpointId = emptyCheckpointId(sessionId)
                    val timestamp = now()
                    main = ConversationEntity(
                        id = sessionId,
                        kind = SESSION_KIND_MAIN,
                        personaId = activePersona.id,
                        title = null,
                        mainKey = MAIN_KEY,
                        currentCheckpointId = checkpointId,
                        nextTurnSequence = 1,
                        unread = false,
                        createdAt = timestamp,
                        lastActivityAt = timestamp,
                    )
                    conversations.insert(main)
                    database.checkpointDao().insert(
                        CheckpointEntity(checkpointId, sessionId, 1, 0, 1, timestamp),
                    )
                } else if (main != null) {
                    if (activePersona != null && personas.get(main.personaId) == null) {
                        conversations.setActivePersona(activePersona.id)
                        main = main.copy(personaId = activePersona.id)
                    }
                    ensureInitialCheckpoint(main)
                }

                val dedicated = conversations.getAllDedicated()
                dedicated.forEach { session -> ensureInitialCheckpoint(session) }

                val names = database.commandNameDao()
                val currentNames = names.getAll().associateBy { it.behaviorId }
                val defaults = defaultCommandNames().filterNot { it.behaviorId in currentNames }
                if (defaults.isNotEmpty()) names.upsertAll(defaults)

                val turns = database.turnDao()
                turns.running().forEach { turn ->
                    turns.update(
                        turn.copy(
                            status = TURN_STATUS_INTERRUPTED,
                            finishedAt = now(),
                            failureKind = "STREAM_INTERRUPTED",
                            retryable = true,
                        ),
                    )
                    val messages = database.messageDao()
                    val hasAssistant = messages.forTurn(turn.sessionId, turn.sequence)
                        .any { it.message.role == MESSAGE_ROLE_ASSISTANT && it.message.category == MESSAGE_CATEGORY_HISTORY }
                    if (!hasAssistant) {
                        messages.insert(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                sessionId = turn.sessionId,
                                turnSequence = turn.sequence,
                                messageIndex = messages.nextIndex(turn.sessionId, turn.sequence),
                                role = MESSAGE_ROLE_ASSISTANT,
                                text = "",
                                category = MESSAGE_CATEGORY_HISTORY,
                                status = TURN_STATUS_INTERRUPTED,
                                author = null,
                            ),
                        )
                    }
                }

                dedicated.plusNotNull(conversations.getMain()).forEach { session ->
                    val latest = turns.latest(session.id)
                    if (latest != null && session.nextTurnSequence <= latest.sequence) {
                        conversations.update(session.copy(nextTurnSequence = latest.sequence + 1))
                    }
                }
            }
            cleanupUnreferencedAssets()
            initialized = true
        }
    }

    suspend fun cleanupUnreferencedAssets() {
        val attachmentPaths = database.attachmentDao().referencedPaths()
        val coverPaths = database.personaDao().getAll().mapNotNull { it.coverPath }
        assets.removeUnreferenced((attachmentPaths + coverPaths).toSet())
    }

    suspend fun cleanupUnreferencedAssets(candidates: Collection<String>) {
        val candidatePaths = candidates.toSet()
        if (candidatePaths.isEmpty()) return
        val attachmentPaths = database.attachmentDao().referencedPaths()
        val coverPaths = database.personaDao().getAll().mapNotNull { it.coverPath }
        (candidatePaths - (attachmentPaths + coverPaths).toSet()).forEach { path ->
            assets.delete(path)
        }
    }

    private suspend fun ensureInitialCheckpoint(session: ConversationEntity) {
        val checkpoints = database.checkpointDao().getForSession(session.id)
        if (checkpoints.isEmpty()) {
            val id = emptyCheckpointId(session.id)
            val timestamp = now()
            database.checkpointDao().insert(CheckpointEntity(id, session.id, 1, 0, 1, timestamp))
            database.conversationDao().update(session.copy(currentCheckpointId = id))
        } else if (checkpoints.none { it.id == session.currentCheckpointId }) {
            database.conversationDao().update(session.copy(currentCheckpointId = checkpoints.first().id))
        }
    }

    private fun defaultCommandNames() = listOf(
        CommandNameEntity("Help", "help"),
        CommandNameEntity("Persona", "persona"),
        CommandNameEntity("Checkpoint", "checkpoint"),
        CommandNameEntity("Reset", "reset"),
        CommandNameEntity("Mark", "mark"),
    )

    private fun emptyCheckpointId(sessionId: String) = "empty:$sessionId"

    private companion object {
        const val MAIN_SESSION_ID = "main"
        const val MAIN_KEY = "main"
        const val SESSION_KIND_MAIN = "MAIN"
        const val SESSION_KIND_DEDICATED = "DEDICATED"
        const val TURN_STATUS_INTERRUPTED = "INTERRUPTED"
        const val MESSAGE_ROLE_ASSISTANT = "ASSISTANT"
        const val MESSAGE_CATEGORY_HISTORY = "HISTORY"
    }
}

private fun List<ConversationEntity>.plusNotNull(item: ConversationEntity?): List<ConversationEntity> =
    if (item == null) this else this + item
