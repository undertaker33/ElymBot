package com.astrbot.android.core.runtime.context

data class PromptSkillProjection(
    val skillId: String,
    val name: String,
    val content: String,
    val priority: Int,
    val sortIndex: Int = 0,
    val scope: String = PromptSkillScope.GLOBAL,
    val conflictPolicy: String = PromptSkillConflictPolicy.APPEND,
    val budgetChars: Int = 0,
    val active: Boolean = true,
)

data class ToolSkillProjection(
    val skillId: String,
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?> = mapOf("type" to "object"),
    val resultTemplate: String = "",
    val sortIndex: Int = 0,
    val active: Boolean = true,
)

enum class RuntimeResourceCenterKind {
    MCP_SERVER,
    SKILL,
    TOOL,
}

enum class RuntimeSkillResourceKind {
    PROMPT,
    TOOL,
}

data class RuntimeResourceItemSnapshot(
    val resourceId: String = "",
    val kind: RuntimeResourceCenterKind = RuntimeResourceCenterKind.SKILL,
    val skillKind: RuntimeSkillResourceKind? = null,
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val payloadJson: String = "{}",
    val source: String = "local",
    val enabled: Boolean = true,
)

data class RuntimeConfigResourceProjectionSnapshot(
    val configId: String = "",
    val resourceId: String = "",
    val kind: RuntimeResourceCenterKind = RuntimeResourceCenterKind.SKILL,
    val active: Boolean = true,
    val priority: Int = 0,
    val sortIndex: Int = 0,
    val configJson: String = "{}",
)

data class RuntimeResourceCenterCompatibilitySnapshot(
    val resources: List<RuntimeResourceItemSnapshot>,
    val projections: List<RuntimeConfigResourceProjectionSnapshot>,
)

data class RuntimeResourceProjectionSnapshot<McpServerProjection>(
    val mcpServers: List<McpServerProjection>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
)

object PromptSkillScope {
    const val GLOBAL = "global"
    const val APP_CHAT = "app_chat"
    const val QQ_PRIVATE = "qq_private"
    const val QQ_GROUP = "qq_group"
    const val SCHEDULED_TASK = "scheduled_task"
}

object PromptSkillConflictPolicy {
    const val APPEND = "append"
    const val OVERRIDE_SAME_ID = "override_same_id"
    const val EXCLUSIVE_SCOPE = "exclusive_scope"
}
