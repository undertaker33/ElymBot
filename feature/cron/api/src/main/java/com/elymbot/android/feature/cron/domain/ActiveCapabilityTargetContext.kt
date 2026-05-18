package com.elymbot.android.feature.cron.domain

import org.json.JSONObject

data class ActiveCapabilityTargetContext(
    val platform: String,
    val conversationId: String,
    val botId: String,
    val configProfileId: String,
    val personaId: String,
    val providerId: String,
    val origin: String,
) {
    fun missingRequiredFields(): List<String> {
        return buildList {
            if (platform.isBlank()) add("platform")
            if (conversationId.isBlank()) add("conversation_id")
            if (botId.isBlank()) add("bot_id")
            if (configProfileId.isBlank()) add("config_profile_id")
            if (providerId.isBlank()) add("provider_id")
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("platform", platform)
            put("conversation_id", conversationId)
            put("bot_id", botId)
            put("config_profile_id", configProfileId)
            put("persona_id", personaId)
            put("provider_id", providerId)
            put("origin", origin)
        }
    }
}
