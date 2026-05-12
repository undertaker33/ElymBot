package com.astrbot.android.feature.plugin.presentation.bindings

import com.astrbot.android.feature.plugin.domain.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.domain.PluginLogMaintenancePort
import com.astrbot.android.feature.plugin.domain.PluginRuntimeLogPresentationPort
import kotlinx.coroutines.flow.Flow

interface PluginGovernanceBindings {
    val governanceReadModels: Flow<Map<String, PluginGovernanceReadModel>>
    val logBus: PluginRuntimeLogPresentationPort
    val logMaintenanceService: PluginLogMaintenancePort

    fun getPluginGovernance(pluginId: String): PluginGovernanceReadModel?

    fun getPluginGovernanceSilently(pluginId: String): PluginGovernanceReadModel?
}
