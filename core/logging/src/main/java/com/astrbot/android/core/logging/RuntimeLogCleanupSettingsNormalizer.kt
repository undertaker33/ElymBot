package com.astrbot.android.core.logging

internal fun normalizeRuntimeLogCleanupSettings(
    current: RuntimeLogCleanupSettings,
    enabled: Boolean,
    intervalHours: Int,
    intervalMinutes: Int,
    now: Long,
): RuntimeLogCleanupSettings {
    val hours = intervalHours.coerceAtLeast(0)
    val minutes = intervalMinutes.coerceIn(0, 59)
    val normalizedMinutes = if (enabled && hours == 0 && minutes == 0) 1 else minutes
    val lastCleanupAt = when {
        enabled && (!current.enabled || current.lastCleanupAtEpochMillis <= 0L) -> now
        else -> current.lastCleanupAtEpochMillis
    }
    return RuntimeLogCleanupSettings(
        enabled = enabled,
        intervalHours = hours,
        intervalMinutes = normalizedMinutes,
        lastCleanupAtEpochMillis = lastCleanupAt,
    )
}
