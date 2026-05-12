
package com.astrbot.android.di.startup

import com.astrbot.android.feature.plugin.domain.PluginRuntimeObservationPort
import javax.inject.Inject

internal class PluginRuntimeObservationStartupChain @Inject constructor(
    private val pluginRuntimeObservationPort: PluginRuntimeObservationPort,
) : AppStartupChain {

    override fun run() {
        pluginRuntimeObservationPort.startObserving()
    }
}
