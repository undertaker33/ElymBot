package com.elymbot.android.core.runtime.audio

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLlmSingletonClosureSourceTest {

    private val projectRoot = projectRoot()

    @Test
    fun android_system_tts_bridge_is_injectable_instance_not_global_object() {
        val source = projectRoot.resolve(
            "app/src/main/java/com/elymbot/android/core/runtime/audio/AndroidSystemTtsBridge.kt",
        ).readText()

        assertFalse(
            "AndroidSystemTtsBridge should no longer be a global object.",
            source.contains("object AndroidSystemTtsBridge"),
        )
        assertTrue(
            "AndroidSystemTtsBridge should be available as an injectable instance adapter.",
            source.contains("class AndroidSystemTtsBridge @Inject constructor") &&
                source.contains("RuntimeLogger") &&
                !source.contains("SharedRuntimeLogStore"),
        )
    }

    @Test
    fun sherpa_bridge_uses_asset_service_instead_of_static_asset_manager() {
        val source = projectRoot.resolve(
            "app/src/main/java/com/elymbot/android/core/runtime/audio/SherpaOnnxBridge.kt",
        ).readText()

        assertTrue(
            "SherpaOnnxBridge should delegate asset lookups to SherpaOnnxAssetService.",
            source.contains("private val assetService: SherpaOnnxAssetService") &&
                source.contains("class SherpaOnnxBridge @Inject constructor"),
        )
        assertFalse(
            "SherpaOnnxBridge should not call the static SherpaOnnxAssetManager facade directly.",
            source.contains("SherpaOnnxAssetManager."),
        )
    }

    @Test
    fun sherpa_tts_generation_uses_generated_audio_without_callback_path() {
        val source = projectRoot.resolve(
            "app/src/main/java/com/elymbot/android/core/runtime/audio/SherpaOnnxBridge.kt",
        ).readText()

        assertFalse(
            "QQ TTS replies produce a complete attachment, so Sherpa TTS should avoid the callback path that crosses native code before any audio is needed.",
            source.contains("generateWithCallback("),
        )
        assertFalse(
            "Sherpa TTS should use GeneratedAudio.sampleRate instead of querying OfflineTts.sampleRate() before generation.",
            source.contains("tts.sampleRate()"),
        )
        assertTrue(
            "Sherpa TTS should build the attachment from the GeneratedAudio returned by OfflineTts.generate().",
            source.contains("tts.generate(") &&
                source.contains("generated.sampleRate") &&
                source.contains("generated.samples"),
        )
    }

    @Test
    fun llm_media_service_is_injected_audio_runtime_adapter_not_global_object() {
        val source = projectRoot.resolve(
            "app/src/main/java/com/elymbot/android/core/runtime/llm/LlmMediaService.kt",
        ).readText()

        assertFalse(
            "LlmMediaService should no longer be a global object.",
            source.contains("object LlmMediaService"),
        )
        assertTrue(
            "LlmMediaService should delegate media calls through injected AudioRuntimePort.",
            source.contains("class LlmMediaService @Inject constructor") &&
                source.contains("private val audioRuntimePort: AudioRuntimePort") &&
                !source.contains("ChatCompletionService.transcribeAudio(") &&
                !source.contains("ChatCompletionService.synthesizeSpeech("),
        )
    }

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir") ?: ".").toAbsolutePath()
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: break
        }
        return current
    }
}


