package com.astrbot.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QQLoginPollingTrackerTest {
    @Test
    fun `first visible qq screen starts polling`() {
        val tracker = QQLoginPollingTracker()

        assertEquals(
            QQLoginPollingTransition.START,
            tracker.onScreenVisible("qq-account"),
        )
        assertEquals(1, tracker.activeScreenCount())
        assertEquals("qq-account", tracker.activeScreenSummary())
    }

    @Test
    fun `second visible qq screen keeps a single poller running`() {
        val tracker = QQLoginPollingTracker()

        tracker.onScreenVisible("qq-account")

        assertEquals(
            QQLoginPollingTransition.KEEP_RUNNING,
            tracker.onScreenVisible("qq-login"),
        )
        assertEquals(2, tracker.activeScreenCount())
        assertEquals("qq-account,qq-login", tracker.activeScreenSummary())
    }

    @Test
    fun `duplicate visible callback does not start polling twice`() {
        val tracker = QQLoginPollingTracker()

        tracker.onScreenVisible("qq-login")

        assertEquals(
            QQLoginPollingTransition.NO_CHANGE,
            tracker.onScreenVisible("qq-login"),
        )
        assertEquals(1, tracker.activeScreenCount())
    }

    @Test
    fun `polling stops only after the last visible qq screen hides`() {
        val tracker = QQLoginPollingTracker()

        tracker.onScreenVisible("qq-account")
        tracker.onScreenVisible("qq-login")

        assertEquals(
            QQLoginPollingTransition.KEEP_RUNNING,
            tracker.onScreenHidden("qq-account"),
        )
        assertEquals(1, tracker.activeScreenCount())
        assertEquals(
            QQLoginPollingTransition.STOP,
            tracker.onScreenHidden("qq-login"),
        )
        assertEquals(0, tracker.activeScreenCount())
        assertEquals("", tracker.activeScreenSummary())
    }

    @Test
    fun `version marker includes build metadata and rollout marker`() {
        val marker = buildQqLoginVersionMarker(
            versionName = "0.3.4",
            versionCode = 14,
        )

        assertTrue(marker.contains("QQ login diagnostics build"))
        assertTrue(marker.contains("versionName=0.3.4"))
        assertTrue(marker.contains("versionCode=14"))
        assertTrue(marker.contains("marker=qq-login-diag-v3-poll-singleton"))
    }
}
