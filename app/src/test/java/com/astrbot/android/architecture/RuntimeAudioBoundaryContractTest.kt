package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeAudioBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appMainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val globalSingletonAllowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/global-singleton-allowlist.txt")
    private val runtimeAudioRoot: Path = projectRoot
        .resolve("core/runtime-audio/src/main/java/com/astrbot/android/core/runtime/audio")

    @Test
    fun core_runtime_audio_module_must_be_registered_and_reported() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)

        assertTrue(
            "settings.gradle.kts must declare :core:runtime-audio for phase 9-B.",
            settingsText.contains("""include(":core:runtime-audio")"""),
        )
        assertTrue(
            "app must depend on :core:runtime-audio while app-side adapters still live in :app.",
            appBuildText.contains("""implementation(project(":core:runtime-audio"))"""),
        )
        assertTrue(
            "architecture source root report must include core/runtime-audio/src/main/java.",
            rootBuildText.contains("core/runtime-audio/src/main/java"),
        )
        assertTrue(
            "core/runtime-audio/build.gradle.kts must exist.",
            projectRoot.resolve("core/runtime-audio/build.gradle.kts").exists(),
        )
    }

    @Test
    fun core_runtime_audio_must_own_common_audio_contracts() {
        val required = listOf(
            "AudioRuntimeContracts.kt",
            "OnDeviceTtsCatalog.kt",
            "TtsPromptFormatter.kt",
            "TtsStyleMappings.kt",
        ).map(runtimeAudioRoot::resolve)

        val missing = required.filterNot { it.exists() }
        assertTrue(
            "core:runtime-audio must own phase 9 audio contracts/common TTS helpers: $missing",
            missing.isEmpty(),
        )

        val oldCoarseRuntimeFiles = listOf(
            projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android/core/runtime/audio/OnDeviceTtsCatalog.kt"),
            projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android/core/runtime/audio/TtsPromptFormatter.kt"),
            projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android/core/runtime/audio/TtsStyleMappings.kt"),
        ).filter { it.exists() }
        assertTrue(
            "Coarse :core:runtime must no longer own audio common files: $oldCoarseRuntimeFiles",
            oldCoarseRuntimeFiles.isEmpty(),
        )
    }

    @Test
    fun core_runtime_audio_must_not_import_app_feature_or_business_models() {
        val forbiddenImports = listOf(
            "import com.astrbot.android.model.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.di.",
            "import com.astrbot.android.feature.",
            "import com.astrbot.android.core.db.",
        )
        val violations = kotlinFilesUnder(runtimeAudioRoot).flatMap { file ->
            val text = file.readText(UTF_8)
            forbiddenImports
                .filter(text::contains)
                .map { forbidden -> "${runtimeAudioRoot.relativize(file)} imports $forbidden" }
        }

        assertTrue(
            "core:runtime-audio must expose core-owned ports/models, not app or feature models: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun core_runtime_audio_must_not_absorb_qq_or_voiceasset_business_owners() {
        val forbiddenFiles = listOf(
            "TencentSilkEncoder.kt",
            "TtsVoiceAssetRepository.kt",
            "VoiceCloneService.kt",
        ).map(runtimeAudioRoot::resolve).filter { it.exists() }

        assertTrue(
            "QQ silk and voiceasset storage/clone owners are deferred to phases 18/19 and must not move into generic core audio: $forbiddenFiles",
            forbiddenFiles.isEmpty(),
        )
    }

    @Test
    fun global_singleton_allowlist_must_assign_audio_debt_to_phase_9b_owners() {
        val entries = singletonAllowlistEntries()
        val expected = mapOf(
            "core/runtime/audio/VoiceCloneService.kt" to ExpectedSingletonDebt(
                owner = "feature-voiceasset:runtime",
                expires = "phase-19",
                requiredText = "feature voiceasset",
            ),
            "core/runtime/audio/AndroidSystemTtsBridge.kt" to ExpectedSingletonDebt(
                owner = "core:runtime-audio",
                expires = "phase-19",
                requiredText = "AudioRuntimePort",
            ),
            "core/runtime/audio/SherpaOnnxBridge.kt" to ExpectedSingletonDebt(
                owner = "core:runtime-audio",
                expires = "phase-11",
                requiredText = "AudioRuntimePort",
            ),
            "core/runtime/audio/TtsVoiceAssetRepository.kt" to ExpectedSingletonDebt(
                owner = "feature-voiceasset:data",
                expires = "phase-19",
                requiredText = "feature voiceasset data",
            ),
            "core/runtime/audio/SherpaOnnxAssetManager.kt" to ExpectedSingletonDebt(
                owner = "core:runtime-audio",
                expires = "phase-11",
                requiredText = "download port",
            ),
        )

        val violations = expected.mapNotNull { (path, debt) ->
            val entry = entries[path] ?: return@mapNotNull "$path is missing from global singleton allowlist"
            val combinedText = "${entry.target} ${entry.reason}"
            when {
                entry.owner != debt.owner -> "$path owner=${entry.owner}, expected ${debt.owner}"
                entry.expires != debt.expires -> "$path expires=${entry.expires}, expected ${debt.expires}"
                !combinedText.contains(debt.requiredText) -> {
                    "$path must explain ${debt.requiredText} ownership in target/reason"
                }
                else -> null
            }
        }

        assertTrue(
            "Phase 9-B audio singleton allowlist entries must not retain stale core-audio/phase-5 ownership: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun llm_media_production_adapter_must_use_injected_audio_runtime_port() {
        val probeAdapter = appMainRoot.resolve("core/runtime/llm/HiltLlmProviderProbePort.kt")
        val text = probeAdapter.readText(UTF_8)

        assertTrue(
            "HiltLlmProviderProbePort must inject AudioRuntimePort for STT/TTS media operations.",
            text.contains("AudioRuntimePort") && text.contains("audioRuntimePort"),
        )
        assertTrue(
            "HiltLlmProviderProbePort must not call ChatCompletionService STT/TTS methods directly.",
            !text.contains("ChatCompletionService.transcribeAudio(") &&
                !text.contains("ChatCompletionService.synthesizeSpeech("),
        )
    }

    @Test
    fun llm_media_service_must_be_explicit_compat_facade_with_phase_9_expiry() {
        val mediaService = appMainRoot.resolve("core/runtime/llm/LlmMediaService.kt")
        val text = mediaService.readText(UTF_8)

        assertTrue(
            "LlmMediaService may remain only as an explicit phase 9 compat facade.",
            text.contains("compat facade") &&
                text.contains("Phase 9 final gate") &&
                text.contains("AudioRuntimePort"),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun singletonAllowlistEntries(): Map<String, SingletonAllowlistEntry> {
        return globalSingletonAllowlistFile.readLines(UTF_8)
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .associate { line ->
                val parts = line.split("|").map(String::trim)
                val entry = SingletonAllowlistEntry(
                    path = parts[0],
                    owner = parts[3],
                    target = parts[4],
                    reason = parts[5],
                    expires = parts[6],
                )
                entry.path to entry
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

    private data class ExpectedSingletonDebt(
        val owner: String,
        val expires: String,
        val requiredText: String,
    )

    private data class SingletonAllowlistEntry(
        val path: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
    )
}
