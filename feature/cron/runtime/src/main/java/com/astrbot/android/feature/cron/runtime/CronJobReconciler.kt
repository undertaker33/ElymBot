package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.logging.SharedRuntimeLogStore
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CronJobReconciliationSummary(
    val scheduledCount: Int,
)

class CronJobReconciler @Inject constructor(
    private val repository: CronJobRepositoryPort,
    private val scheduler: CronSchedulerPort,
) {
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    internal constructor(
        repository: CronJobRepositoryPort,
        scheduler: CronSchedulerPort,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        repository = repository,
        scheduler = scheduler,
    ) {
        this.ioDispatcher = ioDispatcher
    }

    fun reconcileAsync(scope: CoroutineScope) {
        scope.launch(ioDispatcher) {
            reconcileNow()
        }
    }

    suspend fun reconcileNow(): CronJobReconciliationSummary = withContext(ioDispatcher) {
        val enabledJobs = repository.listEnabled()
        scheduler.cancelAll()
        enabledJobs.forEach(scheduler::schedule)
        SharedRuntimeLogStore.append(
            "CronJobReconciler: reconciled ${enabledJobs.size} enabled scheduled task(s)",
        )
        CronJobReconciliationSummary(scheduledCount = enabledJobs.size)
    }
}
