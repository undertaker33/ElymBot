package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSwipeSizingTest {
    @Test
    fun private_conversation_uses_narrower_actions_but_wider_total_reveal() {
        val groupReveal = SessionSwipeSizing.revealWidthDp(actionCount = 2)
        val privateReveal = SessionSwipeSizing.revealWidthDp(actionCount = 3)

        assertEquals(148, groupReveal)
        assertEquals(220, privateReveal)
        assertTrue(privateReveal > groupReveal)
    }

    @Test
    fun action_buttons_are_compact_enough_for_three_actions() {
        assertEquals(66, SessionSwipeSizing.actionWidthDp)
        assertEquals(6, SessionSwipeSizing.actionSpacingDp)
        assertEquals(10, SessionSwipeSizing.peekWidthDp)
    }
}
