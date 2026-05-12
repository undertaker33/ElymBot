@file:Suppress("UNUSED_PARAMETER")

package com.astrbot.android.di.startup

import android.app.Application
import com.astrbot.android.AppStrings
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.download.DownloadManagerBootstrap
import com.astrbot.android.feature.qq.domain.QqLoginStateBootstrapper
import com.astrbot.android.feature.qq.domain.QqStartupPort
import javax.inject.Inject

internal class BootstrapPrerequisitesStartupChain @Inject constructor(
    private val application: Application,
    private val qqStartupPort: QqStartupPort,
    private val qqLoginStateBootstrapper: QqLoginStateBootstrapper,
    downloadManagerBootstrap: DownloadManagerBootstrap,
) : AppStartupChain {

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        qqLoginStateBootstrapper.ensureReady()
        qqStartupPort.initialize()
        SherpaOnnxBridge.initialize(application)
    }
}
