package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformCommandSettingsUpdateResult
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqPlatformSettingsStoreTest {
    @Test
    fun legacyCommandFlagsMigrateAndSettingsUpdatesPreserveBothSnapshots() = runBlocking {
        val directory = Files.createTempDirectory("qq-settings-migration-test").toFile()
        try {
            File(directory, "qq-settings.json").writeText(
                """{"schemaVersion":1,"revision":7,"settings":{"pluginCommandsAdminOnlyEnabled":true,"replyWhenPermissionDenied":true}}""",
                Charsets.UTF_8,
            )
            val store = QqPlatformSettingsStore(directory)
            assertEquals(7, store.settings.value.revision)
            assertEquals(0, store.commandSettings.value.revision)
            assertEquals(true, store.commandSettings.value.settings.pluginCommandsAdminOnlyEnabled)
            assertEquals(true, store.commandSettings.value.settings.replyWhenPermissionDenied)

            val mainResult = store.updateSettings(
                expectedRevision = 7,
                settings = store.settings.value.settings.copy(whitelistEnabled = false),
            )
            assertTrue(mainResult is QqPlatformSettingsUpdateResult.Saved)

            val afterMainUpdate = QqPlatformSettingsStore(directory)
            assertEquals(false, afterMainUpdate.settings.value.settings.whitelistEnabled)
            assertEquals(0, afterMainUpdate.commandSettings.value.revision)
            assertEquals(true, afterMainUpdate.commandSettings.value.settings.pluginCommandsAdminOnlyEnabled)
            assertEquals(true, afterMainUpdate.commandSettings.value.settings.replyWhenPermissionDenied)

            val commandResult = afterMainUpdate.updateCommandSettings(
                expectedRevision = 0,
                settings = QqPlatformCommandSettings(),
            )
            assertTrue(commandResult is QqPlatformCommandSettingsUpdateResult.Saved)

            val reloaded = QqPlatformSettingsStore(directory)
            assertEquals(8, reloaded.settings.value.revision)
            assertEquals(1, reloaded.commandSettings.value.revision)
            assertEquals(false, reloaded.settings.value.settings.whitelistEnabled)
            assertEquals(false, reloaded.commandSettings.value.settings.pluginCommandsAdminOnlyEnabled)
            assertEquals(false, reloaded.commandSettings.value.settings.replyWhenPermissionDenied)
        } finally {
            directory.deleteRecursively()
        }
    }
}
