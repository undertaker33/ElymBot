package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.plugin.domain.cleanup.PluginRuntimeArtifactCleaner
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class DefaultPluginRuntimeArtifactCleaner @Inject constructor(
    private val runtimeLoaderProvider: Provider<PluginV2RuntimeLoader>,
) : PluginRuntimeArtifactCleaner {
    override fun cleanup(pluginId: String) {
        runCatching {
            runBlocking {
                runtimeLoaderProvider.get().unload(pluginId)
            }
        }
    }
}
