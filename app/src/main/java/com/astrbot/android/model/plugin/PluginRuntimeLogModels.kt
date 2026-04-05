package com.astrbot.android.model.plugin

enum class PluginRuntimeLogCategory(
    val wireValue: String,
) {
    Dispatcher("dispatcher"),
    Execution("execution"),
    HostAction("host_action"),
    FailureGuard("failure_guard"),
    Scheduler("scheduler"),
    ResultMerger("result_merger"),
    WorkspaceApi("workspace_api"),
}

enum class PluginRuntimeLogLevel(
    val wireValue: String,
) {
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
}

data class PluginRuntimeLogRecord(
    val occurredAtEpochMillis: Long,
    val pluginId: String,
    val pluginVersion: String = "",
    val trigger: PluginTriggerSource? = null,
    val category: PluginRuntimeLogCategory,
    val level: PluginRuntimeLogLevel,
    val code: String,
    val message: String = "",
    val succeeded: Boolean? = null,
    val durationMillis: Long? = null,
    val hostAction: PluginHostAction? = null,
    val resultType: String = "",
    val metadata: Map<String, String> = emptyMap(),
)
