package com.astrbot.android.feature.cron.runtime

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class CronCompatContractTest {
    @Test
    fun production_sources_must_not_call_static_cron_scheduler_facade() {
        val root = projectRoot()
        val sourceRoot = root.resolve("feature/cron/runtime/src/main/java")

        val violations = sourceRoot.toFile()
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .mapNotNull { file ->
                val relativePath = root.relativize(file.toPath()).toString()
                relativePath.takeIf { file.readText().contains("CronJobScheduler.") }
            }
            .toList()

        assertTrue(
            "Cron runtime production sources must use injected scheduler instances, not the static CronJobScheduler facade: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_sources_must_not_keep_static_cron_scheduler_facade() {
        val root = projectRoot()
        val sourceRoot = root.resolve("feature/cron/runtime/src/main/java")

        val violations = sourceRoot.toFile()
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .mapNotNull { file ->
                val relativePath = root.relativize(file.toPath()).toString()
                relativePath.takeIf { "object CronJobScheduler" in file.readText() }
            }
            .toList()

        assertTrue(
            "Cron runtime production sources must not keep a static CronJobScheduler object: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun work_manager_scheduler_and_ports_are_injectable() {
        val root = projectRoot()
        val requiredConstructors = mapOf(
            "feature/cron/runtime/src/main/java/com/astrbot/android/feature/cron/runtime/CronJobScheduler.kt" to
                "class WorkManagerCronJobScheduler @Inject constructor",
            "feature/cron/runtime/src/main/java/com/astrbot/android/feature/cron/runtime/FeatureCronSchedulerPortAdapter.kt" to
                "class FeatureCronSchedulerPortAdapter @Inject constructor",
            "feature/cron/runtime/src/main/java/com/astrbot/android/feature/cron/runtime/WorkManagerCronRescheduler.kt" to
                "class WorkManagerCronRescheduler @Inject constructor",
        )

        val missing = requiredConstructors.filter { (relativePath, expectedConstructor) ->
            expectedConstructor !in root.resolve(relativePath).readText()
        }

        assertTrue(
            "Cron runtime scheduler path must stay Hilt-injectable: ${missing.keys}",
            missing.isEmpty(),
        )
    }

    private fun projectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return generateSequence(cwd) { it.parent }
            .firstOrNull { it.resolve("settings.gradle.kts").exists() }
            ?: error("Unable to resolve project root from $cwd")
    }
}
