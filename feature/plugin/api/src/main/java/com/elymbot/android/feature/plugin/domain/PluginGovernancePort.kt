package com.elymbot.android.feature.plugin.domain

import com.elymbot.android.model.plugin.PluginFailureState

interface PluginGovernancePort {
    fun isSuspended(pluginId: String): Boolean
    fun recoverPlugin(pluginId: String)
    fun suspendPlugin(pluginId: String, reason: String)
    fun currentFailureState(pluginId: String): PluginFailureState?
}
