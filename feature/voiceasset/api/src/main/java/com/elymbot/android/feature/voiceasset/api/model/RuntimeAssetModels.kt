package com.elymbot.android.feature.voiceasset.api.model

enum class RuntimeAssetId(val value: String) {
    TTS("tts"),
    ON_DEVICE_FRAMEWORK("on-device-framework"),
    ON_DEVICE_STT("on-device-stt"),
    ON_DEVICE_TTS("on-device-tts"),
    TTS_VOICE_ASSETS("tts-voice-assets");

    companion object {
        fun fromValue(value: String): RuntimeAssetId? = entries.firstOrNull { it.value == value }
    }
}

data class RuntimeAssetCatalogItem(
    val id: RuntimeAssetId,
    val titleRes: Int,
    val subtitleRes: Int,
    val descriptionRes: Int,
    val actionsEnabled: Boolean = true,
)

data class RuntimeAssetEntryState(
    val catalog: RuntimeAssetCatalogItem,
    val installed: Boolean = false,
    val busy: Boolean = false,
    val lastAction: String = "",
    val details: String = "",
)

data class RuntimeAssetState(
    val assets: List<RuntimeAssetEntryState> = emptyList(),
)
