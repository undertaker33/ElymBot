package com.astrbot.android.core.logging

import kotlinx.coroutines.flow.StateFlow

/**
 * Temporary shared owner for legacy static callers while UI surfaces migrate to injected logging.
 */
object SharedRuntimeLogStore : RuntimeLogStore {
    private val delegate: RuntimeLogStore = DefaultRuntimeLogStore()

    override val logs: StateFlow<List<String>>
        get() = delegate.logs

    override fun append(message: String) {
        delegate.append(message)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun clear() {
        delegate.clear()
    }
}
