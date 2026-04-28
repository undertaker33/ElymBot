package com.astrbot.android.core.logging

import android.content.SharedPreferences

interface RuntimeLogCleanupSettingsStore {
    fun load(): RuntimeLogCleanupSettings

    fun save(settings: RuntimeLogCleanupSettings)
}

class InMemoryRuntimeLogCleanupSettingsStore : RuntimeLogCleanupSettingsStore {
    private var value = RuntimeLogCleanupSettings()

    override fun load(): RuntimeLogCleanupSettings = value

    override fun save(settings: RuntimeLogCleanupSettings) {
        value = settings
    }
}

class SharedPreferencesRuntimeLogCleanupSettingsStore(
    private val prefs: SharedPreferences,
) : RuntimeLogCleanupSettingsStore {
    override fun load(): RuntimeLogCleanupSettings {
        return RuntimeLogCleanupSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            intervalHours = prefs.getInt(KEY_HOURS, 12),
            intervalMinutes = prefs.getInt(KEY_MINUTES, 0),
            lastCleanupAtEpochMillis = prefs.getLong(KEY_LAST_CLEANUP, 0L),
        )
    }

    override fun save(settings: RuntimeLogCleanupSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOURS, settings.intervalHours)
            .putInt(KEY_MINUTES, settings.intervalMinutes)
            .putLong(KEY_LAST_CLEANUP, settings.lastCleanupAtEpochMillis)
            .apply()
    }

    private companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOURS = "hours"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_LAST_CLEANUP = "last_cleanup"
    }
}

