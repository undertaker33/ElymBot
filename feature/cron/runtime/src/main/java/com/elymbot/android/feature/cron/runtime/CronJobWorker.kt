package com.elymbot.android.feature.cron.runtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elymbot.android.core.common.logging.RuntimeLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for scheduled cron jobs.
 *
 * The worker translates WorkManager details into execution metadata and delegates
 * persistence, retry classification, and recurring reschedule to [CronJobRunCoordinator].
 */
@HiltWorker
class CronJobWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: CronJobRunCoordinator,
    private val runtimeLogger: RuntimeLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("jobId")
        if (jobId.isNullOrBlank()) {
            runtimeLogger.append("CronJobWorker: missing jobId input")
            return Result.failure()
        }

        runtimeLogger.append("CronJobWorker: firing for jobId=$jobId attempt=${runAttemptCount + 1}")
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
