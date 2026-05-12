package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssetModelOwnershipContractTest {
    private val projectRoot: Path = detectProjectRoot()
    private val voiceAssetModelsFile: Path =
        projectRoot.resolve("feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/model/TtsVoiceModels.kt")
    private val providerApiVoiceModelsFile: Path =
        projectRoot.resolve("feature/provider/api/src/main/java/com/astrbot/android/model/TtsVoiceModels.kt")

    @Test
    fun voice_asset_models_must_be_owned_by_voiceasset_api() {
        assertTrue(
            "Voice asset models must live in feature:voiceasset:api: $voiceAssetModelsFile",
            voiceAssetModelsFile.exists(),
        )
        assertFalse(
            "Provider api must not continue owning voice asset models: $providerApiVoiceModelsFile",
            providerApiVoiceModelsFile.exists(),
        )

        val source = voiceAssetModelsFile.readText()
        val requiredSymbols = listOf(
            "data class ClonedVoiceBinding",
            "data class TtsVoiceReferenceClip",
            "data class TtsVoiceReferenceAsset",
        )
        val missingSymbols = requiredSymbols.filterNot(source::contains)

        assertTrue(
            "Voice asset api model file is missing required symbols: $missingSymbols",
            missingSymbols.isEmpty(),
        )
        assertFalse(
            "Voice asset api models must not depend on provider implementation/api owner.",
            source.contains("com.astrbot.android.feature.provider"),
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
}
