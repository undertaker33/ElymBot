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
import kotlinx.coroutines.flow.Flow

enum class ReplyDeliveryStatus {
    PENDING,
    SENDING,
    DELIVERED,
    FAILED,
    UNKNOWN,
    CANCELLED,
    STALE,
}

enum class ReplyDeliveryTargetKind {
    APP_TRANSCRIPT,
    PLATFORM,
}

@Entity(
    tableName = "reply_submissions",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["sessionId", "sequence"],
            childColumns = ["sessionId", "turnSequence"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "sessionId",
                "executionId",
                "attemptId",
                "turnSequence",
                "toolStepOrdinal",
                "toolCallOrdinal",
                "messageOrdinal",
            ],
            unique = true,
        ),
        Index(value = ["sessionId", "turnSequence"]),
    ],
)
data class ReplySubmissionEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val turnSequence: Long,
    val executionId: String,
    val attemptId: String,
    val toolStepOrdinal: Int,
    val toolCallOrdinal: Int,
    val messageOrdinal: Int,
    val toolCallId: String,
    val suggestedDelayMillis: Long,
    val historyVisibilityTargetKey: String,
    val acceptedAtEpochMillis: Long,
)

@Entity(
    tableName = "reply_deliveries",
    foreignKeys = [
        ForeignKey(
            entity = ReplySubmissionEntity::class,
            parentColumns = ["messageId"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["messageId", "targetKey"], unique = true),
        Index(value = ["status", "availableAtEpochMillis"]),
        Index(value = ["installationInstanceId", "status"]),
    ],
)
data class ReplyDeliveryEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val targetKey: String,
    val targetKind: String,
    val channelId: String?,
    val conversationId: String?,
    val accountId: String?,
    val actorId: String?,
    val pluginId: String?,
    val installationInstanceId: String?,
    val status: String,
    val historyVisibilityGate: Boolean,
    val availableAtEpochMillis: Long,
    val remoteMessageId: String?,
    val failureCode: String?,
    val updatedAtEpochMillis: Long,
)

data class ReplyDeliveryWithSubmission(
    @Embedded val delivery: ReplyDeliveryEntity,
    val sessionId: String,
    val turnSequence: Long,
    val executionId: String,
    val attemptId: String,
)

data class PendingAppTranscriptDelivery(
    @Embedded val delivery: ReplyDeliveryEntity,
    val sessionId: String,
    val turnSequence: Long,
    val messageIndex: Int,
)

@Entity(
    tableName = "retired_reply_executions",
    primaryKeys = ["sessionId", "executionId", "attemptId"],
    indices = [Index(value = ["sessionId", "turnSequence"])],
)
data class RetiredReplyExecutionEntity(
    val sessionId: String,
    val executionId: String,
    val attemptId: String,
    val turnSequence: Long,
    val retiredAtEpochMillis: Long,
)

/** Receipt-only record retained after rollback removes the canonical transcript row. */
@Entity(
    tableName = "retired_reply_receipts",
    indices = [
        Index(value = ["sessionId", "messageId", "targetKey"], unique = true),
        Index(value = ["sessionId", "turnSequence"]),
    ],
)
data class RetiredReplyReceiptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val turnSequence: Long,
    val messageId: String,
    val targetKey: String,
    val channelId: String,
    val conversationId: String,
    val accountId: String?,
    val actorId: String?,
    val pluginId: String,
    val installationInstanceId: String,
    val status: String,
    val remoteMessageId: String?,
    val retiredAtEpochMillis: Long,
)

data class ReplyExecutionIdentity(
    val sessionId: String,
    val turnSequence: Long,
    val executionId: String,
    val attemptId: String,
)

data class RetirableReplyDelivery(
    val sessionId: String,
    val turnSequence: Long,
    val messageId: String,
    val targetKey: String,
    val targetKind: String,
    val channelId: String?,
    val conversationId: String?,
    val accountId: String?,
    val actorId: String?,
    val pluginId: String?,
    val installationInstanceId: String?,
    val status: String,
    val remoteMessageId: String?,
)

@Dao
interface ReplySubmissionDao {
    @Query(
        """
        SELECT * FROM reply_submissions
        WHERE sessionId = :sessionId AND executionId = :executionId AND attemptId = :attemptId
            AND turnSequence = :turnSequence AND toolStepOrdinal = :toolStepOrdinal
            AND toolCallOrdinal = :toolCallOrdinal
        ORDER BY messageOrdinal
        """,
    )
    suspend fun forCall(
        sessionId: String,
        executionId: String,
        attemptId: String,
        turnSequence: Long,
        toolStepOrdinal: Int,
        toolCallOrdinal: Int,
    ): List<ReplySubmissionEntity>

    @Query(
        """
        SELECT sessionId, turnSequence, executionId, attemptId
        FROM reply_submissions
        WHERE sessionId = :sessionId AND turnSequence > :boundaryExclusive
        GROUP BY sessionId, turnSequence, executionId, attemptId
        """,
    )
    suspend fun executionIdentitiesAfter(
        sessionId: String,
        boundaryExclusive: Long,
    ): List<ReplyExecutionIdentity>

