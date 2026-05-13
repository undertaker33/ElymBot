package com.astrbot.android.di.startup

import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.astrbot.android.app.integration.cron.CronRuntimeReconciliationPort
import com.astrbot.android.di.hilt.ApplicationScope
import com.astrbot.android.feature.qq.domain.QqStartupPort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal class RuntimeLaunchStartupChain @Inject constructor(
    private val qqStartupPort: QqStartupPort,
    private val containerRuntimeInstaller: ContainerRuntimeInstaller,
    private val cronRuntimeReconciliationPort: CronRuntimeReconciliationPort,
    private val runtimeLogger: RuntimeLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppStartupChain {

    override fun run() {
        qqStartupPort.start()
        containerRuntimeInstaller.warmUpAsync(appScope)
        cronRuntimeReconciliationPort.reconcileAsync(appScope)
        runtimeLogger.append("App started")
    }
}
