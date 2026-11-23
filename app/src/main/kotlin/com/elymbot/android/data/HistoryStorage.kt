package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

data class MessageWithAttachments(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)

data class HistoryMessageWithTurnStatus(
    @Embedded val message: MessageEntity,
    val turnStatus: String?,
    val turnSuccessfulSilence: Boolean,
    val isReplySubmission: Boolean,
    val historyVisibilityDelivered: Boolean,
)

data class HistoryMessageCountByTurn(
    val turnSequence: Long,
    val messageCount: Int,
)

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND (NOT EXISTS (SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id) OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.targetKind = 'APP_TRANSCRIPT' AND rd.status = 'DELIVERED') OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED')) ORDER BY turnSequence, messageIndex")
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

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY turnSequence DESC, messageIndex DESC LIMIT 1")
    suspend fun latest(sessionId: String): MessageEntity?

    @Query("SELECT COALESCE(MAX(messageIndex), -1) + 1 FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence")
    suspend fun nextIndex(sessionId: String, sequence: Long): Int

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence < :upperBoundExclusive AND category = 'HISTORY' AND role IN ('USER', 'ASSISTANT') AND (NOT EXISTS (SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id) OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED')) ORDER BY turnSequence, messageIndex")
    suspend fun history(sessionId: String, upperBoundExclusive: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND turnSequence BETWEEN :fromTurn AND :toTurn AND category = 'HISTORY' AND role IN ('USER', 'ASSISTANT') AND (NOT EXISTS (SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id) OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED')) ORDER BY turnSequence, messageIndex")
    suspend fun historyRange(sessionId: String, fromTurn: Long, toTurn: Long): List<MessageEntity>

    @Query(
        """
        SELECT messages.*, turns.status AS turnStatus,
            turns.successfulSilence AS turnSuccessfulSilence,
            EXISTS(SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id) AS isReplySubmission,
            EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED') AS historyVisibilityDelivered
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
            AND (NOT EXISTS (SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id)
                OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id
                    AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED'))
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
        SELECT messages.*, turns.status AS turnStatus,
            turns.successfulSilence AS turnSuccessfulSilence,
            EXISTS(SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id) AS isReplySubmission,
            EXISTS(SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED') AS historyVisibilityDelivered
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
            AND (NOT EXISTS (SELECT 1 FROM reply_submissions rs WHERE rs.messageId = messages.id)
                OR EXISTS (SELECT 1 FROM reply_deliveries rd WHERE rd.messageId = messages.id
                    AND rd.historyVisibilityGate = 1 AND rd.status = 'DELIVERED'))
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

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND turnSequence = :sequence AND role = 'ASSISTANT' AND NOT EXISTS (SELECT 1 FROM reply_submissions WHERE reply_submissions.messageId = messages.id)")
    suspend fun deleteNonReplyAssistantMessages(sessionId: String, sequence: Long)
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
