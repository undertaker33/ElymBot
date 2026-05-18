package com.elymbot.android.data.db

import com.elymbot.android.model.plugin.PluginConfigStorageJson
import com.elymbot.android.model.plugin.PluginConfigStoreSnapshot

fun PluginConfigSnapshotEntity.toSnapshot(): PluginConfigStoreSnapshot {
    return PluginConfigStoreSnapshot(
        coreValues = PluginConfigStorageJson.decodeValues(coreConfigJson),
        extensionValues = PluginConfigStorageJson.decodeValues(extensionConfigJson),
    )
}

fun PluginConfigStoreSnapshot.toEntity(
    pluginId: String,
    updatedAt: Long,
): PluginConfigSnapshotEntity {
    return PluginConfigSnapshotEntity(
        pluginId = pluginId,
        coreConfigJson = PluginConfigStorageJson.encodeValues(coreValues),
        extensionConfigJson = PluginConfigStorageJson.encodeValues(extensionValues),
        updatedAt = updatedAt,
    )
}
