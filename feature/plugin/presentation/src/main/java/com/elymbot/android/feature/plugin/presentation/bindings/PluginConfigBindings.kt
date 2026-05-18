package com.elymbot.android.feature.plugin.presentation.bindings

import com.elymbot.android.model.plugin.PluginConfigStorageBoundary
import com.elymbot.android.model.plugin.PluginConfigStoreSnapshot
import com.elymbot.android.model.plugin.PluginStaticConfigSchema
import com.elymbot.android.model.plugin.PluginStaticConfigValue

interface PluginConfigBindings {
    fun getPluginStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?

    fun resolvePluginSettingsSchemaPath(pluginId: String): String?

    fun resolvePluginConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot

    fun savePluginCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot

    fun savePluginExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot
}
