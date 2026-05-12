package com.astrbot.android.feature.qq.domain

import com.astrbot.android.feature.plugin.domain.runtime.PluginExecutionBatchResult
import com.astrbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource

fun interface QqPluginExecutionPort {
    fun executeBatch(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult
}
