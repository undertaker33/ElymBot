package com.astrbot.android.feature.provider.data

import com.astrbot.android.feature.provider.domain.model.FeatureSupportState
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.feature.provider.domain.model.ProviderType
import org.json.JSONArray

internal fun parseLegacyProviderProfiles(raw: String?): List<ProviderProfile> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val providerType = runCatching {
                ProviderType.valueOf(item.optString("providerType"))
            }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
            val model = item.optString("model")
            add(
                ProviderProfile(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    baseUrl = item.optString("baseUrl"),
                    model = model,
                    providerType = providerType,
                    apiKey = item.optString("apiKey"),
                    capabilities = parseLegacyCapabilities(item.optJSONArray("capabilities"), providerType),
                    enabled = item.optBoolean("enabled", true),
                    multimodalRuleSupport = parseLegacyFeatureSupportState(
                        item.optString("multimodalRuleSupport"),
                        inferMultimodalRuleSupport(providerType, model),
                    ),
                    multimodalProbeSupport = parseLegacyFeatureSupportState(
                        item.optString("multimodalProbeSupport"),
                        FeatureSupportState.UNKNOWN,
                    ),
                    nativeStreamingRuleSupport = parseLegacyFeatureSupportState(
                        item.optString("nativeStreamingRuleSupport"),
                        inferNativeStreamingRuleSupport(providerType, model),
                    ),
                    nativeStreamingProbeSupport = parseLegacyFeatureSupportState(
                        item.optString("nativeStreamingProbeSupport"),
                        FeatureSupportState.UNKNOWN,
                    ),
                    sttProbeSupport = parseLegacyFeatureSupportState(
                        item.optString("sttProbeSupport"),
                        FeatureSupportState.UNKNOWN,
                    ),
                    ttsProbeSupport = parseLegacyFeatureSupportState(
                        item.optString("ttsProbeSupport"),
                        FeatureSupportState.UNKNOWN,
                    ),
                    ttsVoiceOptions = item.optStringList("ttsVoiceOptions"),
                ),
            )
        }
    }
}

private fun parseLegacyCapabilities(
    capabilityArray: JSONArray?,
    providerType: ProviderType,
): Set<ProviderCapability> {
    val parsed = buildSet {
        val source = capabilityArray ?: JSONArray()
        for (index in 0 until source.length()) {
            when (source.optString(index)) {
                "ASR" -> add(ProviderCapability.STT)
                else -> runCatching {
                    ProviderCapability.valueOf(source.getString(index))
                }.getOrNull()?.let(::add)
            }
        }
    }
    return parsed.ifEmpty { setOf(providerType.defaultCapability()) }
}

private fun parseLegacyFeatureSupportState(
    raw: String,
    defaultValue: FeatureSupportState,
): FeatureSupportState {
    return runCatching { FeatureSupportState.valueOf(raw) }.getOrDefault(defaultValue)
}

private fun org.json.JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.opt(index)?.toString().orEmpty())
        }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
