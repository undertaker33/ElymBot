package com.astrbot.android.feature.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureRepositoryFacadePlacementTest {
    @Test
    fun bot_and_config_repositories_do_not_expose_production_singleton_facades() {
        val productionSources = listOf(
            "feature/bot/data/src/main/java/com/astrbot/android/feature/bot/data/FeatureBotRepository.kt" to "FeatureBotRepository",
            "feature/config/data/src/main/java/com/astrbot/android/feature/config/data/FeatureConfigRepository.kt" to "FeatureConfigRepository",
        )

        productionSources.forEach { (relativePath, facadeName) ->
            val source = projectRoot().resolve(relativePath).readText()
            assertFalse(
                "$facadeName production main must expose Store/Port only; keep legacy facade in test source.",
                Regex("""\bobject\s+$facadeName\b""").containsMatchIn(source),
            )
        }
    }

    private fun projectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!current.resolve("settings.gradle.kts").isFile) {
            current = current.parentFile ?: break
        }
        return current
    }
}
