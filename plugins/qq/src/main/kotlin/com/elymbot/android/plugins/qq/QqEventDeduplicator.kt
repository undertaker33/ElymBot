package com.elymbot.android.plugins.qq

import java.util.LinkedHashMap

/** Bounded in-memory duplicate guard; completion follows explicit host acceptance. */
internal class QqEventDeduplicator(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()
    private val completed = LinkedHashMap<String, Unit>(16, 0.75f, true)
    private val inFlight = mutableMapOf<String, Reservation>()

    init {
        require(capacity > 0)
    }

    fun reserve(eventKey: String): ReservationResult = synchronized(lock) {
        if (eventKey.isBlank()) return@synchronized ReservationResult.Invalid
        if (completed.containsKey(eventKey) || inFlight.containsKey(eventKey)) {
            return@synchronized ReservationResult.Duplicate
        }
        Reservation(eventKey).also { reservation -> inFlight[eventKey] = reservation }
            .let(ReservationResult::Reserved)
    }

    private fun finish(reservation: Reservation, accepted: Boolean) = synchronized(lock) {
        if (inFlight[reservation.eventKey] !== reservation) return@synchronized
        inFlight.remove(reservation.eventKey)
        if (!accepted) return@synchronized

        completed[reservation.eventKey] = Unit
        while (completed.size > capacity) {
            val oldest = completed.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
    }

    internal sealed interface ReservationResult {
        data class Reserved(val reservation: QqEventDeduplicator.Reservation) : ReservationResult

        data object Duplicate : ReservationResult

        data object Invalid : ReservationResult
    }

    internal inner class Reservation internal constructor(internal val eventKey: String) {
        private var finished = false

        fun complete() = finish(accepted = true)

        fun release() = finish(accepted = false)

        private fun finish(accepted: Boolean) {
            synchronized(this) {
                if (finished) return
                finished = true
            }
            this@QqEventDeduplicator.finish(this, accepted)
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4_096
    }
}
