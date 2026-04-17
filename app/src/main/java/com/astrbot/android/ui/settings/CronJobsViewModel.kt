package com.astrbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.CronJobRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.cron.CronJobScheduler
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityCreateTaskRequest
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityRuntimeFacade
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityScheduler
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTargetContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

internal class CronJobsViewModel : ViewModel() {

    val jobs: StateFlow<List<CronJob>> = CronJobRepository.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tracks whether the create/edit dialog is showing. */
    val editingJob = mutableStateOf<CronJob?>(null)
    val showCreateDialog = mutableStateOf(false)

    fun toggleEnabled(job: CronJob) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = job.copy(enabled = !job.enabled, updatedAt = System.currentTimeMillis())
            CronJobRepository.update(updated)
            val ctx = appContextRef
            if (ctx != null) {
                if (updated.enabled) {
                    CronJobScheduler.scheduleJob(ctx, updated)
                } else {
                    CronJobScheduler.cancelJob(ctx, updated.jobId)
                }
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            CronJobRepository.delete(jobId)
            appContextRef?.let { CronJobScheduler.cancelJob(it, jobId) }
        }
    }

    fun createJob(
        draft: CronJobEditorDraft,
        selectedBot: BotProfile,
    ) {
        createJob(
            name = draft.name.trim(),
            cronExpression = draft.cronExpression.trim(),
            runAt = draft.runAt.trim(),
            note = draft.note.trim(),
            runOnce = draft.runOnce,
            targetContext = draft.toTargetContext(selectedBot),
        )
    }

    fun createJob(
        name: String,
        cronExpression: String,
        runAt: String,
        note: String,
        runOnce: Boolean,
        targetContext: ActiveCapabilityTargetContext? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = buildCronJobCreateRequest(
                name = name,
                cronExpression = cronExpression,
                runAt = runAt,
                note = note,
                runOnce = runOnce,
                targetContext = targetContext,
            )
            when (val result = createFacade().createFutureTask(request)) {
                is com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTaskCreation.Created -> Unit
                is com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTaskCreation.Failed -> {
                    RuntimeLogRepository.append(
                        "CronJobsViewModel createJob failed: ${result.error.code} ${result.error.message}",
                    )
                }
            }
        }
    }

    fun defaultTargetContext(): ActiveCapabilityTargetContext {
        return runCatching { defaultCronJobTargetContext() }
            .getOrElse {
                RuntimeLogRepository.append(
                    "CronJobsViewModel defaultTargetContext fallback: ${it.message ?: it.javaClass.simpleName}",
                )
                ActiveCapabilityTargetContext(
                    platform = RuntimePlatform.APP_CHAT.wireValue,
                    conversationId = ConversationRepository.DEFAULT_SESSION_ID,
                    botId = "",
                    configProfileId = ConfigRepository.selectedProfileId.value,
                    personaId = "",
                    providerId = "",
                    origin = "ui",
                )
            }
    }

    private fun createFacade(): ActiveCapabilityRuntimeFacade {
        return ActiveCapabilityRuntimeFacade(
            scheduler = object : ActiveCapabilityScheduler {
                override fun schedule(job: CronJob) {
                    val context = appContextRef
                    if (context != null) {
                        CronJobScheduler.scheduleJob(context, job)
                    } else {
                        RuntimeLogRepository.append(
                            "CronJobsViewModel schedule skipped: app context unavailable for job=${job.jobId}",
                        )
                    }
                }

                override fun cancel(jobId: String) {
                    val context = appContextRef
                    if (context != null) {
                        CronJobScheduler.cancelJob(context, jobId)
                    } else {
                        RuntimeLogRepository.append(
                            "CronJobsViewModel cancel skipped: app context unavailable for jobId=$jobId",
                        )
                    }
                }
            },
        )
    }

    companion object {
        /** Set by the Screen composable to give scheduling access. */
        @Volatile
        internal var appContextRef: android.content.Context? = null
    }
}

internal fun buildCronJobCreateRequest(
    name: String,
    cronExpression: String,
    runAt: String,
    note: String,
    runOnce: Boolean,
    targetContext: ActiveCapabilityTargetContext? = null,
): ActiveCapabilityCreateTaskRequest {
    return ActiveCapabilityCreateTaskRequest(
        payload = mapOf(
            "name" to name,
            "note" to note,
            "cron_expression" to cronExpression,
            "run_at" to runAt,
            "run_once" to runOnce,
            "timezone" to ZoneId.systemDefault().id,
            "enabled" to true,
            "origin" to "ui",
        ),
        metadata = null,
        toolSourceContext = null,
        targetContext = targetContext ?: defaultCronJobTargetContext(),
    )
}

private fun defaultCronJobTargetContext(): ActiveCapabilityTargetContext {
    val selectedBot = BotRepository.snapshotProfiles()
        .firstOrNull { it.id == BotRepository.selectedBotId.value }
        ?: BotRepository.snapshotProfiles().firstOrNull()
        ?: error("No bot profiles available for scheduled task creation")
    return selectedBot.toCronJobTargetContext()
}

internal fun BotProfile.toCronJobTargetContext(
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = ConversationRepository.DEFAULT_SESSION_ID,
    origin: String = "ui",
): ActiveCapabilityTargetContext {
    val requestedConfigId = configProfileId.ifBlank { ConfigRepository.selectedProfileId.value }
    val config = ConfigRepository.resolve(requestedConfigId)
    val resolvedConfigId = configProfileId.ifBlank { config.id }
    val providerId = defaultProviderId
        .ifBlank { config.defaultChatProviderId }
        .ifBlank {
            ProviderRepository.providers.value.firstOrNull { provider ->
                provider.enabled && ProviderCapability.CHAT in provider.capabilities
            }?.id.orEmpty()
        }
    return ActiveCapabilityTargetContext(
        platform = platform,
        conversationId = conversationId,
        botId = id,
        configProfileId = resolvedConfigId,
        personaId = defaultPersonaId,
        providerId = providerId,
        origin = origin,
    )
}
