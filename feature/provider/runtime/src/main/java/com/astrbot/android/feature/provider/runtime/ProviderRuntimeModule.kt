package com.astrbot.android.feature.provider.runtime

import com.astrbot.android.feature.provider.api.runtime.ProviderRuntimePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProviderRuntimeModule {
    @Binds
    @Singleton
    abstract fun bindProviderRuntimePort(
        runtimePort: DefaultProviderRuntimePort,
    ): ProviderRuntimePort
}
