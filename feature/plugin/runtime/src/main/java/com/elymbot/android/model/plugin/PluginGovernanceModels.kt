package com.elymbot.android.model.plugin

import com.elymbot.android.feature.plugin.runtime.PluginGovernanceSnapshotMapper
import com.elymbot.android.feature.plugin.runtime.PluginV2CompiledRegistrySnapshot
import com.elymbot.android.feature.plugin.runtime.PluginV2InternalStage

data class PluginV2RegisteredLlmHooksProjection(
    val totalCount: Int,
    val byStage: Map<PluginV2InternalStage, Int>,
    val handlerIds: List<String>,
)

fun PluginInstallRecord.resolveGovernanceSnapshot(): PluginGovernanceSnapshot {
    return PluginGovernanceSnapshotMapper.map(this)
}

fun PluginV2CompiledRegistrySnapshot.projectRegisteredLlmHooks(): PluginV2RegisteredLlmHooksProjection {
    val llmHandlers = handlerRegistry.llmHookHandlers
    return PluginV2RegisteredLlmHooksProjection(
        totalCount = llmHandlers.size,
        byStage = llmHandlers
            .groupBy { handler -> handler.surface.stage }
            .mapValues { (_, handlers) -> handlers.size },
        handlerIds = llmHandlers.map { handler -> handler.handlerId },
    )
}
