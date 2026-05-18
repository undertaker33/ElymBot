package com.elymbot.android.model

import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType

object TtsVoiceCatalog {
    const val CUSTOM_VOICE_ID = "__custom__"

    private val kokoroVoiceIds = listOf(
        "zf_001",
        "zf_005",
        "zf_018",
        "zf_026",
        "zf_036",
        "zf_044",
        "zf_051",
        "zf_060",
        "zf_071",
        "zf_079",
        "zm_009",
        "zm_016",
        "zm_025",
        "zm_041",
        "zm_054",
        "zm_061",
        "zm_068",
        "zm_080",
        "zm_091",
        "zm_100",
    )

    fun optionsFor(provider: ProviderProfile?): List<Pair<String, String>> {
        if (provider == null) return emptyList()
        if (provider.providerType == ProviderType.SHERPA_ONNX_TTS) {
            return onDeviceTtsVoiceOptions(provider.model)
        }
        val presets = when (provider.providerType) {
            ProviderType.OPENAI_TTS -> listOf(
                "alloy",
                "echo",
                "fable",
                "onyx",
                "nova",
                "shimmer",
            )

            ProviderType.BAILIAN_TTS -> if (provider.model.trim().lowercase().contains("-vc")) {
                emptyList()
            } else {
                listOf("Cherry")
            }

            ProviderType.MINIMAX_TTS -> listOf("Chinese (Mandarin)_Warm_Girl")
            else -> emptyList()
        }
        return presets.distinct().map { voiceId -> voiceId to voiceId }
    }

    fun resolveSelectedVoiceChoice(
        provider: ProviderProfile?,
        voiceId: String,
    ): String {
        val options = optionsFor(provider)
        val normalized = voiceId.trim()
        if (normalized.isBlank()) {
            return options.firstOrNull()?.first.orEmpty()
        }
        val availableIds = options.map { it.first }.toSet()
        return if (normalized in availableIds) normalized else CUSTOM_VOICE_ID
    }

    private fun onDeviceTtsVoiceOptions(model: String): List<Pair<String, String>> {
        return when (model.trim().lowercase()) {
            "kokoro" -> kokoroVoiceIds.map { voiceId -> voiceId to voiceId }
            else -> emptyList()
        }
    }
}
