package com.astrbot.android.feature.config.data

import com.astrbot.android.feature.config.domain.model.ConfigProfile
import org.json.JSONArray

data class LegacyConfigImport(
    val profiles: List<ConfigProfile>,
    val selectedProfileId: String?,
)

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
