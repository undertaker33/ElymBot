package com.astrbot.android.ui

import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSwipeRailStateTest {

    @Test
    fun `main swipe pages follow the agreed eight page order`() {
        assertEquals(
            listOf(
                MainSwipePage.BOTS,
                MainSwipePage.MODELS,
                MainSwipePage.PERSONAS,
                enumValueOf<MainSwipePage>("PLUGINS_LOCAL"),
                enumValueOf<MainSwipePage>("PLUGINS_MARKET"),
                MainSwipePage.CHAT,
                MainSwipePage.CONFIG,
                MainSwipePage.ME,
            ),
            MainSwipePage.entries.toList(),
        )
    }

    @Test
    fun `drag preview reveals adjacent page while keeping current page visible`() {
        val preview = MainSwipeRailPreviewState.drag(
            from = MainSwipePage.CHAT,
            deltaFraction = -0.42f,
        )

        assertEquals(MainSwipePage.CHAT, preview.currentPage)
        assertEquals(MainSwipePage.CONFIG, preview.adjacentPage)
        assertTrue(preview.currentPageVisibleFraction > 0f)
        assertTrue(preview.adjacentPageVisibleFraction > 0f)
    }

    @Test
    fun `dragging past rail edge applies resistance instead of crossing boundary`() {
        val resisted = applyMainSwipeRailDrag(
            current = MainSwipePage.BOTS,
            currentOffsetFraction = 0f,
            dragDeltaFraction = 0.4f,
        )

        assertTrue(resisted > 0f)
        assertTrue(resisted < 0.4f)
    }

    @Test
    fun `settle keeps page when threshold is not crossed`() {
        assertEquals(
            enumValueOf<MainSwipePage>("PLUGINS_LOCAL"),
            settleMainSwipePage(
                current = enumValueOf("PLUGINS_LOCAL"),
                deltaFraction = -0.18f,
                velocity = 0f,
            ),
        )
    }

    @Test
    fun `settle advances to adjacent page when drag crosses threshold`() {
        assertEquals(
            enumValueOf<MainSwipePage>("PLUGINS_MARKET"),
            settleMainSwipePage(
                current = enumValueOf("PLUGINS_LOCAL"),
                deltaFraction = -0.38f,
                velocity = 0f,
            ),
        )
    }

    @Test
    fun `plugin workspace tab maps plugins route to local and market rail pages`() {
        val pluginWorkspaceTabClass = Class.forName("com.astrbot.android.ui.screen.PluginWorkspaceTab")
        val localTab = java.lang.Enum.valueOf(pluginWorkspaceTabClass.asSubclass(Enum::class.java), "LOCAL")
        val marketTab = java.lang.Enum.valueOf(pluginWorkspaceTabClass.asSubclass(Enum::class.java), "MARKET")
        val currentMainSwipePage = mainSwipeRailStateMethod(
            "currentMainSwipePage",
            String::class.java,
            Class.forName("com.astrbot.android.ui.screen.BotWorkspaceTab"),
            pluginWorkspaceTabClass,
        )

        assertEquals(
            enumValueOf<MainSwipePage>("PLUGINS_LOCAL"),
            currentMainSwipePage.invoke(null, AppDestination.Plugins.route, botWorkspaceTab("BOTS"), localTab),
        )
        assertEquals(
            enumValueOf<MainSwipePage>("PLUGINS_MARKET"),
            currentMainSwipePage.invoke(null, AppDestination.Plugins.route, botWorkspaceTab("BOTS"), marketTab),
        )
    }

    @Test
    fun `plugin workspace pages map back to plugin workspace tabs`() {
        val pluginWorkspaceTabClass = Class.forName("com.astrbot.android.ui.screen.PluginWorkspaceTab")
        val pluginWorkspaceTabForMainSwipePage = mainSwipeRailStateMethod(
            "pluginWorkspaceTabForMainSwipePage",
            MainSwipePage::class.java,
        )

        val localResult = pluginWorkspaceTabForMainSwipePage.invoke(null, enumValueOf<MainSwipePage>("PLUGINS_LOCAL"))
        val marketResult = pluginWorkspaceTabForMainSwipePage.invoke(null, enumValueOf<MainSwipePage>("PLUGINS_MARKET"))

        assertNotNull(localResult)
        assertNotNull(marketResult)
        assertEquals("LOCAL", (localResult as Enum<*>).name)
        assertEquals("MARKET", (marketResult as Enum<*>).name)
        assertEquals(pluginWorkspaceTabClass, localResult.javaClass)
        assertEquals(pluginWorkspaceTabClass, marketResult.javaClass)
    }

    @Test
    fun `chat page is reachable but does not allow horizontal page switching`() {
        assertEquals(false, mainSwipeEnabledForPage(MainSwipePage.CHAT))
        assertEquals(true, mainSwipeEnabledForPage(enumValueOf("PLUGINS_LOCAL")))
        assertEquals(true, mainSwipeEnabledForPage(enumValueOf("PLUGINS_MARKET")))
        assertEquals(true, mainSwipeEnabledForPage(MainSwipePage.CONFIG))
    }

    @Test
    fun `chat drawer container is rendered only while chat page is current`() {
        assertEquals(false, shouldRenderChatDrawer(renderedPage = MainSwipePage.CHAT, currentPage = MainSwipePage.PLUGINS_LOCAL))
        assertEquals(true, shouldRenderChatDrawer(renderedPage = MainSwipePage.CHAT, currentPage = MainSwipePage.CHAT))
    }

    private fun botWorkspaceTab(name: String): Any {
        val botWorkspaceTabClass = Class.forName("com.astrbot.android.ui.screen.BotWorkspaceTab")
        return java.lang.Enum.valueOf(botWorkspaceTabClass.asSubclass(Enum::class.java), name)
    }

    private fun mainSwipeRailStateMethod(name: String, vararg parameterTypes: Class<*>): Method {
        return Class.forName("com.astrbot.android.ui.MainSwipeRailStateKt").getDeclaredMethod(name, *parameterTypes)
    }
}
