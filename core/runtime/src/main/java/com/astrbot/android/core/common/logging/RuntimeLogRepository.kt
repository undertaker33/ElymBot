package com.astrbot.android.core.common.logging

import com.astrbot.android.core.logging.RuntimeLogStore
import com.astrbot.android.core.logging.SharedRuntimeLogStore
import kotlinx.coroutines.flow.StateFlow

@Deprecated(
    message = "Compat facade only. Inject RuntimeLogStore or RuntimeLogger in new production code.",
)
object RuntimeLogRepository {
    private val compatStore: RuntimeLogStore = SharedRuntimeLogStore

    val logs: StateFlow<List<String>>
        get() = compatStore.logs

    fun append(message: String) {
        compatStore.append(message)
    }

    fun flush() {
        compatStore.flush()
    }

    fun clear() {
        compatStore.clear()
    }
}
