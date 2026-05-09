package com.astrbot.android.feature.cron.domain

interface ActiveCapabilityPromptStrings {
    val activeCapabilityHiddenDuringScheduledTask: String
    val proactiveCapabilityDisabled: String
    val createFutureTaskDisplayName: String
    val createFutureTaskDescription: String
    val deleteFutureTaskDisplayName: String
    val deleteFutureTaskDescription: String
    val listFutureTasksDisplayName: String
    val listFutureTasksDescription: String
    val pauseFutureTaskDisplayName: String
    val pauseFutureTaskDescription: String
    val resumeFutureTaskDisplayName: String
    val resumeFutureTaskDescription: String
    val listFutureTaskRunsDisplayName: String
    val listFutureTaskRunsDescription: String
    val updateFutureTaskDisplayName: String
    val updateFutureTaskDescription: String
    val runFutureTaskNowDisplayName: String
    val runFutureTaskNowDescription: String
    val schemaJobIdCancelDescription: String
    val schemaJobIdDescription: String
    val schemaRunsLimitDescription: String
    val schemaUpdatedShortTitleDescription: String
    val schemaUpdatedTaskInstructionDescription: String
    val schemaTaskEnabledDescription: String
    val schemaUpdatedTaskStatusDescription: String
    val schemaUpdatedRunAtDescription: String
    val schemaUpdatedCronExpressionDescription: String
    val schemaUpdatedTimezoneDescription: String
    val schemaCreateRunOnceDescription: String
    val schemaCreateNameDescription: String
    val schemaCreateNoteDescription: String
    val schemaCreateCronExpressionDescription: String
    val schemaCreateRunAtDescription: String
    val schemaCreateSessionDescription: String
    val schemaCreateTimezoneDescription: String
    val schemaCreateEnabledDescription: String
    val schemaCreateAllowPastImmediateDescription: String
    val schemaCreatePlatformDescription: String
    val schemaCreateConversationIdDescription: String
    val schemaCreateBotIdDescription: String
    val schemaCreateConfigProfileIdDescription: String
    val schemaCreatePersonaIdDescription: String
    val schemaCreateProviderIdDescription: String
    val schemaCreateOriginDescription: String
    val defaultTaskName: String
    val missingNoteMessage: String
    val invalidScheduleMessage: String
    val pastScheduleMessage: String
    val deleteMissingJobIdMessage: String
    val updateMissingJobIdMessage: String
    val pauseMissingJobIdMessage: String
    val resumeMissingJobIdMessage: String
    val listRunsMissingJobIdMessage: String
    val runNowMissingJobIdMessage: String
    val runNowUnavailableMessage: String
    val guardCreatedReply: String
    val guardPastScheduleReply: String
    val guardReminderNotePrefix: String

    fun activeCapabilityToolError(message: String): String
    fun missingContextMessage(fields: String): String
    fun taskNotFoundMessage(jobId: String): String
    fun guardFailedReply(message: String): String
    fun fallbackCreatedInstruction(jobId: String): String
    fun fallbackFailedInstruction(code: String, replyText: String): String
}

class EmptyActiveCapabilityPromptStrings : ActiveCapabilityPromptStrings {
    override val activeCapabilityHiddenDuringScheduledTask = ""
    override val proactiveCapabilityDisabled = ""
    override val createFutureTaskDisplayName = ""
    override val createFutureTaskDescription = ""
    override val deleteFutureTaskDisplayName = ""
    override val deleteFutureTaskDescription = ""
    override val listFutureTasksDisplayName = ""
    override val listFutureTasksDescription = ""
    override val pauseFutureTaskDisplayName = ""
    override val pauseFutureTaskDescription = ""
    override val resumeFutureTaskDisplayName = ""
    override val resumeFutureTaskDescription = ""
    override val listFutureTaskRunsDisplayName = ""
    override val listFutureTaskRunsDescription = ""
    override val updateFutureTaskDisplayName = ""
    override val updateFutureTaskDescription = ""
    override val runFutureTaskNowDisplayName = ""
    override val runFutureTaskNowDescription = ""
    override val schemaJobIdCancelDescription = ""
    override val schemaJobIdDescription = ""
    override val schemaRunsLimitDescription = ""
    override val schemaUpdatedShortTitleDescription = ""
    override val schemaUpdatedTaskInstructionDescription = ""
    override val schemaTaskEnabledDescription = ""
    override val schemaUpdatedTaskStatusDescription = ""
    override val schemaUpdatedRunAtDescription = ""
    override val schemaUpdatedCronExpressionDescription = ""
    override val schemaUpdatedTimezoneDescription = ""
    override val schemaCreateRunOnceDescription = ""
    override val schemaCreateNameDescription = ""
    override val schemaCreateNoteDescription = ""
    override val schemaCreateCronExpressionDescription = ""
    override val schemaCreateRunAtDescription = ""
    override val schemaCreateSessionDescription = ""
    override val schemaCreateTimezoneDescription = ""
    override val schemaCreateEnabledDescription = ""
    override val schemaCreateAllowPastImmediateDescription = ""
    override val schemaCreatePlatformDescription = ""
    override val schemaCreateConversationIdDescription = ""
    override val schemaCreateBotIdDescription = ""
    override val schemaCreateConfigProfileIdDescription = ""
    override val schemaCreatePersonaIdDescription = ""
    override val schemaCreateProviderIdDescription = ""
    override val schemaCreateOriginDescription = ""
    override val defaultTaskName = ""
    override val missingNoteMessage = ""
    override val invalidScheduleMessage = ""
    override val pastScheduleMessage = ""
    override val deleteMissingJobIdMessage = ""
    override val updateMissingJobIdMessage = ""
    override val pauseMissingJobIdMessage = ""
    override val resumeMissingJobIdMessage = ""
    override val listRunsMissingJobIdMessage = ""
    override val runNowMissingJobIdMessage = ""
    override val runNowUnavailableMessage = ""
    override val guardCreatedReply = ""
    override val guardPastScheduleReply = ""
    override val guardReminderNotePrefix = ""

    override fun activeCapabilityToolError(message: String): String = message
    override fun missingContextMessage(fields: String): String = fields
    override fun taskNotFoundMessage(jobId: String): String = jobId
    override fun guardFailedReply(message: String): String = message
    override fun fallbackCreatedInstruction(jobId: String): String = jobId
    override fun fallbackFailedInstruction(code: String, replyText: String): String = "$code $replyText"
}
