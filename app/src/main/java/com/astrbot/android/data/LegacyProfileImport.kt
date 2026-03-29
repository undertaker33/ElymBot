package com.astrbot.android.data

import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.inferNativeStreamingRuleSupport
import org.json.JSONArray

data class LegacyConfigImport(
    val profiles: List<ConfigProfile>,
    val selectedProfileId: String?,
)

fun parseLegacyProviderProfiles(raw: String?): List<ProviderProfile> {
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

fun parseLegacyConfigProfiles(
    rawProfilesJson: String?,
    rawSelectedId: String?,
): LegacyConfigImport {
    val profiles = rawProfilesJson?.takeIf { it.isNotBlank() }?.let { source ->
        val array = JSONArray(source)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ConfigProfile(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        defaultChatProviderId = item.optString("defaultChatProviderId"),
                        defaultVisionProviderId = item.optString("defaultVisionProviderId"),
                        defaultSttProviderId = item.optString("defaultSttProviderId"),
                        defaultTtsProviderId = item.optString("defaultTtsProviderId"),
                        sttEnabled = item.optBoolean("sttEnabled", false),
                        ttsEnabled = item.optBoolean("ttsEnabled", false),
                        alwaysTtsEnabled = item.optBoolean("alwaysTtsEnabled", false),
                        ttsReadBracketedContent = item.optBoolean("ttsReadBracketedContent", true),
                        textStreamingEnabled = item.optBoolean("textStreamingEnabled", false),
                        voiceStreamingEnabled = item.optBoolean("voiceStreamingEnabled", false),
                        streamingMessageIntervalMs = item.optInt("streamingMessageIntervalMs", 120),
                        realWorldTimeAwarenessEnabled = item.optBoolean("realWorldTimeAwarenessEnabled", false),
                        imageCaptionTextEnabled = item.optBoolean("imageCaptionTextEnabled", false),
                        webSearchEnabled = item.optBoolean("webSearchEnabled", false),
                        proactiveEnabled = item.optBoolean("proactiveEnabled", false),
                        ttsVoiceId = item.optString("ttsVoiceId"),
                        imageCaptionPrompt = item.optString(
                            "imageCaptionPrompt",
                            ConfigProfile().imageCaptionPrompt,
                        ),
                        adminUids = item.optStringList("adminUids"),
                        sessionIsolationEnabled = item.optBoolean("sessionIsolationEnabled", false),
                        wakeWords = item.optStringList("wakeWords"),
                        wakeWordsAdminOnlyEnabled = item.optBoolean("wakeWordsAdminOnlyEnabled", false),
                        privateChatRequiresWakeWord = item.optBoolean("privateChatRequiresWakeWord", false),
                        replyTextPrefix = item.optString("replyTextPrefix"),
                        quoteSenderMessageEnabled = item.optBoolean("quoteSenderMessageEnabled", false),
                        mentionSenderEnabled = item.optBoolean("mentionSenderEnabled", false),
                        replyOnAtOnlyEnabled = item.optBoolean("replyOnAtOnlyEnabled", true),
                        whitelistEnabled = item.optBoolean("whitelistEnabled", false),
                        whitelistEntries = item.optStringList("whitelistEntries"),
                        logOnWhitelistMiss = item.optBoolean("logOnWhitelistMiss", false),
                        adminGroupBypassWhitelistEnabled = item.optBoolean("adminGroupBypassWhitelistEnabled", true),
                        adminPrivateBypassWhitelistEnabled = item.optBoolean("adminPrivateBypassWhitelistEnabled", true),
                        ignoreSelfMessageEnabled = item.optBoolean("ignoreSelfMessageEnabled", true),
                        ignoreAtAllEventEnabled = item.optBoolean("ignoreAtAllEventEnabled", true),
                        replyWhenPermissionDenied = item.optBoolean("replyWhenPermissionDenied", false),
                        rateLimitWindowSeconds = item.optInt("rateLimitWindowSeconds", 0),
                        rateLimitMaxCount = item.optInt("rateLimitMaxCount", 0),
                        rateLimitStrategy = item.optString("rateLimitStrategy", "drop"),
                        keywordDetectionEnabled = item.optBoolean("keywordDetectionEnabled", false),
                        keywordPatterns = item.optStringList("keywordPatterns"),
                    ),
                )
            }
        }
    }.orEmpty()
    return LegacyConfigImport(
        profiles = profiles,
        selectedProfileId = rawSelectedId?.takeIf { it.isNotBlank() },
    )
}

fun parseLegacyPersonaProfiles(raw: String?): List<PersonaProfile> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                PersonaProfile(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    tag = item.optString("tag"),
                    systemPrompt = item.optString("systemPrompt"),
                    enabledTools = item.optStringList("enabledTools").toSet(),
                    defaultProviderId = item.optString("defaultProviderId"),
                    maxContextMessages = item.optInt("maxContextMessages", 12),
                    enabled = item.optBoolean("enabled", true),
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
