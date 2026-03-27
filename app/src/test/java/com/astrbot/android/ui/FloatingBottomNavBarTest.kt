package com.astrbot.android.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBottomNavBarTest {

    @Test
    fun `shows floating bottom nav on top level routes without ime overlap`() {
        assertEquals(true, shouldShowFloatingBottomNav(AppDestination.Me.route, imeVisible = false))
        assertEquals(true, shouldShowFloatingBottomNav(AppDestination.Chat.route, imeVisible = false))
    }

    @Test
    fun `hides floating bottom nav when no top level route or chat ime is visible`() {
        assertEquals(false, shouldShowFloatingBottomNav(null, imeVisible = false))
        assertEquals(false, shouldShowFloatingBottomNav(AppDestination.Chat.route, imeVisible = true))
    }

    @Test
    fun `route container does not reserve extra bottom padding for floating bottom nav`() {
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Me.route, visible = true))
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Chat.route, visible = false))
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Chat.route, visible = true))
    }

    @Test
    fun `chat input area still reserves space for floating bottom nav`() {
        assertEquals(0.dp, chatBottomBarPadding(visible = false))
        assertEquals(FloatingBottomNavReservedPadding, chatBottomBarPadding(visible = true))
    }
}
