package com.elymbot.android.feature.plugin.domain

fun interface PluginWorkspacePathPort {
    fun privateRootPath(pluginId: String): String
}
