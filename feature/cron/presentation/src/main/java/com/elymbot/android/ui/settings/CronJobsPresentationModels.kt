package com.elymbot.android.ui.settings

import com.elymbot.android.feature.cron.domain.model.CronJob
import com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord

internal data class CronJobUiListItemPresentation(
    val jobId: String,
    val name: String,
    val cronExpression: String,
    val conversationId: String,
    val nextRunTime: Long,
    val lastRunAt: Long,
    val description: String,
    val enabled: Boolean,
    val runOnce: Boolean,
    val status: String,
)

internal data class CronJobsUiPagePresentation(
    val currentPage: Int,
    val totalPages: Int,
    val visibleJobs: List<CronJobUiListItemPresentation>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

internal data class CronJobRunUiPresentation(
    val executionId: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long,
    val attempt: Int,
    val trigger: String,
    val summary: String,
)

internal fun buildCronJobsUiPresentation(
    jobs: List<CronJob>,
    requestedPage: Int,
    pageSize: Int = 2,
): CronJobsUiPagePresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val totalPages = maxOf(1, (jobs.size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = (currentPage - 1) * pageSize
    val visibleJobs = jobs.drop(startIndex).take(pageSize).map { job ->
        CronJobUiListItemPresentation(
            jobId = job.jobId,
            name = job.name.ifBlank { job.jobId },
            cronExpression = job.cronExpression.ifBlank { "-" },
            conversationId = job.conversationId,
            nextRunTime = job.nextRunTime,
            lastRunAt = job.lastRunAt,
            description = job.description,
            enabled = job.enabled,
            runOnce = job.runOnce,
            status = job.status,
        )
    }
    return CronJobsUiPagePresentation(
        currentPage = currentPage,
        totalPages = totalPages,
        visibleJobs = visibleJobs,
        canGoPrevious = currentPage > 1,
        canGoNext = currentPage < totalPages,
    )
}

internal fun buildCronJobRunUiPresentations(
    records: List<CronJobExecutionRecord>,
): List<CronJobRunUiPresentation> {
    return records.map { record ->
        CronJobRunUiPresentation(
            executionId = record.executionId,
            status = record.status.ifBlank { "-" },
            startedAt = record.startedAt,
            completedAt = record.completedAt,
            attempt = record.attempt,
            trigger = record.trigger,
            summary = record.deliverySummary
                .ifBlank { record.errorMessage }
                .ifBlank { record.errorCode },
        )
    }
}
