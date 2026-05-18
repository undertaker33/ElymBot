package com.elymbot.android.di.hilt

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.network.OkHttpRuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeNetworkModule {

    @Provides
    @Singleton
    fun provideRuntimeNetworkTransport(
        runtimeLogger: RuntimeLogger,
    ): RuntimeNetworkTransport = OkHttpRuntimeNetworkTransport(runtimeLogger = runtimeLogger)
}
