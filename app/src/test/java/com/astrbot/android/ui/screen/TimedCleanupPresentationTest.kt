package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedCleanupPresentationTest {

    @Test
    fun format_timed_cleanup_interval_prefers_human_readable_output() {
        assertEquals("Every 2h 30m", formatTimedCleanupInterval(true, 2, 30))
        assertEquals("Every 45m", formatTimedCleanupInterval(true, 0, 45))
        assertEquals("Off", formatTimedCleanupInterval(false, 12, 0))
    }

    @Test
    fun `timed cleanup surfaces use unified monochrome controls`() {
        assertTrue(timedCleanupDialogUsesUnifiedMonochromeStyle())
        assertTrue(runtimeLogUsesUnifiedActionButtons())
        assertTrue(pluginRuntimeLogUsesUnifiedActionButtons())
    }
}
