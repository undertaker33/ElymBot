package com.elymbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssetModelOwnershipContractTest {
    private val projectRoot: Path = detectProjectRoot()
    private val voiceAssetModelsFile: Path =
        projectRoot.resolve("feature/voiceasset/api/src/main/java/com/elymbot/android/feature/voiceasset/api/model/TtsVoiceModels.kt")
    private val providerApiVoiceModelsFile: Path =
        projectRoot.resolve("feature/provider/api/src/main/java/com/elymbot/android/model/TtsVoiceModels.kt")
    private val voiceAssetDataRoot: Path =
        projectRoot.resolve("feature/voiceasset/data/src/main/java/com/elymbot/android/feature/voiceasset/data")
    private val voiceAssetPresentationRoot: Path =
        projectRoot.resolve("feature/voiceasset/presentation/src/main/java/com/elymbot/android")

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
            source.contains("com.elymbot.android.feature.provider"),
        )
    }

    @Test
    fun phase25_voiceasset_data_and_presentation_modules_must_own_runtime_and_ui() {
        val requiredDataFiles = listOf(
            "RuntimeAssetRepository.kt",
            "TtsVoiceAssetRepository.kt",
            "VoiceCloneService.kt",
        ).map(voiceAssetDataRoot::resolve)
        val requiredPresentationFiles = listOf(
            "ui/settings/AssetScreens.kt",
            "ui/settings/RuntimeAssetViewModel.kt",
        ).map(voiceAssetPresentationRoot::resolve)

        val missing = (requiredDataFiles + requiredPresentationFiles).filterNot { it.exists() }
        assertTrue(
            "Phase 25 voiceasset data/presentation files must live under feature:voiceasset modules: $missing",
            missing.isEmpty(),
        )

        val presentationText = requiredPresentationFiles.joinToString("\n") { it.readText() }
        assertFalse(
            "Voiceasset presentation must not import DAO/runtime implementation/static repositories.",
            presentationText.contains("TtsVoiceAssetAggregateDao") ||
                presentationText.contains("TtsVoiceAssetRepository.") ||
                presentationText.contains("VoiceCloneService."),
        )
        assertFalse(
            "Voiceasset presentation must depend on provider API contracts, not provider runtime packages.",
            presentationText.contains("com.elymbot.android.feature.provider.runtime"),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/elymbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/elymbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
