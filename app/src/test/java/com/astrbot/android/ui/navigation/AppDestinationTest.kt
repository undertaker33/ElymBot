package com.astrbot.android.ui.navigation

import com.astrbot.android.ui.settings.ResourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {

    @Test
    fun `top level routes are stable`() {
        assertEquals("me", AppDestination.Me.route)
        assertEquals("resource-center", AppDestination.ResourceCenter.route)
        assertEquals("cron-jobs", AppDestination.CronJobs.route)
        assertEquals("settings-hub", AppDestination.SettingsHub.route)
        assertEquals("runtime", AppDestination.Runtime.route)
        assertEquals("asset-management", AppDestination.Assets.route)
        assertEquals("backup-hub", AppDestination.BackupHub.route)
    }

    @Test
    fun `resource center routes are stable`() {
        assertEquals("resource-center", AppDestination.ResourceCenter.route)
        assertEquals("resource-center/{resourceType}", AppDestination.ResourceList.route)
        assertEquals("resource-center/mcp", AppDestination.ResourceList.routeFor(ResourceKind.MCP))
        assertEquals("resource-center/skill", AppDestination.ResourceList.routeFor(ResourceKind.SKILL))
        assertEquals("resource-center/tool", AppDestination.ResourceList.routeFor(ResourceKind.TOOL))
    }
}
