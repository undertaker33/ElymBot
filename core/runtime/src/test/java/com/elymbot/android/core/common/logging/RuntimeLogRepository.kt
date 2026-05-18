package com.elymbot.android.core.common.logging

import com.elymbot.android.core.logging.SharedRuntimeLogStore
import kotlinx.coroutines.flow.StateFlow

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
