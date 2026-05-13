package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.common.logging.RuntimeLogger
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
    private val runtimeLogger: RuntimeLogger,
) {
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    internal constructor(
        repository: CronJobRepositoryPort,
        scheduler: CronSchedulerPort,
        ioDispatcher: CoroutineDispatcher,
        runtimeLogger: RuntimeLogger = RuntimeLogger.noop(),
    ) : this(
        repository = repository,
        scheduler = scheduler,
        runtimeLogger = runtimeLogger,
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
        runtimeLogger.append(
            "CronJobReconciler: reconciled ${enabledJobs.size} enabled scheduled task(s)",
        )
        CronJobReconciliationSummary(scheduledCount = enabledJobs.size)
    }
}
