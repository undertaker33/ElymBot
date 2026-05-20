package com.elymbot.android.ui.app

import com.elymbot.android.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalTopBarTest {

    @Test
    fun `cron jobs route has secondary top bar fallback`() {
        val spec = fallbackSecondaryTopBarSpecForRoute(
            route = AppDestination.CronJobs.route,
            strings = testTopBarStrings(),
        )

        assertTrue(spec is SecondaryTopBarSpec.SubPage)
        assertEquals("Scheduled Tasks", (spec as SecondaryTopBarSpec.SubPage).title)
    }

    @Test
    fun `backup module routes use module titles for secondary top bar fallback`() {
        val strings = testTopBarStrings()

        val modelSpec = fallbackSecondaryTopBarSpecForRoute(
            route = AppDestination.ModelBackup.route,
            strings = strings,
        )
        val fullSpec = fallbackSecondaryTopBarSpecForRoute(
            route = AppDestination.FullBackup.route,
            strings = strings,
        )

        assertEquals("Model Backup", (modelSpec as SecondaryTopBarSpec.SubPage).title)
        assertEquals("Full Backup", (fullSpec as SecondaryTopBarSpec.SubPage).title)
    }

    private fun testTopBarStrings(): SecondaryTopBarStrings {
        return SecondaryTopBarStrings(
            config = "Config",
            logs = "Logs",
            qqAccount = "QQ",
            qqLogin = "Login",
            settings = "Settings",
            assetManagement = "Assets",
            pluginDetail = "Plugin",
            pluginWorkspace = "Workspace",
            pluginConfig = "Plugin Config",
            models = "Models",
            runtime = "Runtime",
            dataBackup = "Backup",
            backupBots = "Bot Backup",
            backupModels = "Model Backup",
            backupPersonas = "Persona Backup",
            backupConversations = "Conversation Backup",
            backupConfigs = "Config Backup",
            backupTts = "TTS Backup",
            backupFull = "Full Backup",
            cronJobs = "Scheduled Tasks",
            configDetailDefaultSection = "Basic",
        )
    }
}
