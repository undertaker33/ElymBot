package com.elymbot.android.plugins.tasks

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

internal object TaskDraftValidator {
    fun validationError(
        draft: TaskDraft,
        now: Instant,
        existing: TaskRecord? = null,
    ): String? {
        if (draft.title.trim().isEmpty() || draft.title.trim().length > MAX_TITLE_LENGTH) {
            return "Task title is required and must be at most $MAX_TITLE_LENGTH characters."
        }
        if (draft.prompt.trim().isEmpty() || draft.prompt.trim().length > MAX_PROMPT_LENGTH) {
            return "Task prompt is required and must be at most $MAX_PROMPT_LENGTH characters."
        }
        if (draft.startLocalDateTime.second != 0 || draft.startLocalDateTime.nano != 0) {
            return "Task time must use minute precision."
        }

        val zone = try {
            ZoneId.of(draft.timeZoneId)
        } catch (_: DateTimeException) {
            return "Task time zone is invalid."
        }
        if (zone.rules.getValidOffsets(draft.startLocalDateTime).isEmpty()) {
            return "The selected local time does not exist in this time zone."
        }

        when (draft.repeatRule.kind) {
            TaskRepeatKind.ONCE, TaskRepeatKind.DAILY, TaskRepeatKind.WEEKLY -> {
                if (draft.repeatRule.intervalHours != null) return "This repeat rule cannot include an interval."
            }
            TaskRepeatKind.INTERVAL_HOURS -> {
                val hours = draft.repeatRule.intervalHours ?: return "An interval is required."
                if (hours !in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS) {
                    return "The interval must be between $MIN_INTERVAL_HOURS and $MAX_INTERVAL_HOURS hours."
                }
            }
        }

        when (draft.repeatRule.kind) {
            TaskRepeatKind.ONCE -> {
                if (draft.endCondition.kind != TaskEndKind.NEVER ||
                    draft.endCondition.endDate != null ||
                    draft.endCondition.occurrenceCount != null
                ) {
                    return "One-time tasks cannot have a repeat end condition."
                }
            }
            else -> when (draft.endCondition.kind) {
                TaskEndKind.NEVER -> {
                    if (draft.endCondition.endDate != null || draft.endCondition.occurrenceCount != null) {
                        return "An end condition value is not expected."
                    }
                }
                TaskEndKind.DATE -> {
                    val endDate = draft.endCondition.endDate ?: return "An end date is required."
                    if (endDate.isBefore(draft.startLocalDateTime.toLocalDate()) ||
                        draft.endCondition.occurrenceCount != null
                    ) {
                        return "The end date must be on or after the start date."
                    }
                }
                TaskEndKind.COUNT -> {
                    val count = draft.endCondition.occurrenceCount ?: return "An execution count is required."
                    if (count !in MIN_END_COUNT..MAX_END_COUNT || draft.endCondition.endDate != null) {
                        return "The execution count must be between $MIN_END_COUNT and $MAX_END_COUNT."
                    }
                }
            }
        }

        val startAt = draft.startLocalDateTime.atZone(zone).toInstant()
        val startChanged = existing == null ||
            existing.startLocalDateTime != draft.startLocalDateTime ||
            existing.timeZoneId != draft.timeZoneId ||
            existing.repeatRule != draft.repeatRule
        if (draft.repeatRule.kind == TaskRepeatKind.ONCE && startChanged && startAt <= now) {
            return "A new one-time task must be scheduled in the future."
        }
        return null
    }

    private const val MAX_TITLE_LENGTH = 40
    private const val MAX_PROMPT_LENGTH = 1_600
    private const val MIN_INTERVAL_HOURS = 1
    private const val MAX_INTERVAL_HOURS = 168
    private const val MIN_END_COUNT = 1
    private const val MAX_END_COUNT = 999
}
