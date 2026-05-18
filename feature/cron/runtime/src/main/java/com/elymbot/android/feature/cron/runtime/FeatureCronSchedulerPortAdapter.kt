package com.elymbot.android.feature.cron.runtime

import android.content.Context
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.domain.model.CronJob
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FeatureCronSchedulerPortAdapter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val scheduler: WorkManagerCronJobScheduler = WorkManagerCronJobScheduler(),
) : CronSchedulerPort {

    override fun schedule(job: CronJob) {
        scheduler.scheduleJob(appContext, job)
    }

    override fun cancel(jobId: String) {
        scheduler.cancelJob(appContext, jobId)
    }

    override fun cancelAll() {
        scheduler.cancelAll(appContext)
    }
}
