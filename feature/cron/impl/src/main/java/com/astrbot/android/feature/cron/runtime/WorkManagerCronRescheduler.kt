package com.astrbot.android.feature.cron.runtime

import android.content.Context
import com.astrbot.android.feature.cron.domain.model.CronJob

class WorkManagerCronRescheduler(
    private val applicationContext: Context,
) : CronRescheduler {
    override fun schedule(job: CronJob) {
        CronJobScheduler.scheduleJob(applicationContext, job)
    }
}
