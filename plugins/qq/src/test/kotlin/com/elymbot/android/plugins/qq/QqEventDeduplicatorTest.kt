package com.elymbot.android.plugins.qq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqEventDeduplicatorTest {
    @Test
    fun duplicateReservationCompletesOnlyAfterAcceptanceAndMemoryStaysBounded() {
        val deduplicator = QqEventDeduplicator(capacity = 2)

        val first = reserved(deduplicator, "qq:1000:1")
        assertEquals(QqEventDeduplicator.ReservationResult.Duplicate, deduplicator.reserve("qq:1000:1"))
        first.complete()
        assertEquals(QqEventDeduplicator.ReservationResult.Duplicate, deduplicator.reserve("qq:1000:1"))

        reserved(deduplicator, "qq:1000:2").complete()
        reserved(deduplicator, "qq:1000:3").complete()
        reserved(deduplicator, "qq:1000:1").release()
        assertEquals(QqEventDeduplicator.ReservationResult.Invalid, deduplicator.reserve(" "))
    }

    @Test
    fun releasedReservationCanBeRetried() {
        val deduplicator = QqEventDeduplicator()

        reserved(deduplicator, "qq:1000:1").release()

        assertTrue(deduplicator.reserve("qq:1000:1") is QqEventDeduplicator.ReservationResult.Reserved)
    }

    private fun reserved(deduplicator: QqEventDeduplicator, eventKey: String) =
        (deduplicator.reserve(eventKey) as QqEventDeduplicator.ReservationResult.Reserved).reservation
}
