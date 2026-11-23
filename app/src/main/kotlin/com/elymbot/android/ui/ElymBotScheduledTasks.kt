package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R
import com.elymbot.core.contract.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.flow.flowOf

private enum class TaskFilter(val label: Int) {
    All(R.string.elym_all), Enabled(R.string.elym_task_enabled), Paused(R.string.elym_task_paused),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduledTasksPage(
    model: ElymBotViewModel,
    sessionId: String,
    owner: String,
    current: Boolean,
    available: Boolean,
    back: () -> Unit,
) {
    val service = model.service
    var refresh by remember(sessionId) { mutableIntStateOf(0) }
    var selectedId by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var filter by rememberSaveable(sessionId) { mutableStateOf(TaskFilter.All) }
    var creating by rememberSaveable(sessionId) { mutableStateOf(false) }
    var editing by remember(sessionId) { mutableStateOf<ScheduledTask?>(null) }
    var deleting by remember(sessionId) { mutableStateOf<ScheduledTask?>(null) }
    var issue by remember(sessionId) { mutableStateOf<ScheduledTaskRejection?>(null) }
    var busy by remember(sessionId) { mutableStateOf(false) }
    val outcome by key(sessionId, selectedId, refresh, current, available) {
        val updates = remember {
            if (current && available) service.scheduledTaskUiState(sessionId, selectedId)
            else flowOf<ScheduledTaskUiStateResult?>(null)
        }
        updates.collectAsStateWithLifecycle(initialValue = null)
    }
    val taskState = (outcome as? ScheduledTaskUiStateResult.Visible)?.state
    val snapshot = taskState?.snapshot
    val selected = snapshot?.tasks?.firstOrNull { it.id == selectedId }
    val listBack = { if (selectedId == null) back() else selectedId = null }
    BackHandler(current && selectedId != null, onBack = listBack)

    fun applyResult(result: ScheduledTaskActionResult) {
        when (result) {
            is ScheduledTaskActionResult.Saved -> {
                selectedId = result.task.id
                creating = false
                editing = null
            }
            is ScheduledTaskActionResult.Deleted -> if (selectedId == result.taskId) selectedId = null
            is ScheduledTaskActionResult.RunQueued -> Unit
            is ScheduledTaskActionResult.ConfirmationRequired -> Unit
            is ScheduledTaskActionResult.Rejected -> {
                if (result.reason != ScheduledTaskRejection.CONFIRMATION_DECLINED) issue = result.reason
            }
        }
    }

    fun request(action: ScheduledTaskAction) {
        if (!current || !available || busy) return
        busy = true
        issue = null
        model.perform {
            try {
                applyResult(service.requestScheduledTaskAction(sessionId, UUID.randomUUID().toString(), action))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 5.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), listBack)
            Text(
                stringResource(if (selectedId == null) R.string.elym_scheduled_tasks else R.string.elym_task_detail),
                Modifier.weight(1f).semantics { heading() }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            if (snapshot != null && selectedId == null) {
                RoundAction(R.drawable.elym_plus, stringResource(R.string.elym_task_new), { creating = true }, primary = true)
            }
        }
        key(selectedId) {
            Column(
                Modifier.fillMaxWidth().weight(1f).then(LocalPageBodyMotion.current).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    !available -> EmptyMessage(stringResource(R.string.elym_task_unavailable), R.drawable.elym_clock)
                    outcome == null -> LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.elym_task_loading)))
                    snapshot == null -> {
                        val failure = (outcome as ScheduledTaskUiStateResult.Rejected).reason
                        EmptyMessage(stringResource(failure.label), R.drawable.elym_clock) {
                            Action(stringResource(R.string.elym_retry), { refresh += 1 })
                        }
                    }
                    selectedId != null && selected == null -> EmptyMessage(stringResource(R.string.elym_task_not_found), R.drawable.elym_clock)
                    selected != null -> ScheduledTaskDetail(
                        selected, snapshot.runs, busy,
                        onRun = { request(ScheduledTaskAction.RunNow(selected.id, selected.revision)) },
                        onToggle = {
                            request(if (selected.enabled) ScheduledTaskAction.Pause(selected.id, selected.revision)
                                else ScheduledTaskAction.Resume(selected.id, selected.revision))
                        },
                        onEdit = { editing = selected },
                        onDelete = { deleting = selected },
                    )
                    else -> {
                        if (owner.isNotBlank()) Text(owner, color = Muted, fontSize = 14.sp)
                        if (!taskState.firstTaskCreated && !taskState.guideSkipped) {
                            ScheduledTaskGuide(
                                onCreate = { creating = true },
                                onSkip = {
                                    model.perform {
                                        val result = service.skipScheduledTaskGuide(sessionId)
                                        if (result is ScheduledTaskUiStateResult.Rejected) issue = result.reason
                                    }
                                },
                            )
                        }
                        TagStrip(rememberScrollState()) {
                            TaskFilter.entries.forEach { choice ->
                                TagChip(stringResource(choice.label), choice == filter) { filter = choice }
                            }
                        }
                        val tasks = snapshot.tasks.filter {
                            filter == TaskFilter.All || it.enabled == (filter == TaskFilter.Enabled)
                        }
                        if (tasks.isEmpty()) EmptyMessage(stringResource(R.string.elym_task_empty), R.drawable.elym_clock) {
                            if (filter == TaskFilter.All) Action(stringResource(R.string.elym_task_new), { creating = true }, primary = true)
                        } else Group {
                            tasks.forEachIndexed { index, task ->
                                if (index > 0) HorizontalDivider(color = Line)
                                val next = task.nextRunAt
                                ListRow(
                                    task.title, R.drawable.elym_clock, { selectedId = task.id },
                                    subtitle = if (next == null) repeatLabel(task.repeatRule) else stringResource(
                                        R.string.elym_task_next_at, taskTime(next, task.timeZoneId),
                                    ),
                                    badge = stringResource(task.statusLabel),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (current && available && (creating || editing != null)) {
        val original = editing
        ScheduledTaskEditor(original, busy, dismiss = { creating = false; editing = null }) { draft ->
            request(if (original == null) ScheduledTaskAction.Create(draft)
                else ScheduledTaskAction.Update(original.id, original.revision, draft))
        }
    }
    deleting?.takeIf { current && available }?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.elym_task_delete)) },
            text = { Text(task.title) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                request(ScheduledTaskAction.Delete(task.id, task.revision))
            }) { Text(stringResource(R.string.elym_task_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.elym_cancel)) } },
        )
    }
    issue?.takeIf { current }?.let { rejection ->
        AlertDialog(
            onDismissRequest = { issue = null },
            text = { Text(stringResource(rejection.label)) },
            confirmButton = { TextButton(onClick = { issue = null }) { Text(stringResource(R.string.elym_close)) } },
        )
    }
}

