package com.elymbot.android.core.runtime.audio

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxAssetServiceSourceTest {

    private val sourcePath = projectRoot().resolve(
        "core/runtime-audio/src/main/java/com/elymbot/android/core/runtime/audio/SherpaOnnxAssetManager.kt",
    )
    private val source = sourcePath.readText()

    @Test
    fun sherpa_asset_runtime_has_instance_service_for_hilt_wiring() {
        assertTrue(
            "Sherpa ONNX asset operations should be available as an injectable service before retiring the compat object.",
            source.contains("interface SherpaOnnxAssetService") &&
                source.contains("class DefaultSherpaOnnxAssetService") &&
                source.contains("@Inject constructor"),
        )
    }

    @Test
    fun sherpa_asset_manager_object_is_retired_from_production_main() {
        val objectStart = source.indexOf("object SherpaOnnxAssetManager")
        val serviceStart = source.indexOf("class DefaultSherpaOnnxAssetService")
        assertTrue("SherpaOnnxAssetManager object must be retired after Phase 27 singleton closure.", objectStart < 0)
        assertTrue(
            "DefaultSherpaOnnxAssetService must remain as the production implementation.",
            serviceStart >= 0,
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
