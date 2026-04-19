package com.astrbot.android

import com.astrbot.android.model.plugin.PluginInstallIntent
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPluginDeepLinkTest {
    @Test
    fun `repository deep link parses into pending repository request`() {
        val request = parsePluginDeepLinkInstallRequest(
            "astrbot://plugin/repository?url=https%3A%2F%2Frepo.example.com%2Fcatalog.json",
        )

        assertEquals(
            PluginDeepLinkInstallRequest(
                action = PluginDeepLinkAction.Repository,
                url = "https://repo.example.com/catalog.json",
                intent = PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            ),
            request,
        )
    }

    @Test
    fun `install deep link parses into pending direct package request`() {
        val request = parsePluginDeepLinkInstallRequest(
            "astrbot://plugin/install?url=https%3A%2F%2Fplugins.example.com%2Fdemo.zip",
        )

        assertEquals(
            PluginDeepLinkInstallRequest(
                action = PluginDeepLinkAction.DirectPackage,
                url = "https://plugins.example.com/demo.zip",
                intent = PluginInstallIntent.directPackageUrl("https://plugins.example.com/demo.zip"),
            ),
            request,
        )
    }

    @Test
    fun `plugin deep link rejects non https urls`() {
        assertNull(
            parsePluginDeepLinkInstallRequest(
                "astrbot://plugin/install?url=http%3A%2F%2Fplugins.example.com%2Fdemo.zip",
            ),
        )
    }

    @Test
    fun `unsupported deep link returns null`() {
        assertNull(parsePluginDeepLinkInstallRequest("https://example.com/plugin/install?url=x"))
        assertNull(parsePluginDeepLinkInstallRequest("astrbot://plugin/update?url=https://example.com/x.zip"))
    }

    @Test
    fun `legacy parsePluginInstallIntentFromDeepLink still works for backward compatibility`() {
        val intent = parsePluginInstallIntentFromDeepLink(
            "astrbot://plugin/repository?url=https%3A%2F%2Frepo.example.com%2Fcatalog.json",
        )
        assertEquals(
            PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            intent,
        )
    }

    @Test
    fun `legacy parsePluginInstallIntentFromDeepLink rejects http urls`() {
        assertNull(
            parsePluginInstallIntentFromDeepLink(
                "astrbot://plugin/install?url=http%3A%2F%2Fplugins.example.com%2Fdemo.zip",
            ),
        )
    }

    @Test
    fun `handle plugin deep link must only enqueue pending confirmation`() {
        val source = java.nio.file.Path.of("app/src/main/java/com/astrbot/android/MainActivity.kt")
            .takeIf { java.nio.file.Files.exists(it) }
            ?: java.nio.file.Path.of("src/main/java/com/astrbot/android/MainActivity.kt")
        val mainActivity = source.readText()
        val handleFunction = mainActivity.substringAfter("private fun handlePluginDeepLink")
            .substringBefore("\n    private fun ")

        assertTrue(
            "handlePluginDeepLink must not directly execute PluginInstallIntentHandler.handle; confirmation dialog should do it",
            !handleFunction.contains("pluginInstallIntentHandler.handle"),
        )
    }
}
