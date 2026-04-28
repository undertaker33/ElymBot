package com.astrbot.android.model

typealias BotProfile = com.astrbot.android.feature.bot.domain.model.BotProfile
typealias ConfigProfile = com.astrbot.android.feature.config.domain.model.ConfigProfile
typealias CronJob = com.astrbot.android.feature.cron.domain.model.CronJob
typealias CronJobExecutionRecord = com.astrbot.android.feature.cron.domain.model.CronJobExecutionRecord
typealias PersonaProfile = com.astrbot.android.feature.persona.domain.model.PersonaProfile
typealias PersonaToolEnablementSnapshot = com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
typealias FeatureSupportState = com.astrbot.android.feature.provider.domain.model.FeatureSupportState
typealias ProviderCapability = com.astrbot.android.feature.provider.domain.model.ProviderCapability
typealias ProviderProfile = com.astrbot.android.feature.provider.domain.model.ProviderProfile
typealias ProviderType = com.astrbot.android.feature.provider.domain.model.ProviderType
typealias BotRuntimeState = com.astrbot.android.feature.qq.domain.model.BotRuntimeState
typealias NapCatBridgeConfig = com.astrbot.android.feature.qq.domain.model.NapCatBridgeConfig
typealias NapCatLoginState = com.astrbot.android.feature.qq.domain.model.NapCatLoginState
typealias NapCatRuntimeState = com.astrbot.android.feature.qq.domain.model.NapCatRuntimeState
typealias RuntimeStatus = com.astrbot.android.feature.qq.domain.model.RuntimeStatus
typealias SavedQqAccount = com.astrbot.android.feature.qq.domain.model.SavedQqAccount

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

data class ClonedVoiceBinding(
    val id: String,
    val providerId: String,
    val providerType: ProviderType,
    val model: String,
    val voiceId: String,
    val displayName: String,
    val createdAt: Long,
    val lastVerifiedAt: Long = 0L,
    val status: String = "ready",
)

data class TtsVoiceReferenceClip(
    val id: String,
    val localPath: String,
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TtsVoiceReferenceAsset(
    val id: String,
    val name: String,
    val source: String = "",
    val localPath: String = "",
    val remoteUrl: String = "",
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val clips: List<TtsVoiceReferenceClip> = emptyList(),
    val providerBindings: List<ClonedVoiceBinding> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

