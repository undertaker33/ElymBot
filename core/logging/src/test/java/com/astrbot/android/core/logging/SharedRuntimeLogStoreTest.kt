package com.astrbot.android.core.logging

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeLogStoreTest {

    @After
    fun tearDown() {
        SharedRuntimeLogStore.clear()
    }

    @Test
    fun append_is_visible_from_same_shared_store_instance() {
        val store: RuntimeLogStore = SharedRuntimeLogStore

        store.append("shared-entry")
        SharedRuntimeLogStore.flush()

        assertTrue(SharedRuntimeLogStore.logs.value.any { entry ->
            entry.contains("shared-entry")
        })
    }
}
