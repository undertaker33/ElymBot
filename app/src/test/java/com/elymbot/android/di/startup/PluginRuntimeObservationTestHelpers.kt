package com.elymbot.android.di.startup

import com.elymbot.android.core.common.logging.RuntimeLogRepository
import com.elymbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.elymbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.elymbot.android.feature.plugin.runtime.PluginV2RuntimeSyncResult
import com.elymbot.android.model.plugin.PluginInstallRecord
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal suspend fun syncPluginRuntimeRecordsAndSignalReady(
    records: List<PluginInstallRecord>,
    loader: PluginV2RuntimeLoader,
    lifecycleManager: PluginV2LifecycleManager,
    platformInstanceKey: String = ANDROID_PLATFORM_INSTANCE_KEY,
): PluginV2RuntimeSyncResult {
    return loader.sync(records).also {
        lifecycleManager.onElymBotLoaded()
        lifecycleManager.onPlatformLoaded(platformInstanceKey)
    }
}

internal const val ANDROID_PLATFORM_INSTANCE_KEY: String = "elymbot-android"

internal fun CoroutineScope.observePluginRuntimeRecords(
    records: StateFlow<List<PluginInstallRecord>>,
    sync: suspend (List<PluginInstallRecord>) -> PluginV2RuntimeSyncResult,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Job {
    return launch(dispatcher) {
        records.collectLatest { currentRecords ->
            try {
                sync(currentRecords)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                RuntimeLogRepository.append(
                    "Plugin v2 runtime loader sync failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }
}
