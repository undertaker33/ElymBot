package com.astrbot.android.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultRuntimeLogMaintenanceService(
    private val store: RuntimeLogCleanupSettingsStore,
) : RuntimeLogMaintenanceService {

    private val _settings = MutableStateFlow(store.load())
    override val settings: StateFlow<RuntimeLogCleanupSettings> = _settings.asStateFlow()

    override fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ) {
        val normalized = normalizeRuntimeLogCleanupSettings(
            current = _settings.value,
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
        persist(normalized)
    }

    override fun recordCleanup(now: Long) {
        val current = _settings.value
        if (!current.enabled) return
        persist(current.copy(lastCleanupAtEpochMillis = now))
    }

    override fun maybeAutoClear(
        now: Long,
        onClear: () -> Unit,
    ): Boolean {
        val current = _settings.value
        if (!current.shouldAutoClear(now)) return false
        onClear()
        persist(current.copy(lastCleanupAtEpochMillis = now))
        return true
    }

    private fun persist(settings: RuntimeLogCleanupSettings) {
        store.save(settings)
        _settings.value = settings
    }
}
