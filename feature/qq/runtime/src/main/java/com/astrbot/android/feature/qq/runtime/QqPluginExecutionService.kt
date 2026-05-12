package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.plugin.domain.runtime.PluginExecutionBatchResult
import com.astrbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.qq.domain.QqPluginExecutionPort
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource

internal class QqPluginExecutionService(
    private val executeBatchBlock: (
        PluginTriggerSource,
        (PluginRuntimePlugin) -> PluginExecutionContext,
    ) -> PluginExecutionBatchResult,
) : QqPluginExecutionPort {
    override fun executeBatch(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return executeBatchBlock(trigger, contextFactory)
    }
}
