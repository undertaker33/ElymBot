package com.astrbot.android.core.common.logging

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeLogCompatFacadePlacementTest {

    @Test
    fun legacy_runtime_log_facades_are_not_production_sources() {
        val productionCompatFiles = listOf(
            "core/runtime/src/main/java/com/astrbot/android/core/common/logging/AppLogger.kt",
            "core/runtime/src/main/java/com/astrbot/android/core/common/logging/RuntimeLogRepository.kt",
            "core/runtime/src/main/java/com/astrbot/android/core/common/logging/RuntimeLogCleanupRepository.kt",
        )

        val userDir = System.getProperty("user.dir") ?: "."
        val root = File(userDir).parentFile ?: File(userDir)

        productionCompatFiles.forEach { relativePath ->
            assertFalse(
                "$relativePath must stay out of production main; keep legacy runtime log facades test-only.",
                File(root, relativePath).exists(),
            )
        }
    }
}
