package com.astrbot.android.runtime.cron

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.astrbot.android.runtime.RuntimeLogRepository

/**
 * WorkManager worker for scheduled cron jobs.
 *
 * The worker translates WorkManager details into execution metadata and delegates
 * persistence, retry classification, and recurring reschedule to [CronJobRunCoordinator].
 */
class CronJobWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("jobId")
        if (jobId.isNullOrBlank()) {
            RuntimeLogRepository.append("CronJobWorker: missing jobId input")
            return Result.failure()
        }

        RuntimeLogRepository.append("CronJobWorker: firing for jobId=$jobId attempt=${runAttemptCount + 1}")
        val coordinator = CronJobRunCoordinator(
            scheduler = WorkManagerCronRescheduler(applicationContext),
        )

        return when (
            coordinator.runDueJob(
                jobId = jobId,
                attempt = runAttemptCount + 1,
                trigger = "work_manager",
            )
        ) {
            CronJobRunOutcome.Succeeded -> Result.success()
            CronJobRunOutcome.Skipped -> Result.success()
            CronJobRunOutcome.Retry -> Result.retry()
            CronJobRunOutcome.Failed -> Result.failure()
        }
    }
}

data class CronJobExecutionContext(
    val jobId: String,
    val name: String,
    val description: String,
    val jobType: String,
    val note: String,
    val sessionId: String,
    val platform: String,
    val conversationId: String,
    val botId: String,
    val configProfileId: String,
    val personaId: String,
    val providerId: String,
    val origin: String,
    val runOnce: Boolean,
    val runAt: String,
)

fun interface CronJobExecutionBridge {
    suspend fun execute(context: CronJobExecutionContext): CronJobDeliverySummary

    companion object {
        @Volatile
        var instance: CronJobExecutionBridge? = null
    }
}
