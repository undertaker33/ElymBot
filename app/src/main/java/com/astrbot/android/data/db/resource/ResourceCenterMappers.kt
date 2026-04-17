package com.astrbot.android.data.db.resource

import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind

fun ResourceCenterItemEntity.toModel(): ResourceCenterItem {
    return ResourceCenterItem(
        resourceId = resourceId,
        kind = enumValueOfOrDefault(kind, ResourceCenterKind.SKILL),
        skillKind = skillKind?.let { enumValueOfOrDefault(it, SkillResourceKind.PROMPT) },
        name = name,
        description = description,
        content = content,
        payloadJson = payloadJson,
        source = source,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ResourceCenterItem.toEntity(): ResourceCenterItemEntity {
    return ResourceCenterItemEntity(
        resourceId = resourceId,
        kind = kind.name,
        skillKind = skillKind?.name,
        name = name,
        description = description,
        content = content,
        payloadJson = payloadJson,
        source = source,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ConfigResourceProjectionEntity.toModel(): ConfigResourceProjection {
    return ConfigResourceProjection(
        configId = configId,
        resourceId = resourceId,
        kind = enumValueOfOrDefault(kind, ResourceCenterKind.SKILL),
        active = active,
        priority = priority,
        sortIndex = sortIndex,
        configJson = configJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ConfigResourceProjection.toEntity(): ConfigResourceProjectionEntity {
    return ConfigResourceProjectionEntity(
        configId = configId,
        resourceId = resourceId,
        kind = kind.name,
        active = active,
        priority = priority,
        sortIndex = sortIndex,
        configJson = configJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private inline fun <reified T : Enum<T>> enumValueOfOrDefault(
    name: String,
    fallback: T,
): T {
    return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
