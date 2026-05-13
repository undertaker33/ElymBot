@file:Suppress("UNUSED_PARAMETER")

package com.astrbot.android.di.startup

import android.app.Application
import com.astrbot.android.AppStrings
import com.astrbot.android.app.integration.download.DownloadManagerBootstrapPort
import com.astrbot.android.core.logging.SharedRuntimeLogStore
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.feature.qq.domain.QqLoginStateBootstrapper
import com.astrbot.android.feature.qq.domain.QqStartupPort
import javax.inject.Inject

internal class BootstrapPrerequisitesStartupChain @Inject constructor(
    private val application: Application,
    private val qqStartupPort: QqStartupPort,
    private val qqLoginStateBootstrapper: QqLoginStateBootstrapper,
    private val downloadManagerBootstrapPort: DownloadManagerBootstrapPort,
) : AppStartupChain {

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            SharedRuntimeLogStore.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        downloadManagerBootstrapPort.ensureReady()
        qqLoginStateBootstrapper.ensureReady()
        qqStartupPort.initialize()
        SherpaOnnxBridge.initialize(application)
    }
}
