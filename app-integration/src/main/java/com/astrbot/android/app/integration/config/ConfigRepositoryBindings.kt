package com.astrbot.android.app.integration.config

import com.astrbot.android.feature.config.data.FeatureConfigRepositoryPortAdapter
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ConfigRepositoryBindings {

    @Provides
    @Singleton
    fun provideConfigRepositoryPort(
        adapter: FeatureConfigRepositoryPortAdapter,
    ): ConfigRepositoryPort = adapter
}
