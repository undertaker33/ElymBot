package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext

interface PluginHostCapabilityGateway {
    fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult

    fun injectContext(context: PluginExecutionContext): PluginExecutionContext
}

class DefaultPluginHostCapabilityGateway(
    private val hostActionExecutor: ExternalPluginHostActionExecutor = ExternalPluginHostActionExecutor(),
) : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        return hostActionExecutor.execute(
            pluginId = pluginId,
            request = request,
            context = context,
        )
    }

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext {
        return PluginExecutionHostApi.inject(
            context = context,
            hostSnapshot = PluginExecutionHostApi.resolve(context.pluginId),
        )
    }
}
