package com.astrbot.android.core.logging

data class RuntimeLogCleanupSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val intervalMinutes: Int = 0,
    val lastCleanupAtEpochMillis: Long = 0L,
) {
    init {
        require(intervalHours >= 0) { "intervalHours must not be negative." }
        require(intervalMinutes in 0..59) { "intervalMinutes must be between 0 and 59." }
    }

    fun intervalMillis(): Long {
        return intervalHours * 60L * 60L * 1000L + intervalMinutes * 60L * 1000L
    }

    fun shouldAutoClear(now: Long): Boolean {
        if (!enabled) return false
        val intervalMillis = intervalMillis()
        if (intervalMillis <= 0L) return false
        if (lastCleanupAtEpochMillis <= 0L) return false
        return now - lastCleanupAtEpochMillis >= intervalMillis
    }
}

