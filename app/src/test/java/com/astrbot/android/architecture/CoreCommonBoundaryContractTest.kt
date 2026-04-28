package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCommonBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val coreCommonRoot: Path =
        projectRoot.resolve("core/common/src/main/java/com/astrbot/android/core/common")

    @Test
    fun core_common_must_remain_stateless_and_platform_free() {
        val forbiddenTokens = listOf(
            "import android.",
            "Context",
            "@Volatile",
            "MutableStateFlow",
            "MutableSharedFlow",
            "CoroutineScope",
            "SupervisorJob",
            "RuntimeLogRepository",
            "graphInstance",
            "delegate",
        )

        val violations = kotlinFilesUnder(coreCommonRoot)
            .filter { file ->
                val text = file.readText(UTF_8)
                forbiddenTokens.any { token -> text.contains(token) }
            }
            .map { file -> projectRoot.relativize(file).toString().replace('\\', '/') }

        assertTrue(
            ":core:common must only hold low-coupling common code; keep platform/global-state debt in owner modules. Found: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
