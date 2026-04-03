package com.astrbot.android

import com.astrbot.android.model.plugin.PluginInstallIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityPluginDeepLinkTest {
    @Test
    fun `repository deep link parses into repository install intent`() {
        val intent = parsePluginInstallIntentFromDeepLink(
            "astrbot://plugin/repository?url=https%3A%2F%2Frepo.example.com%2Fcatalog.json",
        )

        assertEquals(
            PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            intent,
        )
    }

    @Test
    fun `install deep link parses into direct package install intent`() {
        val intent = parsePluginInstallIntentFromDeepLink(
            "astrbot://plugin/install?url=https%3A%2F%2Fplugins.example.com%2Fdemo.zip",
        )

        assertEquals(
            PluginInstallIntent.directPackageUrl("https://plugins.example.com/demo.zip"),
            intent,
        )
    }

    @Test
    fun `unsupported deep link returns null`() {
        assertNull(parsePluginInstallIntentFromDeepLink("https://example.com/plugin/install?url=x"))
        assertNull(parsePluginInstallIntentFromDeepLink("astrbot://plugin/update?url=https://example.com/x.zip"))
    }
}
