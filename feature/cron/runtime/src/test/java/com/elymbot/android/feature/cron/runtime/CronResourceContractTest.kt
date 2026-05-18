package com.elymbot.android.feature.cron.runtime

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronResourceContractTest {
    private val projectRoot = detectProjectRoot()

    @Test
    fun cron_and_runtime_prompt_resources_keep_default_and_zh_keys_in_sync() {
        listOf("cron_strings.xml", "runtime_prompt_strings.xml").forEach { fileName ->
            val defaultKeys = resourceKeys(projectRoot.resolve("app/src/main/res/values/$fileName"))
            val zhKeys = resourceKeys(projectRoot.resolve("app/src/main/res/values-zh/$fileName"))

            assertTrue("Expected $fileName to declare strings.", defaultKeys.isNotEmpty())
            assertEquals("values and values-zh keys must match for $fileName", defaultKeys, zhKeys)
        }
    }

    @Test
    fun active_capability_runtime_code_keeps_large_user_facing_prompts_in_resources() {
        val provider = productionFile(
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ActiveCapabilityToolSourceProvider.kt",
        ).readText()
        val fallbackResponder = productionFile(
            "feature/cron/runtime/src/main/java/com/elymbot/android/feature/cron/runtime/ScheduledTaskIntentFallbackResponder.kt",
        ).readText()

        assertFalse(provider.contains("Required for creating reminders, timers, timed follow-ups"))
        assertFalse(provider.contains("Run a scheduled future task immediately through the injected scheduled task runner."))
        assertFalse(fallbackResponder.contains("Tell the user naturally that the reminder was created."))
        assertFalse(fallbackResponder.contains("Do not call create_future_task again."))
    }

    private fun resourceKeys(file: File): Set<String> {
        assertTrue("Missing resource file: ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val keys = linkedSetOf<String>()
        listOf("string", "string-array").forEach { tag ->
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (!name.isNullOrBlank()) keys += name
            }
        }
        return keys
    }

    private fun productionFile(relativePath: String): File =
        projectRoot.resolve(relativePath)
            .takeIf { file -> file.exists() }
            ?: error("Missing production file: $relativePath")

    private fun detectProjectRoot(): File {
        var current = File("").absoluteFile
        while (current.parentFile != null) {
            if (current.resolve("settings.gradle.kts").exists() ||
                current.resolve("settings.gradle").exists()
            ) {
                return current
            }
            current = current.parentFile
        }
        error("Unable to locate project root from ${File("").absolutePath}")
    }
}
