package com.astrbot.android.feature.bot.data

import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import org.json.JSONObject

internal data class LegacyBotBindingState(
    val configProfileId: String = ConfigRepositoryPort.DEFAULT_CONFIG_ID,
    val boundQqUins: List<String> = emptyList(),
    val persistConversationLocally: Boolean = false,
)

internal fun parseLegacyBotBindings(raw: String?): Map<String, LegacyBotBindingState> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyMap()
    val objectJson = JSONObject(source)
    return buildMap {
        objectJson.keys().forEach { key ->
            val rawValue = objectJson.opt(key)
            put(
                key,
                when (rawValue) {
                    is JSONObject -> LegacyBotBindingState(
                        configProfileId = rawValue.optString("configProfileId")
                            .ifBlank { ConfigRepositoryPort.DEFAULT_CONFIG_ID },
                        boundQqUins = rawValue.optStringList("boundQqUins"),
                        persistConversationLocally = rawValue.optBoolean("persistConversationLocally", false),
                    )

                    else -> LegacyBotBindingState(
                        configProfileId = objectJson.optString(key)
                            .ifBlank { ConfigRepositoryPort.DEFAULT_CONFIG_ID },
                    )
                },
            )
        }
    }
}

private fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.optString(index).trim())
        }
    }.filter { it.isNotBlank() }
}
