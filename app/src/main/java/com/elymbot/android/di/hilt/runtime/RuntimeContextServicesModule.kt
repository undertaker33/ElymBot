package com.elymbot.android.di.hilt.runtime

import com.elymbot.android.core.runtime.context.DefaultRuntimeContextResolverPort
import com.elymbot.android.core.runtime.context.RuntimeContextDataPort
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.di.ProductionRuntimeContextDataPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeContextServicesModule {

    @Provides
    @Singleton
    fun provideRuntimeContextDataPort(
        dataPort: ProductionRuntimeContextDataPort,
    ): RuntimeContextDataPort = dataPort

    @Provides
    @Singleton
    fun provideRuntimeContextResolverPort(
        resolver: DefaultRuntimeContextResolverPort,
    ): RuntimeContextResolverPort = resolver
}
