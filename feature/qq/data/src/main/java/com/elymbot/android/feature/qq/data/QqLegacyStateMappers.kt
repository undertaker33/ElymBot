package com.elymbot.android.feature.qq.data

import com.elymbot.android.data.db.SavedQqAccountEntity
import com.elymbot.android.feature.qq.domain.model.NapCatBridgeConfig
import com.elymbot.android.feature.qq.domain.model.SavedQqAccount
import org.json.JSONArray

internal fun parseLegacyNapCatBridgeConfig(
    defaults: NapCatBridgeConfig = NapCatBridgeConfig(),
    values: Map<String, Any?>,
): NapCatBridgeConfig {
    return mergeNapCatBridgeConfig(defaults, values)
}

internal fun mergeNapCatBridgeConfig(
    defaults: NapCatBridgeConfig = NapCatBridgeConfig(),
    values: Map<String, Any?>,
): NapCatBridgeConfig {
    return defaults.copy(
        runtimeMode = values["runtime_mode"]?.toString().orEmpty().ifBlank { defaults.runtimeMode },
        endpoint = values["endpoint"]?.toString().orEmpty().ifBlank { defaults.endpoint },
        healthUrl = values["health_url"]?.toString().orEmpty().ifBlank { defaults.healthUrl },
        autoStart = (values["auto_start"] as? Boolean) ?: defaults.autoStart,
        startCommand = sanitizeBridgeCommand(values["start_command"]?.toString(), defaults.startCommand),
        stopCommand = sanitizeBridgeCommand(values["stop_command"]?.toString(), defaults.stopCommand),
        statusCommand = sanitizeBridgeCommand(values["status_command"]?.toString(), defaults.statusCommand),
        commandPreview = sanitizeBridgeCommand(values["command_preview"]?.toString(), defaults.commandPreview),
    )
}

internal fun parseLegacySavedQqAccounts(raw: String?): List<SavedQqAccount> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uin = item.optString("uin").trim()
            if (uin.isBlank()) continue
            add(
                SavedQqAccount(
                    uin = uin,
                    nickName = item.optString("nickName").trim(),
                    avatarUrl = item.optString("avatarUrl").trim(),
                ),
            )
        }
    }
}

internal fun SavedQqAccountEntity.toModel(): SavedQqAccount {
    return SavedQqAccount(
        uin = uin,
        nickName = nickName,
        avatarUrl = avatarUrl,
    )
}

internal fun SavedQqAccount.toEntity(sortIndex: Int): SavedQqAccountEntity {
    return SavedQqAccountEntity(
        uin = uin,
        nickName = nickName,
        avatarUrl = avatarUrl,
        sortIndex = sortIndex,
        updatedAt = System.currentTimeMillis(),
    )
}

private fun sanitizeBridgeCommand(candidate: String?, fallback: String): String {
    val value = candidate.orEmpty().trim()
    if (value.isBlank()) return fallback
    return if (value.contains("/data/local/tmp/napcat/", ignoreCase = true)) fallback else value
}
