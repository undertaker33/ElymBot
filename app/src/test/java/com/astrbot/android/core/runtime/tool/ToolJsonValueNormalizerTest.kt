package com.astrbot.android.core.runtime.tool

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolJsonValueNormalizerTest {

    private val projectRoot: Path = detectProjectRoot()
    private val sourceRoots: List<Path> = listOf(
        projectRoot.resolve("app/src/main/java/com/astrbot/android"),
        projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android"),
    )

    @Test
    fun shared_json_normalizer_source_must_exist_and_replace_local_recursive_helpers() {
        val helperFile = productionFile("core/runtime/tool/ToolJsonValueNormalizer.kt")
        assertTrue(
            "Shared tool JSON normalizer must exist at ${helperFile.toAbsolutePath()}",
            helperFile.exists(),
        )

        val runtimeResourceProjections = productionFile("core/runtime/context/RuntimeResourceProjections.kt").readText()
        val futureToolSourceContracts = productionFile("feature/plugin/runtime/toolsource/FutureToolSourceContracts.kt").readText()

        assertTrue(
            "RuntimeResourceProjections must stop declaring its own recursive JSON normalization helpers.",
            !runtimeResourceProjections.contains("private fun JSONObject?.toJsonLikeMap()") &&
                !runtimeResourceProjections.contains("private fun JSONArray.toJsonLikeList()") &&
                !runtimeResourceProjections.contains("private fun normalizeJsonValue("),
        )
        assertTrue(
            "FutureToolSourceContracts must stop declaring its own recursive JSON normalization helpers.",
            !futureToolSourceContracts.contains("private fun JSONObject.toJsonLikeMap()") &&
                !futureToolSourceContracts.contains("private fun JSONArray.toJsonLikeList()") &&
                !futureToolSourceContracts.contains("private fun Any?.toJsonLikeValue()"),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private fun productionFile(relativePath: String): Path {
        return sourceRoots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { path -> path.exists() }
            ?: error("Missing production file: $relativePath")
    }
}
