package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType

@Entity(tableName = "provider_profiles")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: String,
    val apiKey: String,
    val capabilitiesJson: String,
    val enabled: Boolean,
    val multimodalRuleSupport: String,
    val multimodalProbeSupport: String,
    val nativeStreamingRuleSupport: String,
    val nativeStreamingProbeSupport: String,
    val sttProbeSupport: String,
    val ttsProbeSupport: String,
    val ttsVoiceOptionsJson: String,
    val sortIndex: Int,
    val updatedAt: Long,
)

fun ProviderEntity.toProfile(): ProviderProfile {
    return ProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = runCatching { ProviderType.valueOf(providerType) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE),
        apiKey = apiKey,
        capabilities = capabilitiesJson.parseJsonStringList()
            .mapNotNull { value -> runCatching { ProviderCapability.valueOf(value) }.getOrNull() }
            .toSet(),
        enabled = enabled,
        multimodalRuleSupport = runCatching { FeatureSupportState.valueOf(multimodalRuleSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        multimodalProbeSupport = runCatching { FeatureSupportState.valueOf(multimodalProbeSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        nativeStreamingRuleSupport = runCatching { FeatureSupportState.valueOf(nativeStreamingRuleSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        nativeStreamingProbeSupport = runCatching { FeatureSupportState.valueOf(nativeStreamingProbeSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        sttProbeSupport = runCatching { FeatureSupportState.valueOf(sttProbeSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        ttsProbeSupport = runCatching { FeatureSupportState.valueOf(ttsProbeSupport) }
            .getOrDefault(FeatureSupportState.UNKNOWN),
        ttsVoiceOptions = ttsVoiceOptionsJson.parseJsonStringList(),
    )
}

fun ProviderProfile.toEntity(sortIndex: Int): ProviderEntity {
    return ProviderEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.name,
        apiKey = apiKey,
        capabilitiesJson = capabilities.map { it.name }.toSet().toJsonArrayString(),
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.name,
        multimodalProbeSupport = multimodalProbeSupport.name,
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.name,
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.name,
        sttProbeSupport = sttProbeSupport.name,
        ttsProbeSupport = ttsProbeSupport.name,
        ttsVoiceOptionsJson = ttsVoiceOptions.toJsonArrayString(),
        sortIndex = sortIndex,
        updatedAt = System.currentTimeMillis(),
    )
}
