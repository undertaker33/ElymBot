package com.astrbot.android.core.common.logging

import android.content.Context
import com.astrbot.android.core.logging.RuntimeLogMaintenanceService
import com.astrbot.android.core.logging.SharedRuntimeLogMaintenanceService
import com.astrbot.android.core.logging.SharedPreferencesRuntimeLogCleanupSettingsStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.StateFlow

typealias RuntimeLogCleanupSettings = com.astrbot.android.core.logging.RuntimeLogCleanupSettings
typealias RuntimeLogCleanupSettingsStore = com.astrbot.android.core.logging.RuntimeLogCleanupSettingsStore
typealias InMemoryRuntimeLogCleanupSettingsStore =
    com.astrbot.android.core.logging.InMemoryRuntimeLogCleanupSettingsStore

@Deprecated(
    message = "Compat facade only. Inject RuntimeLogMaintenanceService in new production code.",
)
object RuntimeLogCleanupRepository {
    private const val PREFS_NAME = "runtime_log_cleanup"

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var storeOverrideForTests: RuntimeLogCleanupSettingsStore? = null

    private val service: RuntimeLogMaintenanceService = SharedRuntimeLogMaintenanceService

    val settings: StateFlow<RuntimeLogCleanupSettings>
        get() = service.settings

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true) && storeOverrideForTests == null) return
        val store = storeOverrideForTests ?: SharedPreferencesRuntimeLogCleanupSettingsStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
        SharedRuntimeLogMaintenanceService.replaceSettingsStore(store)
    }

    fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        service.updateSettings(
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
    }

    fun recordCleanup(now: Long = System.currentTimeMillis()) {
        service.recordCleanup(now)
    }

    fun maybeAutoClear(
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean {
        return service.maybeAutoClear(now = now, onClear = onClear)
    }

    fun setStoreOverrideForTests(store: RuntimeLogCleanupSettingsStore?) {
        storeOverrideForTests = store
        SharedRuntimeLogMaintenanceService.replaceSettingsStore(
            store ?: InMemoryRuntimeLogCleanupSettingsStore(),
        )
        initialized.set(store != null)
    }
}
