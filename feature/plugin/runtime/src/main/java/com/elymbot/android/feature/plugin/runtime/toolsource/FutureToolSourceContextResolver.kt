package com.elymbot.android.feature.plugin.runtime.toolsource

import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.RuntimeSkillProjectionResolver
import com.elymbot.android.core.runtime.tool.ToolSourceRequestContext
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.plugin.runtime.toResourceConfigSnapshot
import com.elymbot.android.feature.plugin.runtime.toRuntimeConfigSnapshot
import com.elymbot.android.feature.plugin.runtime.toRuntimeResourceCenterCompatibilitySnapshot
import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import javax.inject.Inject
import javax.inject.Singleton

interface FutureToolSourceContextResolver {
    fun resolveForConfig(configProfileId: String): ToolSourceContext

    fun resolveForRequest(requestContext: ToolSourceRequestContext): ToolSourceContext {
        return resolveForConfig(requestContext.configId).copy(
            requestId = requestContext.conversationId,
            conversationId = requestContext.conversationId,
        )
    }
}

@Singleton
class PortBackedFutureToolSourceContextResolver @Inject constructor(
    private val configPort: ConfigRepositoryPort,
    private val resourceCenterPort: ResourceCenterPort,
) : FutureToolSourceContextResolver {
    override fun resolveForConfig(configProfileId: String): ToolSourceContext {
        val config = configPort.resolve(configProfileId)
        val resourceProjection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
            snapshot = resourceCenterPort.compatibilitySnapshotForConfig(config.toRuntimeConfigSnapshot().toResourceConfigSnapshot())
                .toRuntimeResourceCenterCompatibilitySnapshot(),
            platform = RuntimePlatform.APP_CHAT,
            trigger = IngressTrigger.USER_MESSAGE,
        )
        return ToolSourceContext.fromConfigSnapshot(
            config = config.toRuntimeConfigSnapshot(),
            mcpServers = resourceProjection.mcpServers,
            promptSkills = resourceProjection.promptSkills,
            toolSkills = resourceProjection.toolSkills,
        )
    }
}
