package com.astrbot.android.feature.cron.data

import com.astrbot.android.feature.cron.domain.model.CronJob
import com.astrbot.android.feature.cron.domain.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("UNUSED_PARAMETER")
object FeatureCronJobRepository {
    val jobs: StateFlow<List<CronJob>> = MutableStateFlow(emptyList())

    suspend fun create(job: CronJob): CronJob = job

    suspend fun update(job: CronJob): CronJob = job

    suspend fun delete(jobId: String) = Unit

    suspend fun getByJobId(jobId: String): CronJob? = null

    suspend fun listAll(): List<CronJob> = emptyList()

    suspend fun listEnabled(): List<CronJob> = emptyList()

    suspend fun updateStatus(
        jobId: String,
        status: String,
        lastRunAt: Long? = null,
        lastError: String? = null,
    ) = Unit

    suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    suspend fun listRecentExecutionRecords(jobId: String, limit: Int = 5): List<CronJobExecutionRecord> = emptyList()

    suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
}