    @Query(
        """
        SELECT reply_submissions.sessionId AS sessionId,
            reply_submissions.turnSequence AS turnSequence,
            reply_submissions.messageId AS messageId,
            reply_deliveries.targetKey AS targetKey,
            reply_deliveries.targetKind AS targetKind,
            reply_deliveries.channelId AS channelId,
            reply_deliveries.conversationId AS conversationId,
            reply_deliveries.accountId AS accountId,
            reply_deliveries.actorId AS actorId,
            reply_deliveries.pluginId AS pluginId,
            reply_deliveries.installationInstanceId AS installationInstanceId,
            CASE WHEN reply_deliveries.status = 'SENDING' THEN 'UNKNOWN' ELSE reply_deliveries.status END AS status,
            reply_deliveries.remoteMessageId AS remoteMessageId
        FROM reply_submissions
        INNER JOIN reply_deliveries ON reply_deliveries.messageId = reply_submissions.messageId
        WHERE reply_submissions.sessionId = :sessionId AND reply_submissions.turnSequence > :boundaryExclusive
            AND reply_deliveries.targetKind = 'PLATFORM'
            AND reply_deliveries.status IN ('DELIVERED', 'UNKNOWN', 'SENDING')
        """,
    )
    suspend fun externalEffectsAfter(
        sessionId: String,
        boundaryExclusive: Long,
    ): List<RetirableReplyDelivery>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ReplySubmissionEntity)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM reply_submissions
            WHERE sessionId = :sessionId AND turnSequence BETWEEN :fromTurn AND :toTurn
                AND EXISTS (
                    SELECT 1 FROM reply_deliveries
                    WHERE reply_deliveries.messageId = reply_submissions.messageId
                        AND reply_deliveries.historyVisibilityGate = 1
                        AND reply_deliveries.status IN ('PENDING', 'SENDING', 'FAILED', 'UNKNOWN')
                )
        )
        """,
    )
    suspend fun hasIneligibleHistoryMessages(sessionId: String, fromTurn: Long, toTurn: Long): Boolean
}

@Dao
interface ReplyDeliveryDao {
    @Query(
        """
        SELECT reply_deliveries.*,
            reply_submissions.sessionId AS sessionId,
            reply_submissions.turnSequence AS turnSequence,
            reply_submissions.executionId AS executionId,
            reply_submissions.attemptId AS attemptId
        FROM reply_deliveries
        INNER JOIN reply_submissions ON reply_submissions.messageId = reply_deliveries.messageId
        INNER JOIN messages ON messages.id = reply_deliveries.messageId
        WHERE reply_submissions.sessionId = :sessionId
        ORDER BY messages.turnSequence, messages.messageIndex, reply_deliveries.targetKey
        """,
    )
    fun observeForSession(sessionId: String): Flow<List<ReplyDeliveryWithSubmission>>

    @Query("SELECT * FROM reply_deliveries WHERE messageId = :messageId ORDER BY targetKey")
    suspend fun forMessage(messageId: String): List<ReplyDeliveryEntity>

    @Query("SELECT * FROM reply_deliveries WHERE id = :id")
    suspend fun get(id: String): ReplyDeliveryEntity?

    @Query(
        """
        SELECT reply_deliveries.*
        FROM reply_deliveries
        INNER JOIN reply_submissions ON reply_submissions.messageId = reply_deliveries.messageId
        INNER JOIN messages ON messages.id = reply_deliveries.messageId
        WHERE reply_deliveries.status = 'PENDING'
        ORDER BY reply_submissions.acceptedAtEpochMillis, messages.sessionId, messages.turnSequence,
            messages.messageIndex, reply_deliveries.targetKey
        """,
    )
    fun observePending(): Flow<List<ReplyDeliveryEntity>>

    @Query(
        """
        SELECT reply_deliveries.*,
            reply_submissions.sessionId AS sessionId,
            reply_submissions.turnSequence AS turnSequence,
            messages.messageIndex AS messageIndex
        FROM reply_deliveries
        INNER JOIN reply_submissions ON reply_submissions.messageId = reply_deliveries.messageId
        INNER JOIN messages ON messages.id = reply_deliveries.messageId
        WHERE reply_deliveries.status = 'PENDING' AND reply_deliveries.targetKind = 'APP_TRANSCRIPT'
        ORDER BY messages.sessionId, messages.turnSequence, messages.messageIndex
        """,
    )
    fun observePendingAppTranscript(): Flow<List<PendingAppTranscriptDelivery>>

    @Query(
        """
        SELECT reply_deliveries.*,
            reply_submissions.sessionId AS sessionId,
            reply_submissions.turnSequence AS turnSequence,
            messages.messageIndex AS messageIndex
        FROM reply_deliveries
        INNER JOIN reply_submissions ON reply_submissions.messageId = reply_deliveries.messageId
        INNER JOIN messages ON messages.id = reply_deliveries.messageId
        WHERE reply_deliveries.status = 'PENDING' AND reply_deliveries.targetKind = 'APP_TRANSCRIPT'
        ORDER BY messages.sessionId, messages.turnSequence, messages.messageIndex
        """,
    )
    suspend fun pendingAppTranscriptSnapshot(): List<PendingAppTranscriptDelivery>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM reply_deliveries
            INNER JOIN reply_submissions ON reply_submissions.messageId = reply_deliveries.messageId
            WHERE reply_submissions.sessionId = :sessionId
                AND reply_submissions.turnSequence = :turnSequence
                AND (
                    (reply_deliveries.targetKind = 'APP_TRANSCRIPT' AND reply_deliveries.status = 'DELIVERED')
                    OR (reply_deliveries.targetKind = 'PLATFORM' AND reply_deliveries.status IN ('DELIVERED', 'UNKNOWN', 'SENDING'))
                )
        )
        """,
    )
    suspend fun hasVisibleOrPossibleEffectInTurn(sessionId: String, turnSequence: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM reply_submissions WHERE messageId = :messageId)")
    suspend fun isCanonicalReply(messageId: String): Boolean

    @Query("UPDATE reply_deliveries SET status = 'SENDING', updatedAtEpochMillis = :now WHERE id = :id AND status = 'PENDING'")
    suspend fun markSending(id: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'DELIVERED', updatedAtEpochMillis = :now WHERE id = :id AND targetKind = 'APP_TRANSCRIPT' AND status = 'PENDING' AND availableAtEpochMillis <= :now")
    suspend fun releaseDueAppTranscript(id: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = :status, remoteMessageId = :remoteMessageId, failureCode = :failureCode, updatedAtEpochMillis = :now WHERE id = :id AND status IN ('SENDING', 'UNKNOWN')")
    suspend fun resolveAttempt(
        id: String,
        status: String,
        remoteMessageId: String?,
        failureCode: String?,
        now: Long,
    ): Int

    @Query("UPDATE reply_deliveries SET status = 'PENDING', failureCode = NULL, remoteMessageId = NULL, updatedAtEpochMillis = :now WHERE id = :deliveryId AND messageId = :messageId AND status = 'FAILED'")
    suspend fun retryConfirmedFailure(messageId: String, deliveryId: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'CANCELLED', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId) AND status = 'PENDING'")
    suspend fun cancelPendingForSession(sessionId: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'UNKNOWN', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId) AND status = 'SENDING'")
    suspend fun markSendingUnknownForSession(sessionId: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'CANCELLED', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId AND executionId = :executionId AND attemptId = :attemptId) AND status = 'PENDING'")
    suspend fun cancelPendingForAttempt(sessionId: String, executionId: String, attemptId: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'UNKNOWN', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId AND executionId = :executionId AND attemptId = :attemptId) AND status = 'SENDING'")
    suspend fun markSendingUnknownForAttempt(sessionId: String, executionId: String, attemptId: String, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'CANCELLED', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId AND turnSequence = :turnSequence) AND status = 'PENDING'")
    suspend fun cancelPendingForTurn(sessionId: String, turnSequence: Long, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'UNKNOWN', updatedAtEpochMillis = :now WHERE messageId IN (SELECT messageId FROM reply_submissions WHERE sessionId = :sessionId AND turnSequence = :turnSequence) AND status = 'SENDING'")
    suspend fun markSendingUnknownForTurn(sessionId: String, turnSequence: Long, now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'UNKNOWN', updatedAtEpochMillis = :now WHERE status = 'SENDING'")
    suspend fun recoverSendingAsUnknown(now: Long): Int

    @Query("UPDATE reply_deliveries SET status = 'STALE', updatedAtEpochMillis = :now WHERE pluginId = :pluginId AND installationInstanceId = :instanceId AND status = 'PENDING'")
    suspend fun markPendingStaleForInstallation(pluginId: String, instanceId: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ReplyDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ReplyDeliveryEntity>)
}

@Dao
interface RetiredReplyDao {
    @Query("SELECT EXISTS(SELECT 1 FROM retired_reply_executions WHERE sessionId = :sessionId AND executionId = :executionId AND attemptId = :attemptId)")
    suspend fun isRetired(sessionId: String, executionId: String, attemptId: String): Boolean

    @Query("SELECT * FROM retired_reply_receipts WHERE sessionId = :sessionId ORDER BY turnSequence, messageId, targetKey")
    fun observeReceiptsForSession(sessionId: String): Flow<List<RetiredReplyReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun retireExecutions(items: List<RetiredReplyExecutionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipts(items: List<RetiredReplyReceiptEntity>)

    @Query("UPDATE retired_reply_receipts SET status = :status, remoteMessageId = :remoteMessageId WHERE sessionId = :sessionId AND messageId = :messageId AND targetKey = :targetKey AND status = 'UNKNOWN'")
    suspend fun resolveReceipt(
        sessionId: String,
        messageId: String,
        targetKey: String,
        status: String,
        remoteMessageId: String?,
    ): Int

    @Query("DELETE FROM retired_reply_executions WHERE sessionId IN (:sessionIds)")
    suspend fun deleteExecutionsForSessions(sessionIds: List<String>)

    @Query("DELETE FROM retired_reply_receipts WHERE sessionId IN (:sessionIds)")
    suspend fun deleteReceiptsForSessions(sessionIds: List<String>)
}
