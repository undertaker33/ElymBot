package com.astrbot.android.app.integration.cron

import com.astrbot.android.feature.cron.runtime.CronJobReconciler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

interface CronRuntimeReconciliationPort {
    fun reconcileAsync(scope: CoroutineScope)
}

internal class HiltCronRuntimeReconciliationPort @Inject constructor(
    private val reconciler: CronJobReconciler,
) : CronRuntimeReconciliationPort {
    override fun reconcileAsync(scope: CoroutineScope) {
        reconciler.reconcileAsync(scope)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CronRuntimeReconciliationModule {
    @Binds
    @Singleton
    abstract fun bindCronRuntimeReconciliationPort(
        port: HiltCronRuntimeReconciliationPort,
    ): CronRuntimeReconciliationPort
}
