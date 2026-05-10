package com.astrbot.android.model.plugin

import com.astrbot.android.feature.plugin.runtime.PluginGovernanceSnapshotMapper
import com.astrbot.android.feature.plugin.runtime.PluginV2CompiledRegistrySnapshot
import com.astrbot.android.feature.plugin.runtime.PluginV2InternalStage
import java.util.Collections

data class PluginV2RegisteredLlmHooksProjection(
    val totalCount: Int,
    val byStage: Map<PluginV2InternalStage, Int>,
    val handlerIds: List<String>,
)

object PluginLifecycleDiagnosticsStore {
    private val records = Collections.synchronizedList(mutableListOf<PluginLifecycleDiagnostic>())

    fun record(record: PluginLifecycleDiagnostic) {
        records += record
    }

    fun snapshot(): List<PluginLifecycleDiagnostic> {
        return synchronized(records) {
            records.toList()
        }
    }

    fun clear() {
        synchronized(records) {
            records.clear()
        }
    }
}

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
