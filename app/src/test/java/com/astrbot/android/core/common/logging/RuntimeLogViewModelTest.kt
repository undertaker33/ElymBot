package com.astrbot.android.core.common.logging

import com.astrbot.android.core.logging.RuntimeLogCleanupSettings
import com.astrbot.android.core.logging.RuntimeLogMaintenanceService
import com.astrbot.android.core.logging.RuntimeLogStore
import com.astrbot.android.ui.viewmodel.RuntimeLogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogViewModelTest {

    @Test
    fun exposes_logs_and_cleanup_settings_from_injected_store_and_service() {
        val store = FakeRuntimeLogStore(initialLogs = listOf("boot", "ready"))
        val maintenance = FakeRuntimeLogMaintenanceService(
            initialSettings = RuntimeLogCleanupSettings(
                enabled = true,
                intervalHours = 1,
                intervalMinutes = 15,
                lastCleanupAtEpochMillis = 100L,
            ),
        )

        val viewModel = RuntimeLogViewModel(store, maintenance)

        assertSame(store.logs, viewModel.logs)
        assertSame(maintenance.settings, viewModel.cleanupSettings)
        assertEquals(listOf("boot", "ready"), viewModel.logs.value)
        assertTrue(viewModel.cleanupSettings.value.enabled)
    }

    @Test
    fun clearLogs_clears_injected_store_and_records_cleanup() {
        val store = FakeRuntimeLogStore(initialLogs = listOf("one"))
        val maintenance = FakeRuntimeLogMaintenanceService(
            initialSettings = RuntimeLogCleanupSettings(enabled = true),
        )
        val viewModel = RuntimeLogViewModel(store, maintenance)

        viewModel.clearLogs()

        assertEquals(emptyList<String>(), store.logs.value)
        assertEquals(1, store.clearCount)
        assertEquals(1, maintenance.recordCleanupCount)
    }

    @Test
    fun maybeAutoClear_delegates_to_service_with_injected_store_clear_callback() {
        val store = FakeRuntimeLogStore(initialLogs = listOf("old"))
        val maintenance = FakeRuntimeLogMaintenanceService(
            initialSettings = RuntimeLogCleanupSettings(enabled = true),
            autoClearResult = true,
        )
        val viewModel = RuntimeLogViewModel(store, maintenance)

        val cleared = viewModel.maybeAutoClear()

        assertTrue(cleared)
        assertEquals(1, maintenance.maybeAutoClearCount)
        assertEquals(1, store.clearCount)
        assertEquals(emptyList<String>(), store.logs.value)
    }

    @Test
    fun maybeAutoClear_does_not_clear_store_when_service_declines() {
        val store = FakeRuntimeLogStore(initialLogs = listOf("fresh"))
        val maintenance = FakeRuntimeLogMaintenanceService(
            initialSettings = RuntimeLogCleanupSettings(enabled = true),
            autoClearResult = false,
        )
        val viewModel = RuntimeLogViewModel(store, maintenance)

        val cleared = viewModel.maybeAutoClear()

        assertFalse(cleared)
        assertEquals(1, maintenance.maybeAutoClearCount)
        assertEquals(0, store.clearCount)
        assertEquals(listOf("fresh"), store.logs.value)
    }

    @Test
    fun updateCleanupSettings_delegates_to_injected_service() {
        val store = FakeRuntimeLogStore()
        val maintenance = FakeRuntimeLogMaintenanceService()
        val viewModel = RuntimeLogViewModel(store, maintenance)

        viewModel.updateCleanupSettings(enabled = true, intervalHours = 2, intervalMinutes = 30)

        assertEquals(Triple(true, 2, 30), maintenance.lastUpdate)
    }

    private class FakeRuntimeLogStore(
        initialLogs: List<String> = emptyList(),
    ) : RuntimeLogStore {
        private val _logs = MutableStateFlow(initialLogs)
        override val logs: StateFlow<List<String>> = _logs
        var clearCount = 0

        override fun append(message: String) {
            _logs.value = _logs.value + message
        }

        override fun flush() = Unit

        override fun clear() {
            clearCount += 1
            _logs.value = emptyList()
        }
    }

    private class FakeRuntimeLogMaintenanceService(
        initialSettings: RuntimeLogCleanupSettings = RuntimeLogCleanupSettings(),
        private val autoClearResult: Boolean = false,
    ) : RuntimeLogMaintenanceService {
        private val _settings = MutableStateFlow(initialSettings)
        override val settings: StateFlow<RuntimeLogCleanupSettings> = _settings
        var recordCleanupCount = 0
        var maybeAutoClearCount = 0
        var lastUpdate: Triple<Boolean, Int, Int>? = null

        override fun updateSettings(
            enabled: Boolean,
            intervalHours: Int,
            intervalMinutes: Int,
            now: Long,
        ) {
            lastUpdate = Triple(enabled, intervalHours, intervalMinutes)
            _settings.value = RuntimeLogCleanupSettings(
                enabled = enabled,
                intervalHours = intervalHours,
                intervalMinutes = intervalMinutes,
                lastCleanupAtEpochMillis = now,
            )
        }

        override fun recordCleanup(now: Long) {
            recordCleanupCount += 1
            _settings.value = _settings.value.copy(lastCleanupAtEpochMillis = now)
        }

        override fun maybeAutoClear(
            now: Long,
            onClear: () -> Unit,
        ): Boolean {
            maybeAutoClearCount += 1
            if (autoClearResult) {
                onClear()
            }
            return autoClearResult
        }
    }
}
