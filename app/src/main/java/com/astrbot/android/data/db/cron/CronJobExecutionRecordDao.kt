package com.astrbot.android.data.db.cron

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.astrbot.android.data.db.CronJobExecutionRecordEntity

@Dao
interface CronJobExecutionRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CronJobExecutionRecordEntity)

    @Query(
        """
        SELECT * FROM cron_job_execution_records
        WHERE jobId = :jobId
        ORDER BY startedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listRecentForJob(jobId: String, limit: Int): List<CronJobExecutionRecordEntity>

    @Query(
        """
        SELECT * FROM cron_job_execution_records
        WHERE jobId = :jobId
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun latestForJob(jobId: String): CronJobExecutionRecordEntity?

    @Query("DELETE FROM cron_job_execution_records WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: String)

    @Query("SELECT COUNT(*) FROM cron_job_execution_records")
    suspend fun count(): Int
}
