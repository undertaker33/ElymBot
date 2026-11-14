package com.elymbot.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "personas",
    indices = [Index(value = ["sortOrder"])],
)
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val tagsValue: String,
    val prompt: String,
    val favorite: Boolean,
    val coverPath: String?,
    val atlasTile: Int,
    val coverX: Float,
    val coverY: Float,
    val avatarX: Float,
    val avatarY: Float,
    val sortOrder: Int,
)

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
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["sessionId", "sequence"],
            childColumns = ["sessionId", "turnSequence"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "turnSequence", "messageIndex"], unique = true),
        Index(value = ["sessionId", "turnSequence", "category"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val turnSequence: Long,
    val messageIndex: Int,
    val role: String,
    val text: String,
    val category: String,
    val status: String?,
    val author: String?,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val privatePath: String,
    val mediaType: String,
    val byteSize: Long,
)

@Entity(
    tableName = "checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "displayNumber"], unique = true),
        Index(value = ["sessionId", "creationOrder"], unique = true),
    ],
)
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val displayNumber: Int,
    val boundaryTurnSequence: Long,
    val creationOrder: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "history_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "fromTurnInclusive", "toTurnInclusive"])],
)
data class HistorySummaryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val fromTurnInclusive: Long,
    val toTurnInclusive: Long,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "model_services")
data class ModelServiceEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val catalogValue: String,
)

@Entity(tableName = "model_assignments")
data class ModelAssignmentEntity(
    @PrimaryKey val role: String,
    val serviceId: String,
    val modelId: String,
)

