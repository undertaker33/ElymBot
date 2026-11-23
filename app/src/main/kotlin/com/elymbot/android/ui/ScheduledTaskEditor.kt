package com.elymbot.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elymbot.android.R
import com.elymbot.core.contract.ScheduledTask
import com.elymbot.core.contract.ScheduledTaskDraft
import com.elymbot.core.contract.ScheduledTaskEndCondition
import com.elymbot.core.contract.ScheduledTaskRepeatRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val TaskTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

private enum class RepeatOption(val label: Int) {
    Once(R.string.elym_task_once), Daily(R.string.elym_task_daily),
    Weekly(R.string.elym_task_weekly), Interval(R.string.elym_task_interval),
}

private enum class EndOption(val label: Int) {
    Never(R.string.elym_task_no_end), Date(R.string.elym_task_end_date), Count(R.string.elym_task_count),
}

@Composable
internal fun ScheduledTaskEditor(
    task: ScheduledTask?,
    submitting: Boolean,
    dismiss: () -> Unit,
    save: (ScheduledTaskDraft) -> Unit,
) {
    val initial = remember(task) { task?.startLocalDateTime ?: LocalDateTime.now().plusMinutes(5) }
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var prompt by rememberSaveable(task?.id) { mutableStateOf(task?.prompt.orEmpty()) }
    var date by rememberSaveable(task?.id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(task?.id) { mutableStateOf(initial.toLocalTime().format(TaskTimeFormat)) }
    var timeZone by rememberSaveable(task?.id) { mutableStateOf(task?.timeZoneId ?: ZoneId.systemDefault().id) }
    var repeat by rememberSaveable(task?.id) {
        mutableStateOf(when (task?.repeatRule) {
            null, ScheduledTaskRepeatRule.Once -> RepeatOption.Once
            ScheduledTaskRepeatRule.Daily -> RepeatOption.Daily
            ScheduledTaskRepeatRule.Weekly -> RepeatOption.Weekly
            is ScheduledTaskRepeatRule.IntervalHours -> RepeatOption.Interval
        })
    }
    var hours by rememberSaveable(task?.id) {
        mutableStateOf((task?.repeatRule as? ScheduledTaskRepeatRule.IntervalHours)?.hours?.toString().orEmpty())
    }
    var end by rememberSaveable(task?.id) {
        mutableStateOf(when (task?.endCondition) {
            null, ScheduledTaskEndCondition.Never -> EndOption.Never
            is ScheduledTaskEndCondition.OnDate -> EndOption.Date
            is ScheduledTaskEndCondition.AfterOccurrences -> EndOption.Count
        })
    }
    var endDate by rememberSaveable(task?.id) {
        mutableStateOf((task?.endCondition as? ScheduledTaskEndCondition.OnDate)?.date?.toString().orEmpty())
    }
    var count by rememberSaveable(task?.id) {
        mutableStateOf((task?.endCondition as? ScheduledTaskEndCondition.AfterOccurrences)?.count?.toString().orEmpty())
    }
    var invalid by rememberSaveable(task?.id) { mutableStateOf(false) }
    val isoDate = stringResource(R.string.elym_task_date_format)
    Sheet(
        stringResource(if (task == null) R.string.elym_task_new else R.string.elym_task_edit),
        { if (!submitting) dismiss() },
        maxContentHeightFraction = 0.88f,
        deliberateSwipe = true,
    ) {
        Field(stringResource(R.string.elym_task_name), title, { title = it.take(40) })
        Field(stringResource(R.string.elym_prompt), prompt, { prompt = it.take(1600) }, multiline = true)
        Field(stringResource(R.string.elym_task_start_date), date, { date = it }, isoDate)
        Field(stringResource(R.string.elym_task_start_time), time, { time = it }, stringResource(R.string.elym_task_time_format))
        Field(stringResource(R.string.elym_task_timezone), timeZone, { timeZone = it }, ZoneId.systemDefault().id)
        TaskEditorChoice(R.string.elym_task_repeat, repeat, RepeatOption.entries.map { it to it.label }) { repeat = it }
        if (repeat == RepeatOption.Interval) {
            Field(stringResource(R.string.elym_task_interval_hours), hours, { hours = it.filter(Char::isDigit).take(3) })
        }
        if (repeat != RepeatOption.Once) {
            TaskEditorChoice(R.string.elym_task_end, end, EndOption.entries.map { it to it.label }) { end = it }
            when (end) {
                EndOption.Never -> Unit
                EndOption.Date -> Field(stringResource(R.string.elym_task_end_date), endDate, { endDate = it }, isoDate)
                EndOption.Count -> Field(stringResource(R.string.elym_task_count), count, { count = it.filter(Char::isDigit).take(3) })
            }
        }
        if (invalid) Text(stringResource(R.string.elym_invalid_input), color = Danger)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.elym_save), {
                val draft = runCatching {
                    val start = LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim(), TaskTimeFormat))
                    val zone = ZoneId.of(timeZone.trim())
                    val repeatRule = when (repeat) {
                        RepeatOption.Once -> ScheduledTaskRepeatRule.Once
                        RepeatOption.Daily -> ScheduledTaskRepeatRule.Daily
                        RepeatOption.Weekly -> ScheduledTaskRepeatRule.Weekly
                        RepeatOption.Interval -> ScheduledTaskRepeatRule.IntervalHours(hours.toInt())
                    }
                    val endCondition = if (repeat == RepeatOption.Once) ScheduledTaskEndCondition.Never else when (end) {
                        EndOption.Never -> ScheduledTaskEndCondition.Never
                        EndOption.Date -> ScheduledTaskEndCondition.OnDate(LocalDate.parse(endDate.trim()))
                        EndOption.Count -> ScheduledTaskEndCondition.AfterOccurrences(count.toInt())
                    }
                    ScheduledTaskDraft(title.trim(), prompt.trim(), start, zone.id, repeatRule, endCondition)
                }.getOrNull()
                invalid = draft == null
                if (draft != null) save(draft)
            }, primary = true, enabled = !submitting && title.isNotBlank() && prompt.isNotBlank())
            Action(stringResource(R.string.elym_cancel), dismiss, enabled = !submitting)
        }
    }
}

@Composable
private fun <T> TaskEditorChoice(label: Int, selected: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.first { it.first == selected }.second
    Box {
        ListRow(stringResource(label), R.drawable.elym_sliders, { expanded = true }, subtitle = stringResource(selectedLabel))
        DropdownMenu(expanded, { expanded = false }, containerColor = SurfaceColor) {
            options.forEach { (option, text) ->
                DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
