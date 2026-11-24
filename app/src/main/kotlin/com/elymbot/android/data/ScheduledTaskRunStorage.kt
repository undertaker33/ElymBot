package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "scheduled_task_run_inbox",
    indices = [Index(value = ["status", "triggeredAtEpochMillis", "runId"])],
)
data class ScheduledTaskRunInboxEntity(
    @PrimaryKey val runId: String,
    val taskId: String,
    val occurrenceId: String?,
    val scheduleRevision: Long,
    val plannedAtEpochMillis: Long?,
    val triggeredAtEpochMillis: Long,
    val title: String,
    val prompt: String,
    val timeZoneId: String,
    val origin: String,
    val installationPluginId: String,
    val installationInstanceId: String,
    val status: String,
    val sessionId: String? = null,
    val turnSequence: Long? = null,
    val failureKind: String? = null,
    val updatedAtEpochMillis: Long,
    val receiptPending: Boolean = false,
)

@Dao
interface ScheduledTaskRunInboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: ScheduledTaskRunInboxEntity): Long

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE runId = :runId")
    suspend fun get(runId: String): ScheduledTaskRunInboxEntity?

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE sessionId = :sessionId AND turnSequence = :turnSequence LIMIT 1")
    suspend fun getForTurn(sessionId: String, turnSequence: Long): ScheduledTaskRunInboxEntity?

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE status = 'QUEUED' ORDER BY triggeredAtEpochMillis, runId")
    suspend fun queued(): List<ScheduledTaskRunInboxEntity>

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE status IN ('CLAIMING', 'CLAIMED') ORDER BY triggeredAtEpochMillis, runId")
    suspend fun claimPending(): List<ScheduledTaskRunInboxEntity>

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE status = 'RUNNING' ORDER BY triggeredAtEpochMillis, runId")
    suspend fun running(): List<ScheduledTaskRunInboxEntity>

    @Query("UPDATE scheduled_task_run_inbox SET status = 'CLAIMING', updatedAtEpochMillis = :now WHERE runId = :runId AND status = 'QUEUED'")
    suspend fun markClaiming(runId: String, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = 'CLAIMED', updatedAtEpochMillis = :now WHERE runId = :runId AND status = 'QUEUED'")
    suspend fun markClaimed(runId: String, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = 'CLAIMED', updatedAtEpochMillis = :now WHERE runId = :runId AND status = 'CLAIMING'")
    suspend fun finishClaim(runId: String, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = 'QUEUED', updatedAtEpochMillis = :now WHERE runId = :runId AND status = 'CLAIMING'")
    suspend fun requeueClaiming(runId: String, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = 'RUNNING', sessionId = :sessionId, turnSequence = :turnSequence, updatedAtEpochMillis = :now WHERE runId = :runId AND status = 'CLAIMED'")
    suspend fun markRunning(runId: String, sessionId: String, turnSequence: Long, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = :status, failureKind = :failureKind, prompt = '', title = '', receiptPending = :receiptPending, updatedAtEpochMillis = :now WHERE runId = :runId AND status IN ('CLAIMED', 'RUNNING')")
    suspend fun finish(runId: String, status: String, failureKind: String?, receiptPending: Boolean, now: Long): Int

    @Query("UPDATE scheduled_task_run_inbox SET status = :status, failureKind = :failureKind, prompt = '', title = '', receiptPending = :receiptPending, updatedAtEpochMillis = :now WHERE runId = :runId AND status IN ('QUEUED', 'CLAIMING', 'CLAIMED')")
    suspend fun finishBeforeStart(runId: String, status: String, failureKind: String?, receiptPending: Boolean, now: Long): Int

    @Query("SELECT * FROM scheduled_task_run_inbox WHERE receiptPending = 1 ORDER BY updatedAtEpochMillis, runId")
    suspend fun pendingReceipts(): List<ScheduledTaskRunInboxEntity>

    @Query("UPDATE scheduled_task_run_inbox SET receiptPending = 0, updatedAtEpochMillis = :now WHERE runId = :runId AND receiptPending = 1")
    suspend fun acknowledgeReceipt(runId: String, now: Long): Int
}