@Entity(
    tableName = "model_probe_results",
    primaryKeys = ["serviceId", "modelId", "baseUrl", "target"],
    foreignKeys = [
        ForeignKey(
            entity = ModelServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ModelProbeEntity(
    val serviceId: String,
    val modelId: String,
    val baseUrl: String,
    val target: String,
    val status: String,
    val failureKind: String?,
    val httpStatusCode: Int?,
)

@Entity(tableName = "command_names")
data class CommandNameEntity(
    @PrimaryKey val behaviorId: String,
    val name: String,
)

@Entity(
    tableName = "service_credentials",
    foreignKeys = [
        ForeignKey(
            entity = ModelServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CredentialEntity(
    @PrimaryKey val serviceId: String,
    val iv: ByteArray,
    val encryptedKey: ByteArray,
)

data class MessageWithAttachments(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)

data class HistoryMessageWithTurnStatus(
    @Embedded val message: MessageEntity,
    val turnStatus: String?,
)

data class HistoryMessageCountByTurn(
    val turnSequence: Long,
    val messageCount: Int,
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
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY sortOrder, id")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun get(id: String): PersonaEntity?

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM personas")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<PersonaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: PersonaEntity)

    @Update
    suspend fun update(item: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: String)
}

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

    @Query("SELECT c.id, c.personaId, COALESCE(c.title, '') AS title, (SELECT CASE WHEN m.text <> '' THEN m.text ELSE NULL END FROM messages m WHERE m.sessionId = c.id AND m.category = 'HISTORY' ORDER BY m.turnSequence DESC, m.messageIndex DESC LIMIT 1) AS preview, EXISTS(SELECT 1 FROM turns t WHERE t.sessionId = c.id AND t.status = 'RUNNING') AS running, c.unread FROM conversations c WHERE c.kind = 'MAIN' AND c.mainKey = 'main' LIMIT 1")
    fun observeMainSummary(): Flow<ConversationSummaryRow?>

    @Query("SELECT c.id, c.personaId, COALESCE(c.title, '') AS title, (SELECT CASE WHEN m.text <> '' THEN m.text ELSE NULL END FROM messages m WHERE m.sessionId = c.id AND m.category = 'HISTORY' ORDER BY m.turnSequence DESC, m.messageIndex DESC LIMIT 1) AS preview, EXISTS(SELECT 1 FROM turns t WHERE t.sessionId = c.id AND t.status = 'RUNNING') AS running, c.unread FROM conversations c WHERE c.kind = 'DEDICATED' AND c.personaId = :personaId AND c.title IS NOT NULL ORDER BY c.lastActivityAt DESC, c.createdAt DESC")
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

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY turnSequence, messageIndex")
    fun observeForSession(sessionId: String): Flow<List<MessageWithAttachments>>

    @Query(
        """
        SELECT turnSequence, COUNT(*) AS messageCount
        FROM messages
        WHERE sessionId = :sessionId AND category = 'HISTORY' AND role IN ('USER', 'ASSISTANT')
        GROUP BY turnSequence
        ORDER BY turnSequence
        """,
    )
    fun observeHistoryMessageCountsByTurn(sessionId: String): Flow<List<HistoryMessageCountByTurn>>

    @Transaction
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence ORDER BY messageIndex")
    suspend fun forTurn(sessionId: String, sequence: Long): List<MessageWithAttachments>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY turnSequence DESC, messageIndex DESC LIMIT 1")
    suspend fun latest(sessionId: String): MessageEntity?

    @Query("SELECT COALESCE(MAX(messageIndex), -1) + 1 FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence")
    suspend fun nextIndex(sessionId: String, sequence: Long): Int

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence < :upperBoundExclusive AND category = 'HISTORY' AND role IN ('USER', 'ASSISTANT') ORDER BY turnSequence, messageIndex")
    suspend fun history(sessionId: String, upperBoundExclusive: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence BETWEEN :fromTurn AND :toTurn AND category = 'HISTORY' AND role IN ('USER', 'ASSISTANT') ORDER BY turnSequence, messageIndex")
    suspend fun historyRange(sessionId: String, fromTurn: Long, toTurn: Long): List<MessageEntity>

    @Query(
        """
        SELECT messages.*, turns.status AS turnStatus
        FROM messages
        LEFT JOIN turns ON turns.sessionId = messages.sessionId AND turns.sequence = messages.turnSequence
        WHERE messages.sessionId = :sessionId
            AND messages.turnSequence < :upperBoundExclusive
            AND (:fromTurnInclusive IS NULL OR messages.turnSequence >= :fromTurnInclusive)
            AND (:toTurnInclusive IS NULL OR messages.turnSequence <= :toTurnInclusive)
            AND (:cursorTurn IS NULL OR messages.turnSequence < :cursorTurn
                OR (messages.turnSequence = :cursorTurn AND messages.messageIndex < :cursorIndex))
            AND messages.category = 'HISTORY'
            AND messages.role IN ('USER', 'ASSISTANT')
        ORDER BY messages.turnSequence DESC, messages.messageIndex DESC
        LIMIT :limit
        """,
    )
    suspend fun historyNewestPage(
        sessionId: String,
        upperBoundExclusive: Long,
        fromTurnInclusive: Long?,
        toTurnInclusive: Long?,
        cursorTurn: Long?,
        cursorIndex: Int?,
        limit: Int,
    ): List<HistoryMessageWithTurnStatus>

    @Query(
        """
        SELECT messages.*, turns.status AS turnStatus
        FROM messages
        LEFT JOIN turns ON turns.sessionId = messages.sessionId AND turns.sequence = messages.turnSequence
        WHERE messages.sessionId = :sessionId
            AND messages.turnSequence < :upperBoundExclusive
            AND (:fromTurnInclusive IS NULL OR messages.turnSequence >= :fromTurnInclusive)
            AND (:toTurnInclusive IS NULL OR messages.turnSequence <= :toTurnInclusive)
            AND (:cursorTurn IS NULL OR messages.turnSequence > :cursorTurn
                OR (messages.turnSequence = :cursorTurn AND messages.messageIndex > :cursorIndex))
            AND messages.category = 'HISTORY'
            AND messages.role IN ('USER', 'ASSISTANT')
        ORDER BY messages.turnSequence, messages.messageIndex
        LIMIT :limit
        """,
    )
    suspend fun historyOldestPage(
        sessionId: String,
        upperBoundExclusive: Long,
        fromTurnInclusive: Long?,
        toTurnInclusive: Long?,
        cursorTurn: Long?,
        cursorIndex: Int?,
        limit: Int,
    ): List<HistoryMessageWithTurnStatus>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence AND role = 'USER' AND category = 'HISTORY' ORDER BY messageIndex LIMIT 1")
    suspend fun userMessage(sessionId: String, sequence: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence AND role = 'ASSISTANT' AND category = 'HISTORY' ORDER BY messageIndex LIMIT 1")
    suspend fun assistantHistoryMessage(sessionId: String, sequence: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<MessageEntity>)

    @Update
    suspend fun update(item: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence AND role = 'ASSISTANT'")
    suspend fun deleteAssistantMessages(sessionId: String, sequence: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT privatePath FROM attachments")
    suspend fun referencedPaths(): List<String>

    @Query(
        """
        SELECT attachments.privatePath
        FROM attachments
        INNER JOIN messages ON messages.id = attachments.messageId
        WHERE messages.sessionId IN (:sessionIds)
        """,
    )
    suspend fun pathsForSessions(sessionIds: List<String>): List<String>

    @Query(
        """
        SELECT attachments.privatePath
        FROM attachments
        INNER JOIN messages ON messages.id = attachments.messageId
        WHERE messages.sessionId = :sessionId AND messages.turnSequence > :boundaryExclusive
        """,
    )
    suspend fun pathsAfterTurn(sessionId: String, boundaryExclusive: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: AttachmentEntity)
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId ORDER BY creationOrder")
    suspend fun getForSession(sessionId: String): List<CheckpointEntity>

    @Query("DELETE FROM checkpoints WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId ORDER BY creationOrder")
    fun observeForSession(sessionId: String): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId AND displayNumber = :number LIMIT 1")
    suspend fun getByNumber(sessionId: String, number: Int): CheckpointEntity?

    @Query("SELECT COALESCE(MAX(creationOrder), 0) + 1 FROM checkpoints WHERE sessionId = :sessionId")
    suspend fun nextCreationOrder(sessionId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CheckpointEntity)

    @Query("DELETE FROM checkpoints WHERE sessionId = :sessionId AND creationOrder > :creationOrder")
    suspend fun deleteAfter(sessionId: String, creationOrder: Long)
}

@Dao
interface HistorySummaryDao {
    @Query("SELECT * FROM history_summaries WHERE sessionId = :sessionId ORDER BY fromTurnInclusive")
    suspend fun getForSession(sessionId: String): List<HistorySummaryEntity>

    @Query("SELECT * FROM history_summaries WHERE sessionId = :sessionId AND toTurnInclusive < :upperBoundExclusive ORDER BY fromTurnInclusive")
    suspend fun getBefore(sessionId: String, upperBoundExclusive: Long): List<HistorySummaryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: HistorySummaryEntity)

    @Query("DELETE FROM history_summaries WHERE sessionId = :sessionId AND toTurnInclusive > :boundaryInclusive")
    suspend fun deleteAfter(sessionId: String, boundaryInclusive: Long)

    @Query("DELETE FROM history_summaries WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("SELECT EXISTS(SELECT 1 FROM history_summaries WHERE sessionId = :sessionId AND fromTurnInclusive <= :toTurn AND toTurnInclusive >= :fromTurn)")
    suspend fun overlaps(sessionId: String, fromTurn: Long, toTurn: Long): Boolean
}

@Dao
interface ModelConfigurationDao {
    @Query("SELECT * FROM model_services ORDER BY name, id")
    fun observeServices(): Flow<List<ModelServiceEntity>>

    @Query("SELECT * FROM model_services ORDER BY name, id")
    suspend fun getServices(): List<ModelServiceEntity>

    @Query("SELECT * FROM model_services WHERE id = :id")
    suspend fun getService(id: String): ModelServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(item: ModelServiceEntity)

    @Query("DELETE FROM model_services WHERE id = :id")
    suspend fun deleteService(id: String)

    @Query("DELETE FROM model_assignments WHERE serviceId = :serviceId")
    suspend fun deleteAssignmentsForService(serviceId: String)

    @Query("SELECT * FROM model_assignments ORDER BY role")
    fun observeAssignments(): Flow<List<ModelAssignmentEntity>>

    @Query("SELECT * FROM model_assignments WHERE role = :role LIMIT 1")
    suspend fun getAssignment(role: String): ModelAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(item: ModelAssignmentEntity)

    @Query("DELETE FROM model_assignments WHERE role = :role")
    suspend fun deleteAssignment(role: String)

    @Query("SELECT * FROM model_probe_results WHERE serviceId = :serviceId AND modelId = :modelId AND baseUrl = :baseUrl")
    suspend fun getProbes(serviceId: String, modelId: String, baseUrl: String): List<ModelProbeEntity>

    @Query("DELETE FROM model_probe_results WHERE serviceId = :serviceId")
    suspend fun deleteProbes(serviceId: String)

    @Query("DELETE FROM model_probe_results WHERE serviceId = :serviceId AND modelId = :modelId AND baseUrl = :baseUrl")
    suspend fun deleteProbesForModel(serviceId: String, modelId: String, baseUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProbe(item: ModelProbeEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM service_credentials WHERE serviceId = :serviceId)")
    suspend fun hasCredential(serviceId: String): Boolean
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM service_credentials WHERE serviceId = :serviceId")
    suspend fun get(serviceId: String): CredentialEntity?

    @Query("SELECT serviceId FROM service_credentials")
    fun observeServiceIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CredentialEntity)

    @Query("DELETE FROM service_credentials WHERE serviceId = :serviceId")
    suspend fun delete(serviceId: String)
}

@Dao
interface CommandNameDao {
    @Query("SELECT * FROM command_names ORDER BY behaviorId")
    fun observeAll(): Flow<List<CommandNameEntity>>

    @Query("SELECT * FROM command_names ORDER BY behaviorId")
    suspend fun getAll(): List<CommandNameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CommandNameEntity>)
}

@Database(
    entities = [
        PersonaEntity::class,
        ConversationEntity::class,
        TurnEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        CheckpointEntity::class,
        HistorySummaryEntity::class,
        ModelServiceEntity::class,
        ModelAssignmentEntity::class,
        ModelProbeEntity::class,
        CommandNameEntity::class,
        CredentialEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ElymBotDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun conversationDao(): ConversationDao
    abstract fun turnDao(): TurnDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun historySummaryDao(): HistorySummaryDao
    abstract fun modelConfigurationDao(): ModelConfigurationDao
    abstract fun credentialDao(): CredentialDao
    abstract fun commandNameDao(): CommandNameDao

    companion object {
        fun open(context: Context): ElymBotDatabase =
            Room.databaseBuilder(context.applicationContext, ElymBotDatabase::class.java, "elymbot.db")
                .build()
    }
}
