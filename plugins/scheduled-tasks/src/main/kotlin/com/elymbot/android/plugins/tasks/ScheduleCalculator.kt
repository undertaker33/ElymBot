package com.elymbot.android.plugins.tasks

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

internal object ScheduleCalculator {
    fun nextOccurrenceAfter(
        task: TaskRecord,
        after: Instant,
        scheduledExecutionCount: Int,
    ): PlannedOccurrence? {
        if (task.endCondition.kind == TaskEndKind.COUNT &&
            scheduledExecutionCount >= requireNotNull(task.endCondition.occurrenceCount)
        ) {
            return null
        }

        val zone = ZoneId.of(task.timeZoneId)
        val start = task.startLocalDateTime.atZone(zone).toInstant()
        val candidate = when (task.repeatRule.kind) {
            TaskRepeatKind.ONCE -> start.takeIf { it > after }
            TaskRepeatKind.INTERVAL_HOURS -> nextIntervalOccurrence(task, start, after)
            TaskRepeatKind.DAILY -> nextCalendarOccurrence(task, zone, after, daily = true)
            TaskRepeatKind.WEEKLY -> nextCalendarOccurrence(task, zone, after, daily = false)
        } ?: return null

        if (candidate < task.eligibleFrom || isAfterEndDate(task, zone, candidate)) return null
        return plannedOccurrence(task, candidate)
    }

    fun latestOccurrenceAtOrBefore(task: TaskRecord, now: Instant): PlannedOccurrence? {
        val zone = ZoneId.of(task.timeZoneId)
        val start = task.startLocalDateTime.atZone(zone).toInstant()
        if (start > now || now < task.eligibleFrom) return null

        val candidate = when (task.repeatRule.kind) {
            TaskRepeatKind.ONCE -> start
            TaskRepeatKind.INTERVAL_HOURS -> {
                val step = Duration.ofHours(requireNotNull(task.repeatRule.intervalHours).toLong())
                val elapsed = Duration.between(start, now)
                val periods = elapsed.toHours() / step.toHours()
                start.plus(step.multipliedBy(periods))
            }
            TaskRepeatKind.DAILY -> latestCalendarOccurrence(task, zone, now, daily = true)
            TaskRepeatKind.WEEKLY -> latestCalendarOccurrence(task, zone, now, daily = false)
        } ?: return null

        if (candidate < task.eligibleFrom || isAfterEndDate(task, zone, candidate)) return null
        return plannedOccurrence(task, candidate)
    }

    private fun nextIntervalOccurrence(task: TaskRecord, start: Instant, after: Instant): Instant? {
        val hours = requireNotNull(task.repeatRule.intervalHours).toLong()
        val step = Duration.ofHours(hours)
        if (after < start) return start

        val elapsed = Duration.between(start, after)
        val elapsedHours = elapsed.toHours()
        val periods = (elapsedHours / hours) + 1
        return start.plus(step.multipliedBy(periods))
    }

    private fun nextCalendarOccurrence(
        task: TaskRecord,
        zone: ZoneId,
        after: Instant,
        daily: Boolean,
    ): Instant? {
        val startDate = task.startLocalDateTime.toLocalDate()
        val localTime = task.startLocalDateTime.toLocalTime()
        val afterDate = after.atZone(zone).toLocalDate()
        val intervalDays = if (daily) 1L else 7L
        val daysFromStart = ChronoUnit.DAYS.between(startDate, afterDate).coerceAtLeast(0)
        var date = startDate.plusDays((daysFromStart / intervalDays) * intervalDays)
        var candidate = LocalDateTime.of(date, localTime).atZone(zone).toInstant()
        if (candidate <= after) {
            date = date.plusDays(intervalDays)
            candidate = LocalDateTime.of(date, localTime).atZone(zone).toInstant()
        }
        if (date < startDate || !daily && date.dayOfWeek != startDate.dayOfWeek) return null
        return candidate
    }

    private fun latestCalendarOccurrence(
        task: TaskRecord,
        zone: ZoneId,
        now: Instant,
        daily: Boolean,
    ): Instant? {
        val startDate = task.startLocalDateTime.toLocalDate()
        val localTime = task.startLocalDateTime.toLocalTime()
        var date = now.atZone(zone).toLocalDate()
        var candidate = LocalDateTime.of(date, localTime).atZone(zone).toInstant()
        if (candidate > now) {
            date = date.minusDays(1)
            candidate = LocalDateTime.of(date, localTime).atZone(zone).toInstant()
        }
        if (daily) {
            if (date < startDate) return null
        } else {
            val daysSinceStart = ChronoUnit.DAYS.between(startDate, date)
            if (daysSinceStart < 0) return null
            date = date.minusDays(daysSinceStart % 7)
            candidate = LocalDateTime.of(date, localTime).atZone(zone).toInstant()
            if (candidate > now) return null
        }
        return candidate.takeIf { it >= task.startLocalDateTime.atZone(zone).toInstant() }
    }

    private fun isAfterEndDate(task: TaskRecord, zone: ZoneId, candidate: Instant): Boolean {
        val endDate = task.endCondition.endDate ?: return false
        return candidate.atZone(zone).toLocalDate().isAfter(endDate)
    }

    private fun plannedOccurrence(task: TaskRecord, plannedAt: Instant): PlannedOccurrence {
        val key = "${task.id}|${task.revision}|${plannedAt.toEpochMilli()}"
        val occurrenceId = UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
        val runId = UUID.nameUUIDFromBytes("run|$key".toByteArray(StandardCharsets.UTF_8)).toString()
        return PlannedOccurrence(plannedAt, occurrenceId, runId)
    }
}
