package com.astrbot.android.core.logging

import kotlinx.coroutines.flow.StateFlow

interface RuntimeLogMaintenanceService {
    val settings: StateFlow<RuntimeLogCleanupSettings>

    fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    )

    fun recordCleanup(now: Long = System.currentTimeMillis())

    fun maybeAutoClear(
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean
}

