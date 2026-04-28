package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.core.logging.RuntimeLogMaintenanceService
import com.astrbot.android.core.logging.RuntimeLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RuntimeLogViewModel @Inject constructor(
    private val runtimeLogStore: RuntimeLogStore,
    private val runtimeLogMaintenanceService: RuntimeLogMaintenanceService,
) : ViewModel() {
    val logs = runtimeLogStore.logs
    val cleanupSettings = runtimeLogMaintenanceService.settings

    fun clearLogs() {
        runtimeLogStore.clear()
        runtimeLogMaintenanceService.recordCleanup()
    }

    fun updateCleanupSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
    ) {
        runtimeLogMaintenanceService.updateSettings(
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
        )
    }

    fun recordCleanup() {
        runtimeLogMaintenanceService.recordCleanup()
    }

    fun maybeAutoClear(): Boolean {
        return runtimeLogMaintenanceService.maybeAutoClear {
            runtimeLogStore.clear()
        }
    }
}
