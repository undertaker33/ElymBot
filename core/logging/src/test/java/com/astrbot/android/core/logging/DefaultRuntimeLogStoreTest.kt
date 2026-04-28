package com.astrbot.android.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRuntimeLogStoreTest {

    @Test
    fun append_retains_recent_entries_in_ring_buffer() {
        val sinkMessages = mutableListOf<String>()
        val store = DefaultRuntimeLogStore(
            maxEntries = 2_000,
            throttleIntervalMs = 200L,
            clock = { 1_000L },
            timestampFormatter = { "12:00:00" },
            sink = RuntimeLogSink { message -> sinkMessages += message },
        )

        repeat(2_105) { index ->
            store.append("entry-$index")
        }
        store.flush()

        val snapshot = store.logs.value
        assertEquals(2_000, snapshot.size)
        assertTrue(snapshot.first().contains("entry-105"))
        assertTrue(snapshot.last().contains("entry-2104"))
        assertEquals("entry-2104", sinkMessages.last())
    }

    @Test
    fun clear_removes_buffered_logs() {
        val store = DefaultRuntimeLogStore(
            clock = { 1_000L },
            timestampFormatter = { "12:00:00" },
            sink = RuntimeLogSink { },
        )

        store.append("hello")
        store.flush()
        store.clear()

        assertEquals(emptyList<String>(), store.logs.value)
    }
}
