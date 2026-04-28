package com.astrbot.android.core.common.logging

@Deprecated(
    message = "Compat facade only. Inject RuntimeLogger in new production code.",
)
object AppLogger {
    fun append(message: String) {
        RuntimeLogRepository.append(message)
    }

    fun flush() {
        RuntimeLogRepository.flush()
    }
}
