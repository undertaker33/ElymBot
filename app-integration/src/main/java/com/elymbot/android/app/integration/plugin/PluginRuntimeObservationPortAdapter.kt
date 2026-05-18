package com.elymbot.android.app.integration.plugin

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.di.hilt.ApplicationScope
import com.elymbot.android.feature.plugin.domain.PluginRuntimeObservationPort
import com.elymbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.elymbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.elymbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.elymbot.android.model.plugin.PluginInstallRecord
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
internal class PluginRuntimeObservationPortAdapter @Inject constructor(
    private val pluginRuntimeLoader: PluginV2RuntimeLoader,
    private val pluginLifecycleManager: PluginV2LifecycleManager,
    private val pluginStateRepository: PluginStateRepositoryPort,
    @ApplicationScope private val appScope: CoroutineScope,
    private val runtimeLogger: RuntimeLogger,
) : PluginRuntimeObservationPort {
    private var pluginRuntimeLoaderSyncJob: Job? = null

    override fun startObserving() {
        if (pluginRuntimeLoaderSyncJob?.isActive == true) return
        pluginRuntimeLoaderSyncJob = appScope.launch(Dispatchers.IO) {
            pluginStateRepository.records.collectLatest { currentRecords ->
                try {
                    syncPluginRuntimeRecordsAndSignalReady(currentRecords)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    runtimeLogger.append(
                        "Plugin v2 runtime loader sync failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private suspend fun syncPluginRuntimeRecordsAndSignalReady(
        records: List<PluginInstallRecord>,
    ) {
        pluginRuntimeLoader.sync(records).also {
            pluginLifecycleManager.onElymBotLoaded()
            pluginLifecycleManager.onPlatformLoaded(ANDROID_PLATFORM_INSTANCE_KEY)
        }
    }

    private companion object {
        const val ANDROID_PLATFORM_INSTANCE_KEY: String = "elymbot-android"
    }
}
