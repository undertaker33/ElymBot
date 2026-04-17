package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ResourceCenterRepository
import com.astrbot.android.runtime.context.IngressTrigger
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.context.RuntimeSkillProjectionResolver

/**
 * Aggregates all [FutureToolSourceProvider] instances and produces a unified list
 * of [PluginToolDescriptor]s for the centralized tool registry compiler.
 *
 * This is the single gateway that [PluginV2ToolSourceGateway] and the host capability
 * system delegate to when resolving non-PLUGIN_V2/HOST_BUILTIN source kinds.
 */
class FutureToolSourceRegistry(
    private val providers: List<FutureToolSourceProvider> = defaultProviders(),
) {
    private val providersByKind: Map<PluginToolSourceKind, FutureToolSourceProvider> =
        providers.associateBy { it.sourceKind }

    suspend fun collectToolDescriptors(
        configProfileId: String,
    ): List<PluginToolDescriptor> {
        val context = ToolSourceRegistryIngestContext(toolSourceContext = contextForConfig(configProfileId))
        return collectToolDescriptors(context.toolSourceContext)
    }

    suspend fun collectToolDescriptors(
        toolSourceContext: ToolSourceContext,
    ): List<PluginToolDescriptor> {
        val context = ToolSourceRegistryIngestContext(toolSourceContext = toolSourceContext)
        return providers.flatMap { provider ->
            provider.listBindings(context).map { binding -> binding.descriptor }
        }
    }

    suspend fun isAvailable(
        sourceKind: PluginToolSourceKind,
        ownerId: String,
        configProfileId: String,
    ): Boolean {
        val provider = providersByKind[sourceKind] ?: return false
        val identity = ToolSourceIdentity(
            sourceKind = sourceKind,
            ownerId = ownerId,
            sourceRef = "",
            displayName = "",
        )
        val availability = provider.availabilityOf(
            identity = identity,
            context = ToolSourceAvailabilityContext(toolSourceContext = contextForConfig(configProfileId)),
        )
        return availability.providerReachable && availability.capabilityAllowed
    }

    suspend fun isAvailable(
        sourceKind: PluginToolSourceKind,
        ownerId: String,
        toolSourceContext: ToolSourceContext,
    ): Boolean {
        val provider = providersByKind[sourceKind] ?: return false
        val identity = ToolSourceIdentity(
            sourceKind = sourceKind,
            ownerId = ownerId,
            sourceRef = "",
            displayName = "",
        )
        val availability = provider.availabilityOf(
            identity = identity,
            context = ToolSourceAvailabilityContext(toolSourceContext = toolSourceContext),
        )
        return availability.providerReachable && availability.capabilityAllowed
    }

    suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult? {
        val provider = providersByKind[request.identity.sourceKind] ?: return null
        return provider.invoke(request)
    }

    fun providerFor(sourceKind: PluginToolSourceKind): FutureToolSourceProvider? {
        return providersByKind[sourceKind]
    }

    companion object {
        fun defaultProviders(): List<FutureToolSourceProvider> = listOf(
            McpToolSourceProvider(),
            SkillToolSourceProvider(),
            ActiveCapabilityToolSourceProvider(),
            ContextStrategyToolSourceProvider(),
            WebSearchToolSourceProvider(),
        )

        fun contextForConfig(configProfileId: String): ToolSourceContext {
            val config = ConfigRepository.resolve(configProfileId)
            val resourceProjection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
                snapshot = ResourceCenterRepository.compatibilitySnapshotForConfig(config),
                platform = RuntimePlatform.APP_CHAT,
                trigger = IngressTrigger.USER_MESSAGE,
            )
            return ToolSourceContext.fromConfigProfile(
                config = config,
                mcpServers = resourceProjection.mcpServers,
                promptSkills = resourceProjection.promptSkills,
                toolSkills = resourceProjection.toolSkills,
            )
        }
    }
}
