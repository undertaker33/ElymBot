package com.elymbot.android.feature.plugin.domain.cleanup

import com.elymbot.android.feature.plugin.data.PluginStoragePaths
import com.elymbot.android.feature.plugin.data.config.PluginConfigStorage
import com.elymbot.android.feature.plugin.data.state.PluginStateStore
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginUninstallPolicy
import javax.inject.Inject
import javax.inject.Singleton

interface PluginRuntimeArtifactCleaner {
    fun cleanup(pluginId: String)
}

interface PluginDataCleanupService {
    fun cleanupForUninstall(
        record: PluginInstallRecord,
        policy: PluginUninstallPolicy,
    )
}

@Singleton
class DefaultPluginDataCleanupService @Inject constructor(
    private val storagePaths: PluginStoragePaths,
    private val configStorage: PluginConfigStorage,
    private val stateStore: PluginStateStore,
    private val runtimeArtifactCleaner: PluginRuntimeArtifactCleaner,
) : PluginDataCleanupService {
    override fun cleanupForUninstall(
        record: PluginInstallRecord,
        policy: PluginUninstallPolicy,
    ) {
        runtimeArtifactCleaner.cleanup(record.pluginId)
        deletePath(record.localPackagePath)
        deletePath(record.extractedDir)
        if (policy == PluginUninstallPolicy.REMOVE_DATA) {
            configStorage.deleteSnapshot(record.pluginId)
            stateStore.deleteByPluginId(record.pluginId)
            deletePath(storagePaths.privateDir(record.pluginId).absolutePath)
        }
    }

    private fun deletePath(path: String) {
        if (path.isBlank()) return
        kotlin.runCatching {
            java.io.File(path).takeIf(java.io.File::exists)?.deleteRecursively()
        }
    }
}
