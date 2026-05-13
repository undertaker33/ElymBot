package com.astrbot.android.feature.cron.runtime

import android.content.Context
import com.astrbot.android.feature.cron.domain.model.CronJob
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManagerCronRescheduler @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val scheduler: WorkManagerCronJobScheduler = WorkManagerCronJobScheduler(),
) : CronRescheduler {
    override fun schedule(job: CronJob) {
        scheduler.scheduleJob(applicationContext, job)
    }
}
