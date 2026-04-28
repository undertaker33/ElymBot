package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.cache.DefaultRuntimeCacheMaintenanceService
import com.astrbot.android.core.runtime.cache.RuntimeCacheMaintenancePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeCacheModule {

    @Provides
    @Singleton
    fun provideRuntimeCacheMaintenancePort(
        @ApplicationContext context: Context,
        runtimeLogger: RuntimeLogger,
    ): RuntimeCacheMaintenancePort {
        return DefaultRuntimeCacheMaintenanceService(
            filesDir = context.applicationContext.filesDir,
            logger = runtimeLogger,
        )
    }
}
