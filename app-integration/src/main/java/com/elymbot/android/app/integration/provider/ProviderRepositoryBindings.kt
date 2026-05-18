package com.elymbot.android.app.integration.provider

import com.elymbot.android.feature.provider.data.FeatureProviderRepositoryPortAdapter
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ProviderRepositoryBindings {
    @Provides
    @Singleton
    fun provideProviderRepositoryPort(
        adapter: FeatureProviderRepositoryPortAdapter,
    ): ProviderRepositoryPort = adapter
}
