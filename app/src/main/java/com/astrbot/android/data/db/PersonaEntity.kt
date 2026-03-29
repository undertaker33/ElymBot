package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.PersonaProfile

@Entity(tableName = "persona_profiles")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tag: String,
    val systemPrompt: String,
    val enabledToolsJson: String,
    val defaultProviderId: String,
    val maxContextMessages: Int,
    val enabled: Boolean,
    val sortIndex: Int,
    val updatedAt: Long,
)

fun PersonaEntity.toProfile(): PersonaProfile {
    return PersonaProfile(
        id = id,
        name = name,
        tag = tag,
        systemPrompt = systemPrompt,
        enabledTools = enabledToolsJson.parseJsonStringList().toSet(),
        defaultProviderId = defaultProviderId,
        maxContextMessages = maxContextMessages,
        enabled = enabled,
    )
}

fun PersonaProfile.toEntity(sortIndex: Int): PersonaEntity {
    return PersonaEntity(
        id = id,
        name = name,
        tag = tag,
        systemPrompt = systemPrompt,
        enabledToolsJson = enabledTools.toJsonArrayString(),
        defaultProviderId = defaultProviderId,
        maxContextMessages = maxContextMessages,
        enabled = enabled,
        sortIndex = sortIndex,
        updatedAt = System.currentTimeMillis(),
    )
}
