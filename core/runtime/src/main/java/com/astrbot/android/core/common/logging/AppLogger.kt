package com.astrbot.android.core.common.logging

import com.astrbot.android.core.logging.SharedRuntimeLogStore

@Deprecated(
    message = "Compat facade only. Inject RuntimeLogger in new production code.",
)
object AppLogger {
    fun append(message: String) {
        SharedRuntimeLogStore.append(message)
    }

    fun flush() {
        SharedRuntimeLogStore.flush()
    }
}
