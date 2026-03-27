package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSwipeMotionTest {
    @Test
    fun drag_right_from_closed_reveals_actions() {
        val offset = SessionSwipeMotion.applyDrag(
            currentOffset = 0f,
            dragAmount = 48f,
            revealWidth = 120f,
        )

        assertEquals(48f, offset, 0.001f)
    }

    @Test
    fun drag_left_from_opened_card_can_slide_back() {
        val offset = SessionSwipeMotion.applyDrag(
            currentOffset = 96f,
            dragAmount = -72f,
            revealWidth = 120f,
        )

        assertEquals(24f, offset, 0.001f)
    }

    @Test
    fun settle_closes_when_open_distance_is_below_threshold() {
        val offset = SessionSwipeMotion.settleOffset(
            currentOffset = 30f,
            revealWidth = 120f,
        )

        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun settle_keeps_open_when_dragged_far_enough() {
        val offset = SessionSwipeMotion.settleOffset(
            currentOffset = 64f,
            revealWidth = 120f,
        )

        assertEquals(120f, offset, 0.001f)
    }
}
