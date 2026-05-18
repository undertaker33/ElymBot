package com.elymbot.android.feature.cron.domain

import com.elymbot.android.feature.cron.domain.model.CronJob

interface CronSchedulerPort {
    fun schedule(job: CronJob)
    fun cancel(jobId: String)
    fun cancelAll()
}
