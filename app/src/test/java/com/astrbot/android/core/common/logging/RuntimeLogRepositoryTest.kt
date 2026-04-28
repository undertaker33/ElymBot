package com.astrbot.android.core.common.logging

import com.astrbot.android.core.logging.SharedRuntimeLogStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogRepositoryTest {
    @After
    fun tearDown() {
        RuntimeLogRepository.clear()
    }

    @Test
    fun append_retains_large_recent_buffer_for_runtime_diagnostics() {
        RuntimeLogRepository.clear()

        repeat(2_105) { index ->
            RuntimeLogRepository.append("entry-$index")
        }
        RuntimeLogRepository.flush()

        val snapshot = RuntimeLogRepository.logs.value
        assertEquals(2_000, snapshot.size)
        assertTrue(snapshot.first().contains("entry-105"))
        assertTrue(snapshot.last().contains("entry-2104"))
    }

    @Test
    fun shared_core_logging_store_is_visible_through_legacy_repository() {
        RuntimeLogRepository.clear()

        SharedRuntimeLogStore.append("hilt-path-entry")
        SharedRuntimeLogStore.flush()

        assertTrue(RuntimeLogRepository.logs.value.any { entry ->
            entry.contains("hilt-path-entry")
        })
    }
}
