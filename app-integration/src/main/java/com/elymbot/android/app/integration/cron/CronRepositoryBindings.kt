package com.elymbot.android.app.integration.cron

import com.elymbot.android.feature.cron.data.FeatureCronJobRepositoryPortAdapter
import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
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
