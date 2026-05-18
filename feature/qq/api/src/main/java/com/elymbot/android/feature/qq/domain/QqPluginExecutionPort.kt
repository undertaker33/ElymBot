package com.elymbot.android.feature.qq.domain

import com.elymbot.android.feature.plugin.domain.runtime.PluginExecutionBatchResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginTriggerSource

fun interface QqPluginExecutionPort {
    fun executeBatch(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult
}
