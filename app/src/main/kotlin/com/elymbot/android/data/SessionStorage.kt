package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["mainKey"], unique = true),
        Index(value = ["personaId", "kind", "lastActivityAt"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val personaId: String,
    val title: String?,
    val mainKey: String?,
    val currentCheckpointId: String,
    val nextTurnSequence: Long,
    val unread: Boolean,
    val createdAt: Long,
    val lastActivityAt: Long,
)

@Entity(
    tableName = "turns",
    primaryKeys = ["sessionId", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "status", "sequence"])],
)
data class TurnEntity(
    val sessionId: String,
    val sequence: Long,
    val executionId: String,
    val attemptId: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val personaId: String,
    val personaName: String,
    val personaPrompt: String,
    val primaryServiceId: String?,
    val primaryModelId: String?,
    val visionServiceId: String?,
    val visionModelId: String?,
    val failureKind: String?,
    val retryable: Boolean,
    val successfulSilence: Boolean = false,
)

data class ConversationSummaryRow(
    val id: String,
    val personaId: String,
    val title: String,
    val preview: String?,
    val running: Boolean,
    val unread: Boolean,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE mainKey = 'main' LIMIT 1")
    suspend fun getMain(): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations WHERE kind = 'MAIN'")
    suspend fun countMain(): Int

    @Query("SELECT * FROM conversations WHERE mainKey = 'main' LIMIT 1")
    fun observeMain(): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT currentCheckpointId FROM conversations WHERE id = :id")
    fun observeCurrentCheckpointId(id: String): Flow<String?>

    @Query("SELECT * FROM conversations WHERE personaId = :personaId AND kind = 'DEDICATED' ORDER BY lastActivityAt DESC, createdAt DESC")
    suspend fun getDedicated(personaId: String): List<ConversationEntity>

    @Query("SELECT id FROM conversations WHERE kind = 'DEDICATED' AND personaId = :personaId AND title IS NULL AND NOT EXISTS (SELECT 1 FROM turns WHERE turns.sessionId = conversations.id)")
    suspend fun emptyDraftIds(personaId: String): List<String>

    @Query("SELECT * FROM conversations WHERE kind = 'DEDICATED'")
    suspend fun getAllDedicated(): List<ConversationEntity>

    @Query("SELECT c.id, c.personaId, COALESCE(c.title, '') AS title, (SELECT CASE WHEN m.text <> '' THEN m.text ELSE NULL END FROM messages m WHERE m.sessionId = c.id AND m.category = 'HISTORY' AND (NOT EXISTS(SELECT 1 FROM reply_submissions rs WHERE rs.messageId = m.id) OR EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = m.id AND rd.targetKind = 'APP_TRANSCRIPT' AND rd.status = 'DELIVERED') OR EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = m.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED')) ORDER BY m.turnSequence DESC, m.messageIndex DESC LIMIT 1) AS preview, EXISTS(SELECT 1 FROM turns t WHERE t.sessionId = c.id AND t.status = 'RUNNING') AS running, c.unread FROM conversations c WHERE c.kind = 'MAIN' AND c.mainKey = 'main' LIMIT 1")
    fun observeMainSummary(): Flow<ConversationSummaryRow?>

    @Query("SELECT c.id, c.personaId, COALESCE(c.title, '') AS title, (SELECT CASE WHEN m.text <> '' THEN m.text ELSE NULL END FROM messages m WHERE m.sessionId = c.id AND m.category = 'HISTORY' AND (NOT EXISTS(SELECT 1 FROM reply_submissions rs WHERE rs.messageId = m.id) OR EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = m.id AND rd.targetKind = 'APP_TRANSCRIPT' AND rd.status = 'DELIVERED') OR EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = m.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED')) ORDER BY m.turnSequence DESC, m.messageIndex DESC LIMIT 1) AS preview, EXISTS(SELECT 1 FROM turns t WHERE t.sessionId = c.id AND t.status = 'RUNNING') AS running, c.unread FROM conversations c WHERE c.kind = 'DEDICATED' AND c.personaId = :personaId AND c.title IS NOT NULL ORDER BY c.lastActivityAt DESC, c.createdAt DESC")
    fun observeDedicatedSummaries(personaId: String): Flow<List<ConversationSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ConversationEntity)

    @Update
    suspend fun update(item: ConversationEntity)

    @Query("UPDATE conversations SET personaId = :personaId WHERE mainKey = 'main'")
    suspend fun setActivePersona(personaId: String)

    @Query("UPDATE conversations SET unread = :unread WHERE id = :id")
    suspend fun setUnread(id: String, unread: Boolean)

    @Query("UPDATE conversations SET title = :title, lastActivityAt = :lastActivityAt WHERE id = :id AND kind = 'DEDICATED'")
    suspend fun renameDedicated(id: String, title: String, lastActivityAt: Long): Int

    @Query("UPDATE conversations SET unread = :unread, lastActivityAt = :lastActivityAt WHERE id = :id")
    suspend fun setCompletionState(id: String, unread: Boolean, lastActivityAt: Long)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT COUNT(*) FROM conversations WHERE personaId = :personaId AND kind = 'DEDICATED'")
    suspend fun dedicatedCount(personaId: String): Int
}

@Dao
interface TurnDao {
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId AND sequence = :sequence")
    suspend fun get(sessionId: String, sequence: Long): TurnEntity?

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY sequence DESC LIMIT 1")
    suspend fun latest(sessionId: String): TurnEntity?

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId AND sequence > :boundaryExclusive ORDER BY sequence")
    suspend fun after(sessionId: String, boundaryExclusive: Long): List<TurnEntity>

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY sequence")
    fun observeForSession(sessionId: String): Flow<List<TurnEntity>>

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId AND sequence < :upperBoundExclusive ORDER BY sequence")
    suspend fun before(sessionId: String, upperBoundExclusive: Long): List<TurnEntity>

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId AND sequence BETWEEN :fromSequence AND :toSequence ORDER BY sequence")
    suspend fun between(sessionId: String, fromSequence: Long, toSequence: Long): List<TurnEntity>

    @Query("SELECT * FROM turns WHERE status = 'RUNNING'")
    suspend fun running(): List<TurnEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TurnEntity)

    @Update
    suspend fun update(item: TurnEntity)

    @Query("DELETE FROM turns WHERE sessionId = :sessionId AND sequence > :boundaryExclusive")
    suspend fun deleteAfter(sessionId: String, boundaryExclusive: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM turns WHERE sessionId = :sessionId AND status = 'RUNNING')")
    suspend fun isRunning(sessionId: String): Boolean
}
