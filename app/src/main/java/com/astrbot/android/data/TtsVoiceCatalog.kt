package com.astrbot.android.data

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType

object TtsVoiceCatalog {
    const val DEFAULT_VOICE_ID = ""
    const val CUSTOM_VOICE_ID = "__custom__"

    fun optionsFor(provider: ProviderProfile?): List<Pair<String, String>> {
        if (provider == null) return emptyList()
        val presets = when (provider.providerType) {
            ProviderType.OPENAI_TTS -> listOf(
                "alloy",
                "echo",
                "fable",
                "onyx",
                "nova",
                "shimmer",
            )

            ProviderType.BAILIAN_TTS -> listOf("Cherry")

            ProviderType.MINIMAX_TTS -> listOf("Chinese (Mandarin)_Warm_Girl")

            else -> emptyList()
        }
        return buildList {
            add(DEFAULT_VOICE_ID to "Provider default")
            addAll(presets.distinct().map { it to it })
        }
    }

    fun resolveSelectedVoiceChoice(
        provider: ProviderProfile?,
        voiceId: String,
    ): String {
        val normalized = voiceId.trim()
        if (normalized.isBlank()) {
            return DEFAULT_VOICE_ID
        }
        val availableIds = optionsFor(provider).map { it.first }.toSet()
        return if (normalized in availableIds) normalized else CUSTOM_VOICE_ID
    }
}
