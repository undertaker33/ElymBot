package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.plugin.domain.runtime.PluginExecutionBatchResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.elymbot.android.feature.qq.domain.QqPluginExecutionPort
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginTriggerSource

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