@Composable
private fun ScheduledTaskGuide(onCreate: () -> Unit, onSkip: () -> Unit) {
    Group {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.elym_task_first), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                RoundAction(R.drawable.elym_close, stringResource(R.string.elym_guide_later), onSkip)
            }
            Action(stringResource(R.string.elym_task_new), onCreate, primary = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduledTaskDetail(
    task: ScheduledTask,
    runs: List<ScheduledTaskRunRecord>,
    busy: Boolean,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(task.title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(task.statusLabel), color = Muted, fontSize = 14.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Action(stringResource(R.string.elym_task_run_now), onRun, primary = true, enabled = !busy)
        Action(stringResource(if (task.enabled) R.string.elym_task_pause else R.string.elym_task_resume), onToggle, enabled = !busy)
        Action(stringResource(R.string.elym_task_edit), onEdit, enabled = !busy)
    }
    TaskDetails(task.prompt, task.startLocalDateTime.toString().replace('T', ' '), task.timeZoneId, task.repeatRule, task.endCondition)
    task.nextRunAt?.let { Text(stringResource(R.string.elym_task_next_at, taskTime(it, task.timeZoneId)), color = Muted) }
    Text(stringResource(if (task.creationSource == ScheduledTaskCreationSource.APP) R.string.elym_task_origin_app else R.string.elym_task_origin_agent), color = Muted)
    Section(stringResource(R.string.elym_task_runs)) {
        val latest = runs.filter { it.taskId == task.id }.sortedByDescending { it.triggeredAt }.take(6)
        if (latest.isEmpty()) EmptyMessage(stringResource(R.string.elym_task_no_runs), R.drawable.elym_clock)
        latest.forEachIndexed { index, run ->
            if (index > 0) HorizontalDivider(color = Line)
            val plannedAt = run.plannedAt
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(taskTime(run.triggeredAt, task.timeZoneId), color = Ink)
                Text(stringResource(run.status.label), color = if (run.status == ScheduledTaskRunStatus.FAILED) Danger else Muted)
                if (plannedAt != null) Text(stringResource(R.string.elym_task_planned_at, taskTime(plannedAt, task.timeZoneId)), color = Muted)
                else if (run.origin == ScheduledTaskRunOrigin.MANUAL) Text(stringResource(R.string.elym_task_manual), color = Muted)
                run.failureKind?.let { Text(stringResource(it.label), color = Danger) }
            }
        }
    }
    Action(stringResource(R.string.elym_task_delete), onDelete, danger = true, enabled = !busy)
}

@Composable
internal fun ScheduledTaskConfirmation(
    confirmation: ScheduledTaskActionResult.ConfirmationRequired,
    busy: Boolean,
    onDecision: (Boolean) -> Unit,
) {
    Sheet(
        stringResource(confirmation.summary.action.label), { if (!busy) onDecision(false) },
        maxContentHeightFraction = 0.88f, deliberateSwipe = true,
    ) {
        Text(confirmation.summary.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val draft = when (val action = confirmation.requestedAction) {
            is ScheduledTaskAction.Create -> action.draft
            is ScheduledTaskAction.Update -> action.draft
            else -> null
        }
        draft?.let { TaskDetails(it.prompt, it.startLocalDateTime.toString().replace('T', ' '), it.timeZoneId, it.repeatRule, it.endCondition) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.elym_task_approve), { onDecision(true) }, primary = true, enabled = !busy)
            Action(stringResource(R.string.elym_task_decline), { onDecision(false) }, enabled = !busy)
        }
    }
}

