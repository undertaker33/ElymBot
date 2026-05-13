package com.astrbot.android.core.common.logging

import android.content.Context
import com.astrbot.android.core.logging.InMemoryRuntimeLogCleanupSettingsStore as CoreInMemoryRuntimeLogCleanupSettingsStore
import com.astrbot.android.core.logging.RuntimeLogCleanupSettingsStore as CoreRuntimeLogCleanupSettingsStore
import com.astrbot.android.core.logging.SharedRuntimeLogStore
import com.astrbot.android.core.logging.SharedPreferencesRuntimeLogCleanupSettingsStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias RuntimeLogCleanupSettings = com.astrbot.android.core.logging.RuntimeLogCleanupSettings
typealias RuntimeLogCleanupSettingsStore = CoreRuntimeLogCleanupSettingsStore
typealias InMemoryRuntimeLogCleanupSettingsStore = CoreInMemoryRuntimeLogCleanupSettingsStore

internal object RuntimeLogRepository {
    val logs: StateFlow<List<String>>
        get() = SharedRuntimeLogStore.logs

    fun append(message: String) {
        SharedRuntimeLogStore.append(message)
    }

    fun flush() {
        SharedRuntimeLogStore.flush()
    }

    fun clear() {
        SharedRuntimeLogStore.clear()
    }
}

internal object AppLogger {
    fun append(message: String) {
        SharedRuntimeLogStore.append(message)
    }

    fun flush() {
        SharedRuntimeLogStore.flush()
    }
}

internal object RuntimeLogCleanupRepository {
    private const val PREFS_NAME = "runtime_log_cleanup"

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var storeOverrideForTests: RuntimeLogCleanupSettingsStore? = null

    val settings: StateFlow<RuntimeLogCleanupSettings>
        get() = TestRuntimeLogMaintenanceService.settings

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true) && storeOverrideForTests == null) return
        val store = storeOverrideForTests ?: SharedPreferencesRuntimeLogCleanupSettingsStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
        TestRuntimeLogMaintenanceService.replaceSettingsStore(store)
    }

    fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        TestRuntimeLogMaintenanceService.updateSettings(
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
    }

    fun recordCleanup(now: Long = System.currentTimeMillis()) {
        TestRuntimeLogMaintenanceService.recordCleanup(now)
    }

    fun maybeAutoClear(
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean {
        return TestRuntimeLogMaintenanceService.maybeAutoClear(now = now, onClear = onClear)
    }

    fun setStoreOverrideForTests(store: RuntimeLogCleanupSettingsStore?) {
        storeOverrideForTests = store
        TestRuntimeLogMaintenanceService.replaceSettingsStore(
            store ?: InMemoryRuntimeLogCleanupSettingsStore(),
        )
        initialized.set(store != null)
    }
}

private object TestRuntimeLogMaintenanceService {
    @Volatile
    private var store: RuntimeLogCleanupSettingsStore = InMemoryRuntimeLogCleanupSettingsStore()

    private val mutableSettings = MutableStateFlow(store.load())

    val settings: StateFlow<RuntimeLogCleanupSettings>
        get() = mutableSettings

    fun replaceSettingsStore(nextStore: RuntimeLogCleanupSettingsStore) {
        store = nextStore
        mutableSettings.value = nextStore.load()
    }

    fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ) {
        persist(
            normalize(
                current = mutableSettings.value,
                enabled = enabled,
                intervalHours = intervalHours,
                intervalMinutes = intervalMinutes,
                now = now,
            ),
        )
    }

    fun recordCleanup(now: Long) {
        val current = mutableSettings.value
        if (!current.enabled) return
        persist(current.copy(lastCleanupAtEpochMillis = now))
    }

    fun maybeAutoClear(
        now: Long,
        onClear: () -> Unit,
    ): Boolean {
        val current = mutableSettings.value
        if (!current.shouldAutoClear(now)) return false
        onClear()
        persist(current.copy(lastCleanupAtEpochMillis = now))
        return true
    }

    private fun persist(settings: RuntimeLogCleanupSettings) {
        store.save(settings)
        mutableSettings.value = settings
    }

    private fun normalize(
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
}
