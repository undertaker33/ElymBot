package com.astrbot.android.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary shared maintenance bridge for legacy cleanup UI until it can inject the service.
 */
object SharedRuntimeLogMaintenanceService : RuntimeLogMaintenanceService {
    private val delegate = ResettableRuntimeLogMaintenanceService(
        initialStore = InMemoryRuntimeLogCleanupSettingsStore(),
    )

    override val settings: StateFlow<RuntimeLogCleanupSettings>
        get() = delegate.settings

    fun replaceSettingsStore(store: RuntimeLogCleanupSettingsStore) {
        delegate.replaceSettingsStore(store)
    }

    override fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ) {
        delegate.updateSettings(
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
    }

    override fun recordCleanup(now: Long) {
        delegate.recordCleanup(now)
    }

    override fun maybeAutoClear(
        now: Long,
        onClear: () -> Unit,
    ): Boolean {
        return delegate.maybeAutoClear(now = now, onClear = onClear)
    }
}

private class ResettableRuntimeLogMaintenanceService(
    initialStore: RuntimeLogCleanupSettingsStore,
) : RuntimeLogMaintenanceService {
    @Volatile
    private var store: RuntimeLogCleanupSettingsStore = initialStore

    private val _settings = MutableStateFlow(initialStore.load())
    override val settings: StateFlow<RuntimeLogCleanupSettings> = _settings.asStateFlow()

    fun replaceSettingsStore(nextStore: RuntimeLogCleanupSettingsStore) {
        store = nextStore
        _settings.value = nextStore.load()
    }

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
