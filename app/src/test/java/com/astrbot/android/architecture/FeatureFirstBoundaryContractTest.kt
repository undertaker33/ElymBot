package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFirstBoundaryContractTest {
    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val productionSourceRoots: List<Path> = listOf(
        "app/src/main/java/com/astrbot/android",
        "feature/bot/api/src/main/java/com/astrbot/android",
        "feature/bot/data/src/main/java/com/astrbot/android",
        "feature/bot/impl/src/main/java/com/astrbot/android",
        "feature/chat/api/src/main/java/com/astrbot/android",
        "feature/chat/impl/src/main/java/com/astrbot/android",
        "feature/chat/presentation/src/main/java/com/astrbot/android",
        "feature/chat/runtime/src/main/java/com/astrbot/android",
        "feature/config/api/src/main/java/com/astrbot/android",
        "feature/config/data/src/main/java/com/astrbot/android",
        "feature/config/impl/src/main/java/com/astrbot/android",
        "feature/conversation/api/src/main/java/com/astrbot/android",
        "feature/conversation/data/src/main/java/com/astrbot/android",
        "feature/cron/api/src/main/java/com/astrbot/android",
        "feature/cron/impl/src/main/java/com/astrbot/android",
        "feature/persona/api/src/main/java/com/astrbot/android",
        "feature/persona/data/src/main/java/com/astrbot/android",
        "feature/persona/impl/src/main/java/com/astrbot/android",
        "feature/plugin/api/src/main/java/com/astrbot/android",
        "feature/plugin/data/src/main/java/com/astrbot/android",
        "feature/plugin/presentation/src/main/java/com/astrbot/android",
        "feature/plugin/runtime/src/main/java/com/astrbot/android",
        "feature/provider/api/src/main/java/com/astrbot/android",
        "feature/provider/data/src/main/java/com/astrbot/android",
        "feature/provider/impl/src/main/java/com/astrbot/android",
        "feature/qq/api/src/main/java/com/astrbot/android",
        "feature/qq/data/src/main/java/com/astrbot/android",
        "feature/qq/impl/src/main/java/com/astrbot/android",
        "feature/qq/presentation/src/main/java/com/astrbot/android",
        "feature/qq/runtime/src/main/java/com/astrbot/android",
        "feature/resource/api/src/main/java/com/astrbot/android",
        "feature/resource/data/src/main/java/com/astrbot/android",
        "feature/resource/impl/src/main/java/com/astrbot/android",
    ).map(projectRoot::resolve).filter { root -> root.exists() }

    @Test
    fun feature_first_anchor_directories_exist() {
        val required = listOf(
            "feature/bot/data",
            "feature/chat/data",
            "feature/chat/presentation",
            "feature/chat/runtime",
            "feature/config/data",
            "feature/conversation/data",
            "feature/cron/data",
            "feature/persona/data",
            "feature/plugin/runtime",
            "feature/provider/data",
            "feature/qq/data",
            "feature/resource/data",
        )
        val missing = required.filterNot { relativePath ->
            productionSourceRoots.any { root -> root.resolve(relativePath).exists() }
        }
        assertTrue("Missing feature-first anchors: $missing", missing.isEmpty())
    }

    @Test
    fun feature_presentation_and_domain_stay_off_root_singletons() {
        val violations = kotlinFilesUnder("feature")
            .flatMap { file ->
                val relative = relativeProductionPath(file)
                val text = file.readText()
                when {
                    "/presentation/" in relative -> presentationForbiddenImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }

                    "/domain/" in relative -> domainForbiddenImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }

                    else -> emptyList()
                }
            }

        assertTrue(
            "Feature presentation/domain files must stay off root singleton imports: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_tree_must_not_reference_legacy_adapter_types() {
        val legacyAdapterPattern = Regex("""\bLegacy[A-Za-z0-9_]*Adapter\b""")
        val violations = kotlinFilesUnder("feature")
            .flatMap { file ->
                val relative = relativeProductionPath(file)
                legacyAdapterPattern.findAll(file.readText())
                    .map { match -> "$relative references ${match.value}" }
                    .toList()
            }

        assertTrue(
            "Feature production code must not reference legacy adapter types after phase 5: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun chat_runtime_and_presentation_must_not_import_forbidden_conversation_or_ui_layers() {
        val forbiddenByRoot = mapOf(
            "feature/chat/runtime" to listOf(
                "import com.astrbot.android.ui.viewmodel.",
                "import com.astrbot.android.feature.chat.presentation.",
                "import com.astrbot.android.feature.conversation.data.",
            ),
            "feature/chat/presentation" to listOf(
                "import com.astrbot.android.feature.conversation.data.",
                "import com.astrbot.android.feature.conversation.data.",
            ),
        )
        val violations = forbiddenByRoot.flatMap { (relativeRoot, forbiddenImports) ->
            kotlinFilesUnder(relativeRoot).flatMap { file ->
                val relative = relativeProductionPath(file)
                val text = file.readText()
                forbiddenImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        }

        assertTrue(
            "Chat runtime/presentation must stay behind conversation api and off presentation/data impl imports: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_v1_boundary_uses_semantic_dispatch_adapter_name() {
        val legacyFile = productionFileOrFallback("feature/plugin/runtime/PluginV1LegacyAdapter.kt")
        val semanticFile = productionFileOrFallback("feature/plugin/runtime/PluginV1DispatchAdapter.kt")
        val facadeFile = productionFileOrFallback("feature/plugin/runtime/PluginRuntimeFacade.kt")

        assertTrue(
            "PluginV1LegacyAdapter.kt must be removed from production after phase 5 cleanup",
            !legacyFile.exists(),
        )
        assertTrue(
            "PluginV1DispatchAdapter.kt must exist as the semantic V1 frozen boundary",
            semanticFile.exists(),
        )

        val boundaryText = semanticFile.readText()
        assertTrue(
            "PluginV1DispatchAdapter must still document the frozen V1 boundary",
            boundaryText.contains("frozen boundary", ignoreCase = true) ||
                boundaryText.contains("freeze", ignoreCase = true),
        )
        assertTrue(
            "PluginRuntimeFacade must not expose legacy adapter names",
            !facadeFile.readText().contains("PluginV1LegacyAdapter"),
        )
    }

    @Test
    fun production_code_does_not_wire_native_search_providers() {
        val violations = kotlinFilesUnder("")
            .filterNot { file ->
                relativeProductionPath(file).startsWith("core/runtime/search/native/")
            }
            .mapNotNull { file ->
                val relative = relativeProductionPath(file)
                if (file.readText().contains("com.astrbot.android.core.runtime.search.native")) {
                    "$relative imports native search provider"
                } else {
                    null
                }
            }

        assertTrue(
            "Production code must not wire model-native search providers: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        return productionSourceRoots.flatMap { sourceRoot ->
            val root = sourceRoot.resolve(relativeRoot)
            if (!root.exists()) {
                emptyList()
            } else {
                Files.walk(root).use { stream ->
                    stream
                        .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
        }
    }

    private fun relativeProductionPath(file: Path): String {
        val sourceRoot = productionSourceRoots.firstOrNull { root -> file.startsWith(root) }
            ?: error("File $file is not under configured production source roots")
        return sourceRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun productionFileOrFallback(relativePath: String): Path {
        return productionSourceRoots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { file -> file.exists() }
            ?: mainRoot.resolve(relativePath)
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private val presentationForbiddenImports = listOf(
        "import com.astrbot.android.data.",
        "import com.astrbot.android.runtime.",
    )

    private val domainForbiddenImports = listOf(
        "import android.",
        "import androidx.compose.",
        "import com.astrbot.android.data.",
        "import com.astrbot.android.runtime.",
    )
}

