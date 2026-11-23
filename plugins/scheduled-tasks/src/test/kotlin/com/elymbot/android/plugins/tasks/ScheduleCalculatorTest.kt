package com.elymbot.android.plugins.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ScheduleCalculatorTest {
    @Test
    fun dailyTimeRemainsLocalAcrossDaylightSavingChange() {
        val task = task(
            start = "2026-03-07T09:00",
            zone = "America/New_York",
            repeatRule = TaskRepeatRule(TaskRepeatKind.DAILY),
        )

        val next = ScheduleCalculator.nextOccurrenceAfter(
            task = task,
            after = Instant.parse("2026-03-07T14:00:00Z"),
            scheduledExecutionCount = 0,
        )

        assertEquals(Instant.parse("2026-03-08T13:00:00Z"), next?.plannedAt)
    }

    @Test
    fun weeklyRuleKeepsStartWeekdayAndLocalTime() {
        val task = task(
            start = "2026-11-16T18:30",
            zone = "Asia/Shanghai",
            repeatRule = TaskRepeatRule(TaskRepeatKind.WEEKLY),
        )

        val next = ScheduleCalculator.nextOccurrenceAfter(
            task = task,
            after = Instant.parse("2026-11-23T11:00:00Z"),
            scheduledExecutionCount = 0,
        )

        assertEquals(Instant.parse("2026-11-30T10:30:00Z"), next?.plannedAt)
    }

    @Test
    fun intervalRuleUsesElapsedHoursAndCountEndCondition() {
        val task = task(
            start = "2026-11-23T08:00",
            zone = "UTC",
            repeatRule = TaskRepeatRule(TaskRepeatKind.INTERVAL_HOURS, intervalHours = 5),
            endCondition = TaskEndCondition(TaskEndKind.COUNT, occurrenceCount = 2),
        )

        val next = ScheduleCalculator.nextOccurrenceAfter(
            task = task,
            after = Instant.parse("2026-11-23T09:00:00Z"),
            scheduledExecutionCount = 1,
        )
        val ended = ScheduleCalculator.nextOccurrenceAfter(
            task = task,
            after = Instant.parse("2026-11-23T09:00:00Z"),
            scheduledExecutionCount = 2,
        )

        assertEquals(Instant.parse("2026-11-23T13:00:00Z"), next?.plannedAt)
        assertNull(ended)
    }

    private fun task(
        start: String,
        zone: String,
        repeatRule: TaskRepeatRule,
        endCondition: TaskEndCondition = TaskEndCondition(TaskEndKind.NEVER),
    ) = TaskRecord(
        id = "task-1",
        revision = 1,
        title = "Review",
        prompt = "Review the day",
        startLocalDateTime = LocalDateTime.parse(start),
        timeZoneId = zone,
        repeatRule = repeatRule,
        endCondition = endCondition,
        enabled = true,
        eligibleFrom = Instant.parse("2026-01-01T00:00:00Z"),
        creationSource = TaskCreationSource.APP,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
