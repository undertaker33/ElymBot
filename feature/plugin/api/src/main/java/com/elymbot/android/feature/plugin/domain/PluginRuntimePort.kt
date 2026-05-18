package com.elymbot.android.feature.plugin.domain

interface PluginRuntimePort {
    suspend fun refreshRuntimeRegistry()
    fun runtimeLogSummary(pluginId: String): String
    fun isPluginLoaded(pluginId: String): Boolean
}
