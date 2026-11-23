package com.elymbot.android.session

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal enum class ScheduledTaskScheduleMode {
    KEEP,
    REPLACE,
    APPEND,
}

internal fun interface ScheduledTaskWakeScheduler {
    fun schedule(nextWakeAt: Instant?, mode: ScheduledTaskScheduleMode)
}

internal class ScheduledTaskSystemScheduler(context: Context) : ScheduledTaskWakeScheduler {
    private val appContext = context.applicationContext

    override fun schedule(nextWakeAt: Instant?, mode: ScheduledTaskScheduleMode) {
        val workManager = WorkManager.getInstance(appContext)
        if (nextWakeAt == null) {
            if (mode == ScheduledTaskScheduleMode.REPLACE) workManager.cancelUniqueWork(WAKE_WORK_NAME)
            return
        }
        val delayMillis = (nextWakeAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWakeWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            WAKE_WORK_NAME,
            when (mode) {
                ScheduledTaskScheduleMode.KEEP -> ExistingWorkPolicy.KEEP
                ScheduledTaskScheduleMode.REPLACE -> ExistingWorkPolicy.REPLACE
                ScheduledTaskScheduleMode.APPEND -> APPEND_OR_REPLACE
            },
            request,
        )
    }

    private companion object {
        const val WAKE_WORK_NAME = "scheduled-task-next-due"
    }
}

class ScheduledTaskWakeWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? com.elymbot.android.ElymBotApplication
                ?: return Result.failure()
            if (app.processScheduledTaskWake()) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
