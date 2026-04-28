package com.astrbot.android.core.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultRuntimeLogStore(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val throttleIntervalMs: Long = DEFAULT_THROTTLE_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timestampFormatter: (Long) -> String = defaultTimestampFormatter(),
    private val sink: RuntimeLogSink = AndroidRuntimeLogSink(),
    initialLogs: List<String> = listOf("System initialized"),
) : RuntimeLogStore {

    init {
        require(maxEntries > 0) { "maxEntries must be positive." }
        require(throttleIntervalMs >= 0L) { "throttleIntervalMs must not be negative." }
    }

    private val ringLock = Any()
    private val ring = arrayOfNulls<String>(maxEntries)
    private var head = 0
    private var size = 0

    private val _logs = MutableStateFlow(initialLogs)
    override val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Volatile
    private var lastEmitMs = 0L

    @Volatile
    private var dirty = false

    override fun append(message: String) {
        val now = clock()
        val entry = "${timestampFormatter(now)}  $message"
        synchronized(ringLock) {
            ring[head] = entry
            head = (head + 1) % maxEntries
            if (size < maxEntries) size++
            dirty = true
        }
        sink.append(message)

        if (now - lastEmitMs >= throttleIntervalMs) {
            flush()
        }
    }

    override fun flush() {
        if (!dirty) return
        lastEmitMs = clock()
        dirty = false
        _logs.value = snapshot()
    }

    override fun clear() {
        synchronized(ringLock) {
            ring.fill(null)
            head = 0
            size = 0
            lastEmitMs = 0L
            dirty = false
        }
        _logs.value = emptyList()
    }

    private fun snapshot(): List<String> = synchronized(ringLock) {
        if (size == 0) return@synchronized emptyList()
        val start = (head - size + maxEntries) % maxEntries
        List(size) { index -> ring[(start + index) % maxEntries]!! }
    }

    private companion object {
        private const val DEFAULT_MAX_ENTRIES = 2_000
        private const val DEFAULT_THROTTLE_INTERVAL_MS = 200L

        private fun defaultTimestampFormatter(): (Long) -> String {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
            return { millis -> formatter.format(Date(millis)) }
        }
    }
}

