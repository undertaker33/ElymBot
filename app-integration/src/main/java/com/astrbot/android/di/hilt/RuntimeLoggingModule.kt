package com.astrbot.android.di.hilt

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.common.logging.RuntimeLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeLoggingModule {

    @Provides
    @Singleton
    fun provideRuntimeLogger(): RuntimeLogger {
        return RuntimeLogger { message -> AppLogger.append(message) }
    }
}
