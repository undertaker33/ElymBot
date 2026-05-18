package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.model.plugin.PluginExecutionContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production seam for host capability operations.
 * Injected into the capability gateway so production no longer calls the static compat API.
 */
interface PluginExecutionHostResolver {

    fun resolve(pluginId: String): PluginExecutionHostSnapshot

    fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext

    fun registeredHostToolDescriptors(
        handlers: PluginExecutionHostToolHandlers,
    ): List<PluginToolDescriptor>

    fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        handlers: PluginExecutionHostToolHandlers,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        capabilityGateway: PluginV2ToolCapabilityGateway,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot

    fun executeHostBuiltinTool(
        args: PluginToolArgs,
        handlers: PluginExecutionHostToolHandlers,
    ): PluginToolResult?
}

/**
 * Production implementation: talks to the injected host operations directly.
 * Registered as a [Singleton] in [com.elymbot.android.di.hilt.PluginHostCapabilityModule].
 */
@Singleton
class DefaultPluginExecutionHostResolver @Inject constructor(
    private val operations: DefaultPluginExecutionHostOperations,
) : PluginExecutionHostResolver {

    override fun resolve(pluginId: String): PluginExecutionHostSnapshot =
        operations.resolve(pluginId)

    override fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext =
        operations.inject(context, hostSnapshot)

    override fun registeredHostToolDescriptors(
        handlers: PluginExecutionHostToolHandlers,
    ): List<PluginToolDescriptor> =
        operations.registeredHostToolDescriptors(handlers)

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        handlers: PluginExecutionHostToolHandlers,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        capabilityGateway: PluginV2ToolCapabilityGateway,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot =
        operations.registerHostBuiltinTools(
            snapshot = snapshot,
            handlers = handlers,
            personaSnapshot = personaSnapshot,
            capabilityGateway = capabilityGateway,
            futureSourceDescriptors = futureSourceDescriptors,
            activeFutureSourceKinds = activeFutureSourceKinds,
        )

    override fun executeHostBuiltinTool(
        args: PluginToolArgs,
        handlers: PluginExecutionHostToolHandlers,
    ): PluginToolResult? =
        operations.executeHostBuiltinTool(args, handlers)
}
