package com.astrbot.android.feature.plugin.runtime

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.feature.plugin.domain.PluginRuntimeLogCleanupSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PluginRuntimeLogCleanupSettingsStore {
    fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings>

    fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    )
}

class InMemoryPluginRuntimeLogCleanupSettingsStore : PluginRuntimeLogCleanupSettingsStore {
    private val values = linkedMapOf<String, PluginRuntimeLogCleanupSettings>()

    override fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings> = values.toMap()

    override fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        values[pluginId] = settings
    }
}

private class SharedPreferencesPluginRuntimeLogCleanupSettingsStore(
    private val prefs: SharedPreferences,
) : PluginRuntimeLogCleanupSettingsStore {
    override fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings> {
        val all = prefs.all
        val pluginIds = all.keys
            .mapNotNull { key ->
                key.substringBefore('.', "").takeIf { key.contains('.') && it.isNotBlank() }
            }
            .toSet()

        return pluginIds.associateWith { pluginId ->
            PluginRuntimeLogCleanupSettings(
                enabled = prefs.getBoolean(key(pluginId, "enabled"), false),
                intervalHours = prefs.getInt(key(pluginId, "hours"), 12),
                intervalMinutes = prefs.getInt(key(pluginId, "minutes"), 0),
                lastCleanupAtEpochMillis = prefs.getLong(key(pluginId, "last_cleanup"), 0L),
            )
        }
    }

    override fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        prefs.edit()
            .putBoolean(key(pluginId, "enabled"), settings.enabled)
            .putInt(key(pluginId, "hours"), settings.intervalHours)
            .putInt(key(pluginId, "minutes"), settings.intervalMinutes)
            .putLong(key(pluginId, "last_cleanup"), settings.lastCleanupAtEpochMillis)
            .apply()
    }

    private fun key(
        pluginId: String,
        suffix: String,
    ): String = "$pluginId.$suffix"
}

interface PluginLogMaintenanceService {
    val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>>

    fun settingsFor(pluginId: String): PluginRuntimeLogCleanupSettings

    fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    )

    fun recordCleanup(
        pluginId: String,
        now: Long = System.currentTimeMillis(),
    )

    fun maybeAutoClear(
        pluginId: String,
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean
}

class NoOpPluginLogMaintenanceService : PluginLogMaintenanceService {
    private val emptySettings = MutableStateFlow<Map<String, PluginRuntimeLogCleanupSettings>>(emptyMap())

    override val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>> = emptySettings.asStateFlow()

    override fun settingsFor(pluginId: String): PluginRuntimeLogCleanupSettings = PluginRuntimeLogCleanupSettings()

    override fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ) = Unit

    override fun recordCleanup(
        pluginId: String,
        now: Long,
    ) = Unit

    override fun maybeAutoClear(
        pluginId: String,
        now: Long,
        onClear: () -> Unit,
    ): Boolean = false
}

class DefaultPluginLogMaintenanceService(
    private val store: PluginRuntimeLogCleanupSettingsStore = InMemoryPluginRuntimeLogCleanupSettingsStore(),
) : PluginLogMaintenanceService {
    private val _settings = MutableStateFlow(store.loadAll())
    override val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>> = _settings.asStateFlow()

    override fun settingsFor(pluginId: String): PluginRuntimeLogCleanupSettings {
        return _settings.value[pluginId] ?: PluginRuntimeLogCleanupSettings()
    }

    override fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ) {
        val current = settingsFor(pluginId)
        val normalized = normalizeSettings(
            current = current,
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
        persist(pluginId, normalized)
    }

    override fun recordCleanup(
        pluginId: String,
        now: Long,
    ) {
        val current = settingsFor(pluginId)
        if (!current.enabled) return
        persist(pluginId, current.copy(lastCleanupAtEpochMillis = now))
    }

    override fun maybeAutoClear(
        pluginId: String,
        now: Long,
        onClear: () -> Unit,
    ): Boolean {
        val current = settingsFor(pluginId)
        if (!current.shouldAutoClear(now)) return false
        onClear()
        persist(pluginId, current.copy(lastCleanupAtEpochMillis = now))
        return true
    }

    private fun persist(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        store.save(pluginId, settings)
        _settings.value = _settings.value.toMutableMap().apply {
            put(pluginId, settings)
        }
    }

    private fun normalizeSettings(
        current: PluginRuntimeLogCleanupSettings,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ): PluginRuntimeLogCleanupSettings {
        val hours = intervalHours.coerceAtLeast(0)
        val minutes = intervalMinutes.coerceIn(0, 59)
        val normalizedMinutes = if (enabled && hours == 0 && minutes == 0) 1 else minutes
        val lastCleanupAt = when {
            enabled && (!current.enabled || current.lastCleanupAtEpochMillis <= 0L) -> now
            else -> current.lastCleanupAtEpochMillis
        }
        return PluginRuntimeLogCleanupSettings(
            enabled = enabled,
            intervalHours = hours,
            intervalMinutes = normalizedMinutes,
            lastCleanupAtEpochMillis = lastCleanupAt,
        )
    }
}

fun createSharedPreferencesPluginLogMaintenanceService(context: Context): PluginLogMaintenanceService {
    val prefs = context.applicationContext.getSharedPreferences(
        PLUGIN_RUNTIME_LOG_CLEANUP_PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    return DefaultPluginLogMaintenanceService(
        store = SharedPreferencesPluginRuntimeLogCleanupSettingsStore(prefs),
    )
}

private const val PLUGIN_RUNTIME_LOG_CLEANUP_PREFS_NAME = "plugin_runtime_log_cleanup"
