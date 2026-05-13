package com.astrbot.android.feature.cron.runtime

import android.content.Context
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.domain.model.CronJob

class AndroidCronSchedulerPort(
    private val contextProvider: () -> Context?,
    private val scheduler: WorkManagerCronJobScheduler = WorkManagerCronJobScheduler(),
) : CronSchedulerPort {
    override fun schedule(job: CronJob) {
        val context = contextProvider() ?: return
        scheduler.scheduleJob(context, job)
    }

    override fun cancel(jobId: String) {
        val context = contextProvider() ?: return
        scheduler.cancelJob(context, jobId)
    }

    override fun cancelAll() {
        val context = contextProvider() ?: return
        scheduler.cancelAll(context)
    }
}