@Composable
private fun TaskDetails(prompt: String, start: String, zone: String, rule: ScheduledTaskRepeatRule, end: ScheduledTaskEndCondition) {
    Section(stringResource(R.string.elym_prompt)) {
        Text(prompt, Modifier.padding(14.dp), color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
    }
    Section(stringResource(R.string.elym_task_plan)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.elym_task_start_at, start), color = Ink)
            Text(stringResource(R.string.elym_task_timezone_value, zone), color = Ink)
            Text(repeatLabel(rule), color = Ink)
            if (rule != ScheduledTaskRepeatRule.Once) {
                Text(when (end) {
                    ScheduledTaskEndCondition.Never -> stringResource(R.string.elym_task_no_end)
                    is ScheduledTaskEndCondition.OnDate -> stringResource(R.string.elym_task_until, end.date.toString())
                    is ScheduledTaskEndCondition.AfterOccurrences -> pluralStringResource(R.plurals.elym_task_after_runs, end.count, end.count)
                }, color = Ink)
            }
        }
    }
}

@Composable
private fun repeatLabel(rule: ScheduledTaskRepeatRule): String = when (rule) {
    ScheduledTaskRepeatRule.Once -> stringResource(R.string.elym_task_once)
    ScheduledTaskRepeatRule.Daily -> stringResource(R.string.elym_task_daily)
    ScheduledTaskRepeatRule.Weekly -> stringResource(R.string.elym_task_weekly)
    is ScheduledTaskRepeatRule.IntervalHours -> pluralStringResource(R.plurals.elym_task_every_hours, rule.hours, rule.hours)
}

private fun taskTime(at: Instant, zone: String): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.of(zone)).format(at)

private val ScheduledTask.statusLabel: Int get() = when {
    !enabled -> R.string.elym_task_paused
    nextRunAt == null -> R.string.elym_task_ended
    else -> R.string.elym_task_enabled
}

private val ScheduledTaskActionKind.label: Int get() = when (this) {
    ScheduledTaskActionKind.CREATE -> R.string.elym_task_new
    ScheduledTaskActionKind.UPDATE -> R.string.elym_task_edit
    ScheduledTaskActionKind.PAUSE -> R.string.elym_task_pause
    ScheduledTaskActionKind.RESUME -> R.string.elym_task_resume
    ScheduledTaskActionKind.DELETE -> R.string.elym_task_delete
    ScheduledTaskActionKind.RUN_NOW -> R.string.elym_task_run_now
}

internal val ScheduledTaskRejection.label: Int get() = when (this) {
    ScheduledTaskRejection.UNAUTHORIZED, ScheduledTaskRejection.INACTIVE_INSTALLATION -> R.string.elym_task_unavailable
    ScheduledTaskRejection.TASK_NOT_FOUND -> R.string.elym_task_not_found
    ScheduledTaskRejection.REVISION_CONFLICT, ScheduledTaskRejection.IDEMPOTENCY_CONFLICT -> R.string.elym_task_changed
    ScheduledTaskRejection.INVALID_INPUT -> R.string.elym_invalid_input
    ScheduledTaskRejection.TASK_NOT_ENABLED -> R.string.elym_task_paused
    ScheduledTaskRejection.CONFIRMATION_EXPIRED -> R.string.elym_task_confirmation_expired
    ScheduledTaskRejection.CONFIRMATION_DECLINED -> R.string.elym_task_decline
    ScheduledTaskRejection.STORE_UNAVAILABLE -> R.string.elym_task_store_unavailable
}

private val ScheduledTaskRunStatus.label: Int get() = when (this) {
    ScheduledTaskRunStatus.QUEUED -> R.string.elym_task_run_queued
    ScheduledTaskRunStatus.RUNNING -> R.string.elym_task_run_running
    ScheduledTaskRunStatus.SUCCEEDED -> R.string.elym_task_run_succeeded
    ScheduledTaskRunStatus.FAILED -> R.string.elym_task_run_failed
    ScheduledTaskRunStatus.CANCELLED -> R.string.elym_task_run_cancelled
    ScheduledTaskRunStatus.MISSED -> R.string.elym_task_run_missed
    ScheduledTaskRunStatus.STALE -> R.string.elym_task_run_stale
}

private val ScheduledTaskRunFailureKind.label: Int get() = when (this) {
    ScheduledTaskRunFailureKind.HOST_REJECTED -> R.string.elym_task_host_rejected
    ScheduledTaskRunFailureKind.AGENT_FAILED -> R.string.elym_reply_failed
    ScheduledTaskRunFailureKind.USER_CANCELLED -> R.string.elym_task_user_cancelled
    ScheduledTaskRunFailureKind.TASK_CHANGED -> R.string.elym_task_changed
    ScheduledTaskRunFailureKind.TASK_PAUSED -> R.string.elym_task_paused
    ScheduledTaskRunFailureKind.PLUGIN_REPLACED -> R.string.elym_task_plugin_replaced
    ScheduledTaskRunFailureKind.UNKNOWN -> R.string.elym_task_failure_unknown
}
