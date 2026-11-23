package com.elymbot.android.session

import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.core.contract.BuiltInOnlyToolAccessPolicy
import com.elymbot.core.contract.ToolAccessContext
import com.elymbot.core.contract.ToolAccessDecision
import com.elymbot.core.contract.ToolAccessDenial
import com.elymbot.core.contract.ToolAccessPolicy
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolRegistrationOwner
import com.elymbot.core.contract.ToolScope

/** Rechecks the host-stamped installation and scheduled-task main-session boundary. */
internal class AppToolAccessPolicy(
    private val pluginRuntimeManager: PluginRuntimeManager,
) : ToolAccessPolicy {
    override fun canAdvertise(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): Boolean = when (owner) {
        ToolRegistrationOwner.Host -> BuiltInOnlyToolAccessPolicy.canAdvertise(definition, owner, context)
        is ToolRegistrationOwner.Plugin ->
            pluginRuntimeManager.isActive(owner.installation) &&
                context.scope in definition.scopes && routeAllows(owner, context)
    }

    override suspend fun authorize(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): ToolAccessDecision = when (owner) {
        ToolRegistrationOwner.Host -> BuiltInOnlyToolAccessPolicy.authorize(definition, owner, context)
        is ToolRegistrationOwner.Plugin -> when {
            !pluginRuntimeManager.isActive(owner.installation) ->
                ToolAccessDecision.Rejected(ToolAccessDenial.INSTALLATION_INACTIVE)
            context.scope !in definition.scopes ->
                ToolAccessDecision.Rejected(ToolAccessDenial.SESSION_NOT_AUTHORIZED)
            !routeAllows(owner, context) ->
                ToolAccessDecision.Rejected(ToolAccessDenial.ROUTE_NOT_AUTHORIZED)
            else -> ToolAccessDecision.Allowed
        }
    }

    private fun routeAllows(owner: ToolRegistrationOwner.Plugin, context: ToolAccessContext): Boolean {
        if (owner.installation.pluginId != SCHEDULED_TASKS_PLUGIN_ID) return true
        val origin = context.inputMessage?.origin ?: return false
        return context.scope == ToolScope.MAIN_SESSION &&
            context.sessionId == MAIN_SESSION_ID &&
            origin.channelId == APP_CHANNEL_ID &&
            origin.conversationId == MAIN_SESSION_ID &&
            origin.actorId == APP_USER_ID
    }

    private companion object {
        const val APP_CHANNEL_ID = "app"
        const val APP_USER_ID = "local-user"
        const val MAIN_SESSION_ID = "main"
    }
}
