package com.astrbot.android.app.integration.cron

import com.astrbot.android.feature.cron.data.FeatureCronJobRepositoryPortAdapter
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CronRepositoryBindings {

    @Provides
    @Singleton
    fun provideCronJobRepositoryPort(
        adapter: FeatureCronJobRepositoryPortAdapter,
    ): CronJobRepositoryPort = adapter
}
