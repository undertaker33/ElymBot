
package com.astrbot.android.di.startup

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.di.hilt.ApplicationScope
import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSyncResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class PluginRuntimeObservationStartupChain @Inject constructor(
    private val pluginRuntimeLoader: PluginV2RuntimeLoader,
    private val pluginLifecycleManager: PluginV2LifecycleManager,
    private val pluginStateRepository: PluginStateRepositoryPort,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppStartupChain {

    private var pluginRuntimeLoaderSyncJob: Job? = null

    override fun run() {
        pluginRuntimeLoaderSyncJob = appScope.observePluginRuntimeRecords(
            records = pluginStateRepository.records,
            sync = { currentRecords ->
                syncPluginRuntimeRecordsAndSignalReady(
                    records = currentRecords,
                    loader = pluginRuntimeLoader,
                    lifecycleManager = pluginLifecycleManager,
                )
            },
        )
    }
}

internal suspend fun syncPluginRuntimeRecordsAndSignalReady(
    records: List<PluginInstallRecord>,
    loader: PluginV2RuntimeLoader,
    lifecycleManager: PluginV2LifecycleManager,
    platformInstanceKey: String = ANDROID_PLATFORM_INSTANCE_KEY,
): PluginV2RuntimeSyncResult {
    return loader.sync(records).also {
        lifecycleManager.onAstrbotLoaded()
        lifecycleManager.onPlatformLoaded(platformInstanceKey)
    }
}

internal const val ANDROID_PLATFORM_INSTANCE_KEY: String = "astrbot-android"

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
