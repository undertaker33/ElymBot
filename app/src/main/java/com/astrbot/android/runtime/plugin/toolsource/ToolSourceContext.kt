package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.runtime.context.PromptSkillProjection
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.context.ToolSkillProjection
import com.astrbot.android.runtime.plugin.JsonLikeMap

data class ToolSourceContext(
    val requestId: String,
    val platform: RuntimePlatform,
    val configProfileId: String,
    val webSearchEnabled: Boolean,
    val activeCapabilityEnabled: Boolean,
    val mcpServers: List<McpServerEntry>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
    val conversationId: String,
    val runtimePermissions: JsonLikeMap = emptyMap(),
    val networkPolicy: JsonLikeMap = emptyMap(),
) {
    companion object {
        fun fromConfigProfile(
            config: ConfigProfile,
            requestId: String = "",
            platform: RuntimePlatform = RuntimePlatform.APP_CHAT,
            conversationId: String = "",
            mcpServers: List<McpServerEntry> = config.mcpServers,
            promptSkills: List<PromptSkillProjection> = emptyList(),
            toolSkills: List<ToolSkillProjection> = emptyList(),
        ): ToolSourceContext {
            return ToolSourceContext(
                requestId = requestId,
                platform = platform,
                configProfileId = config.id,
                webSearchEnabled = config.webSearchEnabled,
                activeCapabilityEnabled = config.proactiveEnabled,
                mcpServers = mcpServers,
                promptSkills = promptSkills,
                toolSkills = toolSkills,
                conversationId = conversationId,
            )
        }
    }
}
