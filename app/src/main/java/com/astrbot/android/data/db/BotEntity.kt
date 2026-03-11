package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.BotProfile

@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey val id: String,
    val platformName: String,
    val displayName: String,
    val tag: String,
    val accountHint: String,
    val triggerWordsCsv: String,
    val autoReplyEnabled: Boolean,
    val bridgeMode: String,
    val bridgeEndpoint: String,
    val defaultProviderId: String,
    val defaultPersonaId: String,
    val status: String,
    val updatedAt: Long,
)

fun BotEntity.toProfile(): BotProfile {
    return BotProfile(
        id = id,
        platformName = platformName,
        displayName = displayName,
        tag = tag,
        accountHint = accountHint,
        triggerWords = triggerWordsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        autoReplyEnabled = autoReplyEnabled,
        bridgeMode = bridgeMode,
        bridgeEndpoint = bridgeEndpoint,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        status = status,
    )
}

fun BotProfile.toEntity(): BotEntity {
    return BotEntity(
        id = id,
        platformName = platformName,
        displayName = displayName,
        tag = tag,
        accountHint = accountHint,
        triggerWordsCsv = triggerWords.joinToString(","),
        autoReplyEnabled = autoReplyEnabled,
        bridgeMode = bridgeMode,
        bridgeEndpoint = bridgeEndpoint,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        status = status,
        updatedAt = System.currentTimeMillis(),
    )
}
