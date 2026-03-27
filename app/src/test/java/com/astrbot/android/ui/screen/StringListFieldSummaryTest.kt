package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class StringListFieldSummaryTest {
    @Test
    fun empty_values_have_zero_count_and_no_preview() {
        val summary = StringListFieldSummary.from(emptyList())

        assertEquals(0, summary.count)
        assertEquals(emptyList<String>(), summary.previewValues)
    }

    @Test
    fun preview_is_trimmed_and_limited_to_two_items() {
        val summary = StringListFieldSummary.from(
            listOf(" 10001 ", "", "10002", "10003"),
        )

        assertEquals(3, summary.count)
        assertEquals(listOf("10001", "10002"), summary.previewValues)
    }
}
