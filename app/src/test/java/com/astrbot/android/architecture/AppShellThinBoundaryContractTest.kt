package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellThinBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val appMainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun app_data_http_adapter_must_stay_out_of_app_shell() {
        val oldHttpRoot = appMainRoot.resolve("data/http")
        val remainingFiles = kotlinFilesUnder(oldHttpRoot)
            .map(::relativeAppPath)

        assertTrue(
            "HTTP adapter code belongs to :core:runtime; do not grow app shell data/http again: $remainingFiles",
            remainingFiles.isEmpty(),
        )
    }

    @Test
    fun app_root_data_files_must_remain_explicit_transition_debt() {
        val allowed = setOf(
            "data/AppPreferencesRepository.kt",
            "data/LegacyProfileImport.kt",
            "data/LegacyRuntimeStateImport.kt",
            "data/LegacyStructuredStateImport.kt",
            "data/RuntimeAssetRepository.kt",
            "data/RuntimeCacheRepository.kt",
            "data/db/SavedQqAccountMappers.kt",
            "data/db/download/DownloadTaskMappers.kt",
            "data/db/tts/TtsVoiceAssetMappers.kt",
        )

        val actual = kotlinFilesUnder(appMainRoot.resolve("data"))
            .map(::relativeAppPath)
            .toSet()

        val unexpected = actual - allowed
        assertTrue(
            "Task 12 app-thin boundary: new app/data production files must move to owner modules instead of app shell. Found: $unexpected",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun app_core_runtime_files_must_remain_explicit_transition_debt() {
        val allowed = setOf(
            "core/runtime/audio/AndroidSystemTtsBridge.kt",
            "core/runtime/audio/SherpaOnnxAssetManager.kt",
            "core/runtime/audio/SherpaOnnxBridge.kt",
            "core/runtime/audio/TencentSilkEncoder.kt",
            "core/runtime/audio/TtsVoiceAssetRepository.kt",
            "core/runtime/audio/TtsVoiceCatalog.kt",
            "core/runtime/audio/VoiceCloneService.kt",
            "core/runtime/container/BridgeCommandRunner.kt",
            "core/runtime/container/ContainerBridgeController.kt",
            "core/runtime/container/ContainerBridgeRuntimeSupport.kt",
            "core/runtime/container/ContainerBridgeService.kt",
            "core/runtime/container/ContainerBridgeStatePort.kt",
            "core/runtime/container/ContainerRuntimeInstaller.kt",
            "core/runtime/container/DebPayloadExtractor.kt",
            "core/runtime/container/NapCatContainerRuntime.kt",
            "core/runtime/container/RootfsExtractor.kt",
            "core/runtime/container/RootfsOverlayExtractor.kt",
            "core/runtime/context/PlatformRuntimeAdapter.kt",
            "core/runtime/context/PromptAssembler.kt",
            "core/runtime/context/ResolvedRuntimeContext.kt",
            "core/runtime/context/RuntimeContextDataPort.kt",
            "core/runtime/context/RuntimeContextResolver.kt",
            "core/runtime/context/RuntimeIngressEvent.kt",
            "core/runtime/context/RuntimeResourceProjections.kt",
            "core/runtime/context/StreamingModeResolver.kt",
            "core/runtime/context/SystemPromptBuilder.kt",
            "core/runtime/context/ToolSourceContext.kt",
            "core/runtime/llm/ChatCompletionService.kt",
            "core/runtime/llm/ChatCompletionServiceLlmClient.kt",
            "core/runtime/llm/HiltLlmProviderProbePort.kt",
            "core/runtime/llm/LlmClientPort.kt",
            "core/runtime/llm/LlmInvocationContracts.kt",
            "core/runtime/llm/LlmMediaService.kt",
            "core/runtime/llm/LlmProviderProbePort.kt",
            "core/runtime/search/WebSearchPromptGuidance.kt",
            "core/runtime/search/api/BaiduAiSearchProvider.kt",
            "core/runtime/search/api/BoChaSearchProvider.kt",
            "core/runtime/search/api/BraveSearchProvider.kt",
            "core/runtime/search/api/TavilySearchProvider.kt",
            "core/runtime/search/local/LocalMetaSearchFallbackProvider.kt",
            "core/runtime/search/profile/ConfiguredSearchProvider.kt",
            "core/runtime/search/profile/ProfileSearchJson.kt",
        )

        val actual = kotlinFilesUnder(appMainRoot.resolve("core/runtime"))
            .map(::relativeAppPath)
            .toSet()

        val unexpected = actual - allowed
        assertTrue(
            "Task 12 app-thin boundary: new runtime implementation must move to :core:runtime or owner modules, not app shell. Found: $unexpected",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun app_legacy_runtime_root_must_not_reappear() {
        val runtimeRoot = appMainRoot.resolve("runtime")

        assertTrue(
            "Root app/runtime has been retired; keep runtime implementation under owner modules: $runtimeRoot",
            !runtimeRoot.exists(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativeAppPath(file: Path): String {
        return appMainRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
