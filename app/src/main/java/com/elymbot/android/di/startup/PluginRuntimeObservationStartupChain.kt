
package com.elymbot.android.di.startup

import com.elymbot.android.feature.plugin.domain.PluginRuntimeObservationPort
import javax.inject.Inject

internal class PluginRuntimeObservationStartupChain @Inject constructor(
    private val pluginRuntimeObservationPort: PluginRuntimeObservationPort,
) : AppStartupChain {

    override fun run() {
        pluginRuntimeObservationPort.startObserving()
    }
}
