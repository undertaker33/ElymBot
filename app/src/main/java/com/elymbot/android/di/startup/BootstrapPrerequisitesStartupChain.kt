@file:Suppress("UNUSED_PARAMETER")

package com.elymbot.android.di.startup

import com.elymbot.android.app.integration.download.DownloadManagerBootstrapPort
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.qq.domain.QqLoginStateBootstrapper
import com.elymbot.android.feature.qq.domain.QqStartupPort
import javax.inject.Inject

internal class BootstrapPrerequisitesStartupChain @Inject constructor(
    private val qqStartupPort: QqStartupPort,
    private val qqLoginStateBootstrapper: QqLoginStateBootstrapper,
    private val downloadManagerBootstrapPort: DownloadManagerBootstrapPort,
    private val runtimeLogger: RuntimeLogger,
) : AppStartupChain {

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runtimeLogger.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        downloadManagerBootstrapPort.ensureReady()
        qqLoginStateBootstrapper.ensureReady()
        qqStartupPort.initialize()
    }
}
