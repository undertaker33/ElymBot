package com.elymbot.android.app.integration.resource

import com.elymbot.android.feature.resource.data.FeatureResourceCenterPortAdapter
import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ResourceRepositoryBindings {

    @Provides
    @Singleton
    fun provideResourceCenterPort(
        adapter: FeatureResourceCenterPortAdapter,
    ): ResourceCenterPort = adapter
}
