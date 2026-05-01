package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSearchBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val runtimeSearchBuildFile: Path = projectRoot.resolve("core/runtime-search/build.gradle.kts")

    @Test
    fun runtime_search_module_must_be_registered_and_reported() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)

        assertTrue(
            "8-C must register :core:runtime-search in settings.gradle.kts.",
            settingsText.contains("""include(":core:runtime-search")"""),
        )
        assertTrue(
            "Architecture source roots must include core/runtime-search/src/main/java.",
            rootBuildText.contains("core/runtime-search/src/main/java"),
        )
        assertTrue(
            ":core:runtime-search build file must exist.",
            runtimeSearchBuildFile.exists(),
        )
    }

    @Test
    fun runtime_search_module_must_not_depend_on_app_runtime_or_feature_modules() {
        assertTrue(
            ":core:runtime-search build file must exist before checking dependencies.",
            runtimeSearchBuildFile.exists(),
        )

        val text = runtimeSearchBuildFile.readText(UTF_8)
        val forbiddenDependencies = listOf(
            """:app"""",
            """:app-integration"""",
            """:core:runtime"""",
            """:feature:""",
        )
        val violations = forbiddenDependencies.filter(text::contains)

        assertTrue(
            ":core:runtime-search must not depend on app, app-integration, :core:runtime, or feature modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_search_top_level_contracts_must_live_in_runtime_search_module() {
        val movedFiles = listOf(
            "SearchNaturalLanguageParser.kt",
            "SearchProvider.kt",
            "UnifiedSearchContracts.kt",
            "UnifiedSearchCoordinator.kt",
            "WebSearchPromptGuidance.kt",
            "WebSearchTriggerRules.kt",
        )

        val oldRoot = projectRoot.resolve(
            "core/runtime/src/main/java/com/astrbot/android/core/runtime/search",
        )
        val newRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )

        val stillInRuntime = movedFiles.filter { fileName ->
            oldRoot.resolve(fileName).exists()
        }
        val missingFromRuntimeSearch = movedFiles.filterNot { fileName ->
            newRoot.resolve(fileName).exists()
        }

        assertTrue(
            "8-C/8-F top-level search contracts must leave coarse :core:runtime: $stillInRuntime",
            stillInRuntime.isEmpty(),
        )
        assertTrue(
            "8-C/8-F top-level search contracts must live in :core:runtime-search: $missingFromRuntimeSearch",
            missingFromRuntimeSearch.isEmpty(),
        )
    }

    @Test
    fun runtime_search_api_and_profile_must_live_in_runtime_search_module_after_8d() {
        val movedFiles = listOf(
            "api/BaiduAiSearchProvider.kt",
            "api/BoChaSearchProvider.kt",
            "api/BraveSearchProvider.kt",
            "api/TavilySearchProvider.kt",
            "profile/ConfiguredSearchProvider.kt",
            "profile/ProfileSearchJson.kt",
        )

        val oldRoot = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/core/runtime/search",
        )
        val newRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )

        val stillInApp = movedFiles.filter { relativePath ->
            oldRoot.resolve(relativePath).exists()
        }
        val missingFromRuntimeSearch = movedFiles.filterNot { relativePath ->
            newRoot.resolve(relativePath).exists()
        }

        assertTrue(
            "8-D api/profile search providers must leave app-held runtime search residue: $stillInApp",
            stillInApp.isEmpty(),
        )
        assertTrue(
            "8-D api/profile search providers must live in :core:runtime-search: $missingFromRuntimeSearch",
            missingFromRuntimeSearch.isEmpty(),
        )
    }

    @Test
    fun runtime_search_local_models_policy_relevance_merger_and_parsers_must_live_in_runtime_search_module_after_8e1() {
        val movedFiles = listOf(
            "local/LocalSearchModels.kt",
            "local/LocalSearchPolicy.kt",
            "local/LocalSearchRelevance.kt",
            "local/LocalSearchResultMerger.kt",
            "local/parser/BaiduWebParser.kt",
            "local/parser/BingNewsResultParser.kt",
            "local/parser/BingResultParser.kt",
            "local/parser/DuckDuckGoLiteParser.kt",
            "local/parser/HtmlParserUtils.kt",
            "local/parser/SogouResultParser.kt",
        )

        val oldRoot = projectRoot.resolve(
            "core/runtime/src/main/java/com/astrbot/android/core/runtime/search",
        )
        val newRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )

        val stillInRuntime = movedFiles.filter { relativePath ->
            oldRoot.resolve(relativePath).exists()
        }
        val missingFromRuntimeSearch = movedFiles.filterNot { relativePath ->
            newRoot.resolve(relativePath).exists()
        }

        assertTrue(
            "8-E1 local model/policy/relevance/merger/parser files must leave coarse :core:runtime: $stillInRuntime",
            stillInRuntime.isEmpty(),
        )
        assertTrue(
            "8-E1 local model/policy/relevance/merger/parser files must live in :core:runtime-search: $missingFromRuntimeSearch",
            missingFromRuntimeSearch.isEmpty(),
        )
    }

    @Test
    fun runtime_search_module_must_not_import_feature_app_models_android_strings_or_android_provider() {
        val runtimeSearchRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )
        if (!runtimeSearchRoot.exists()) {
            return
        }

        val forbiddenTokens = listOf(
            "import com.astrbot.android.model",
            "import com.astrbot.android.feature",
            "import com.astrbot.android.R",
            "import com.astrbot.android.AppStrings",
            "AppStrings.",
            "AndroidWebSearchPromptStringProvider",
        )
        val violations = kotlinFilesUnder(runtimeSearchRoot).flatMap { file ->
            val text = file.readText(UTF_8)
            forbiddenTokens
                .filter(text::contains)
                .map { token -> "${projectRoot.relativize(file).toString().replace('\\', '/')}:$token" }
        }

        assertTrue(
            ":core:runtime-search must own search contracts without importing app model, feature, R, AppStrings, or Android prompt providers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_search_module_must_not_import_app_logger_after_8e2() {
        val runtimeSearchRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )
        if (!runtimeSearchRoot.exists()) {
            return
        }

        val violations = kotlinFilesUnder(runtimeSearchRoot).filter { file ->
            file.readText(UTF_8).contains("AppLogger")
        }.map { file ->
            projectRoot.relativize(file).toString().replace('\\', '/')
        }

        assertTrue(
            "8-E2 local search fallback must use injected RuntimeLogger, not AppLogger: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_search_local_registry_engine_crawl_and_provider_must_live_in_runtime_search_module_after_8e2() {
        val movedFiles = listOf(
            "local/LocalMetaSearchFallbackProvider.kt",
            "local/SearchEngineRegistry.kt",
            "local/engine/BaiduWebLiteEngineAdapter.kt",
            "local/engine/BingEngineAdapter.kt",
            "local/engine/BingNewsEngineAdapter.kt",
            "local/engine/DuckDuckGoLiteEngineAdapter.kt",
            "local/engine/EngineHttp.kt",
            "local/engine/SearchEngineAdapter.kt",
            "local/engine/SogouEngineAdapter.kt",
            "local/crawl/ContentFetchClient.kt",
            "local/crawl/CrawlModels.kt",
            "local/crawl/DefaultContentCrawlerLite.kt",
            "local/crawl/HtmlContentExtractor.kt",
            "local/crawl/MarkdownLiteGenerator.kt",
            "local/crawl/QueryAwareContentPruner.kt",
            "local/crawl/UrlSafetyPolicy.kt",
        )

        val oldRuntimeRoot = projectRoot.resolve(
            "core/runtime/src/main/java/com/astrbot/android/core/runtime/search",
        )
        val oldAppRoot = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/core/runtime/search",
        )
        val newRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )

        val stillInRuntime = movedFiles.filter { relativePath ->
            oldRuntimeRoot.resolve(relativePath).exists()
        }
        val stillInApp = movedFiles.filter { relativePath ->
            oldAppRoot.resolve(relativePath).exists()
        }
        val missingFromRuntimeSearch = movedFiles.filterNot { relativePath ->
            newRoot.resolve(relativePath).exists()
        }

        assertTrue(
            "8-E2 local registry/engine/crawl/provider files must leave coarse :core:runtime: $stillInRuntime",
            stillInRuntime.isEmpty(),
        )
        assertTrue(
            "8-E2 local registry/engine/crawl/provider files must leave app-held runtime search residue: $stillInApp",
            stillInApp.isEmpty(),
        )
        assertTrue(
            "8-E2 local registry/engine/crawl/provider files must live in :core:runtime-search: $missingFromRuntimeSearch",
            missingFromRuntimeSearch.isEmpty(),
        )
    }

    @Test
    fun runtime_search_prompt_guidance_must_split_pure_contract_from_android_string_adapter_after_8f() {
        val pureGuidanceFile = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search/WebSearchPromptGuidance.kt",
        )
        val oldAppMixedFile = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/core/runtime/search/WebSearchPromptGuidance.kt",
        )
        val androidAdapterFile = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/core/runtime/search/AndroidWebSearchPromptStringProvider.kt",
        )
        val runtimeSearchRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search",
        )

        assertTrue(
            "8-F pure WebSearchPromptGuidance contract must live in :core:runtime-search.",
            pureGuidanceFile.exists(),
        )
        assertFalse(
            "8-F app source must not keep the old mixed WebSearchPromptGuidance file with Android strings.",
            oldAppMixedFile.exists(),
        )
        assertTrue(
            "8-F Android string implementation must stay in app as an explicit adapter.",
            androidAdapterFile.exists(),
        )

        val androidProviderInRuntimeSearch = kotlinFilesUnder(runtimeSearchRoot)
            .filter { file -> file.readText(UTF_8).contains("AndroidWebSearchPromptStringProvider") }
            .map { file -> projectRoot.relativize(file).toString().replace('\\', '/') }

        assertTrue(
            ":core:runtime-search must not own AndroidWebSearchPromptStringProvider: $androidProviderInRuntimeSearch",
            androidProviderInRuntimeSearch.isEmpty(),
        )

        val adapterText = androidAdapterFile.readText(UTF_8)
        assertTrue(
            "App Android prompt adapter must implement the core WebSearchPromptStringProvider contract.",
            adapterText.contains("class AndroidWebSearchPromptStringProvider : WebSearchPromptStringProvider"),
        )
        assertTrue(
            "App Android prompt adapter is the only allowed place for AppStrings/R backed web-search prompt strings.",
            adapterText.contains("AppStrings") && adapterText.contains("R.string.web_search_prompt"),
        )
    }

    @Test
    fun app_search_residue_may_only_keep_android_prompt_string_adapter_after_8f() {
        val appSearchRoot = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/core/runtime/search",
        )
        if (!appSearchRoot.exists()) {
            return
        }

        val allowed = setOf("AndroidWebSearchPromptStringProvider.kt")
        val unexpectedFiles = kotlinFilesUnder(appSearchRoot)
            .map { file -> appSearchRoot.relativize(file).toString().replace('\\', '/') }
            .filterNot { relativePath ->
                relativePath in allowed ||
                    relativePath.startsWith("api/") ||
                    relativePath.startsWith("local/") ||
                    relativePath.startsWith("profile/")
            }

        assertTrue(
            "8-F app-held core/runtime/search residue may only keep the Android prompt string adapter: $unexpectedFiles",
            unexpectedFiles.isEmpty(),
        )
    }

    @Test
    fun runtime_search_module_must_not_take_runtime_context_package_in_8e1() {
        val runtimeSearchSourceRoot = projectRoot.resolve(
            "core/runtime-search/src/main/java",
        )
        if (!runtimeSearchSourceRoot.exists()) {
            return
        }

        val contextViolations = kotlinFilesUnder(runtimeSearchSourceRoot)
            .mapNotNull { file ->
                val relativePath = projectRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText(UTF_8)
                val hasContextPath = relativePath.contains("/core/runtime/context/")
                val hasContextPackage = text.contains("package com.astrbot.android.core.runtime.context")
                if (hasContextPath || hasContextPackage) relativePath else null
            }

        assertTrue(
            "8-E1 must not move runtime context into :core:runtime-search; context has a separate owner/slice: $contextViolations",
            contextViolations.isEmpty(),
        )
    }

    @Test
    fun coarse_runtime_may_only_keep_deferred_search_files_after_8e2() {
        val runtimeSearchRoot = projectRoot.resolve(
            "core/runtime/src/main/java/com/astrbot/android/core/runtime/search",
        )
        if (!runtimeSearchRoot.exists()) {
            return
        }

        val movedRelativePaths = setOf(
            "local/LocalSearchModels.kt",
            "local/LocalSearchPolicy.kt",
            "local/LocalSearchRelevance.kt",
            "local/LocalSearchResultMerger.kt",
            "local/parser/BaiduWebParser.kt",
            "local/parser/BingNewsResultParser.kt",
            "local/parser/BingResultParser.kt",
            "local/parser/DuckDuckGoLiteParser.kt",
            "local/parser/HtmlParserUtils.kt",
            "local/parser/SogouResultParser.kt",
            "local/SearchEngineRegistry.kt",
            "local/engine/BaiduWebLiteEngineAdapter.kt",
            "local/engine/BingEngineAdapter.kt",
            "local/engine/BingNewsEngineAdapter.kt",
            "local/engine/DuckDuckGoLiteEngineAdapter.kt",
            "local/engine/EngineHttp.kt",
            "local/engine/SearchEngineAdapter.kt",
            "local/engine/SogouEngineAdapter.kt",
            "local/crawl/ContentFetchClient.kt",
            "local/crawl/CrawlModels.kt",
            "local/crawl/DefaultContentCrawlerLite.kt",
            "local/crawl/HtmlContentExtractor.kt",
            "local/crawl/MarkdownLiteGenerator.kt",
            "local/crawl/QueryAwareContentPruner.kt",
            "local/crawl/UrlSafetyPolicy.kt",
            "SearchNaturalLanguageParser.kt",
            "SearchProvider.kt",
            "UnifiedSearchContracts.kt",
            "UnifiedSearchCoordinator.kt",
            "WebSearchPromptGuidance.kt",
            "WebSearchTriggerRules.kt",
        )

        val unexpectedFiles = kotlinFilesUnder(runtimeSearchRoot)
            .map { file -> runtimeSearchRoot.relativize(file).toString().replace('\\', '/') }
            .filter { relativePath -> relativePath in movedRelativePaths }

        assertTrue(
            "After 8-E2, coarse :core:runtime may keep only deferred search files, not moved 8-C/8-E1/8-E2 files: $unexpectedFiles",
            unexpectedFiles.isEmpty(),
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
