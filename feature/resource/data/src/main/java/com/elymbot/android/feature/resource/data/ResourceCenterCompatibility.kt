package com.elymbot.android.feature.resource.data

import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.McpServerEntry
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import com.elymbot.android.model.SkillEntry
import com.elymbot.android.model.SkillResourceKind
import org.json.JSONArray
import org.json.JSONObject

object ResourceCenterCompatibility {
    fun projectionsFromConfigProfile(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        projectionsFromConfigSnapshot(profile.toResourceConfigSnapshot())

    fun projectionsFromConfigSnapshot(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot {
        val mcpResources = config.mcpServers.map { entry -> entry.toResource() }
        val skillResources = config.skills.map { entry -> entry.toPromptSkillResource() }
        val mcpProjections = config.mcpServers.mapIndexed { index, entry ->
            ConfigResourceProjection(
                configId = config.id,
                resourceId = entry.serverId,
                kind = ResourceCenterKind.MCP_SERVER,
                active = entry.active,
                priority = 0,
                sortIndex = index,
                configJson = entry.toPayloadJson(),
            )
        }
        val skillProjections = config.skills.mapIndexed { index, entry ->
            ConfigResourceProjection(
                configId = config.id,
                resourceId = entry.skillId,
                kind = ResourceCenterKind.SKILL,
                active = entry.active,
                priority = entry.priority,
                sortIndex = index,
                configJson = "{}",
            )
        }
        return ResourceCenterCompatibilitySnapshot(
            resources = (mcpResources + skillResources).distinctBy { it.resourceId },
            projections = mcpProjections + skillProjections,
        )
    }

    private fun ConfigProfile.toResourceConfigSnapshot(): ResourceConfigSnapshot {
        return ResourceConfigSnapshot(
            id = id,
            mcpServers = mcpServers,
            skills = skills,
        )
    }

    private fun McpServerEntry.toResource(): ResourceCenterItem {
        return ResourceCenterItem(
            resourceId = serverId,
            kind = ResourceCenterKind.MCP_SERVER,
            skillKind = null,
            name = name,
            description = when {
                url.isNotBlank() -> url
                command.isNotBlank() -> command
                else -> transport
            },
            content = "",
            payloadJson = toPayloadJson(),
            source = "legacy_config",
            enabled = active,
        )
    }

    private fun SkillEntry.toPromptSkillResource(): ResourceCenterItem {
        return ResourceCenterItem(
            resourceId = skillId,
            kind = ResourceCenterKind.SKILL,
            skillKind = SkillResourceKind.PROMPT,
            name = name,
            description = description,
            content = content,
            payloadJson = "{}",
            source = "legacy_config",
            enabled = active,
        )
    }

    private fun McpServerEntry.toPayloadJson(): String {
        return JSONObject()
            .put("url", url)
            .put("transport", transport)
            .put("command", command)
            .put("args", JSONArray(args))
            .put("headers", JSONObject(headers))
            .put("timeoutSeconds", timeoutSeconds)
            .toString()
    }
}
