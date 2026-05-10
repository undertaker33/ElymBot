package com.astrbot.android.model.plugin

import com.astrbot.android.feature.plugin.runtime.JsonLikeMap
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult

data class UsingLlmTool(
    val requestId: String,
    val toolCallId: String,
    val descriptor: PluginToolDescriptor,
    val args: PluginToolArgs,
    val metadata: JsonLikeMap? = null,
)

data class ToolExecution(
    val requestId: String,
    val toolCallId: String,
    val descriptor: PluginToolDescriptor,
    val args: PluginToolArgs,
    val metadata: JsonLikeMap? = null,
)

data class LlmToolRespond(
    val requestId: String,
    val toolCallId: String,
    val descriptor: PluginToolDescriptor,
    val result: PluginToolResult,
    val metadata: JsonLikeMap? = null,
)
