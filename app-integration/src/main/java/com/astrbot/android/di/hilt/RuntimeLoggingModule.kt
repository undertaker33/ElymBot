package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.logging.DefaultRuntimeLogMaintenanceService
import com.astrbot.android.core.logging.RuntimeLogCleanupSettingsStore
import com.astrbot.android.core.logging.RuntimeLogMaintenanceService
import com.astrbot.android.core.logging.RuntimeLogStore
import com.astrbot.android.core.logging.SharedRuntimeLogStore
import com.astrbot.android.core.logging.SharedPreferencesRuntimeLogCleanupSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeLoggingModule {

    @Provides
    @Singleton
    fun provideRuntimeLogStore(): RuntimeLogStore {
        return SharedRuntimeLogStore
    }

    @Provides
    @Singleton
    fun provideRuntimeLogger(
        runtimeLogStore: RuntimeLogStore,
    ): RuntimeLogger {
        return RuntimeLogger { message -> runtimeLogStore.append(message) }
    }

    @Provides
    @Singleton
    fun provideRuntimeLogCleanupSettingsStore(
        @ApplicationContext context: Context,
    ): RuntimeLogCleanupSettingsStore {
        val prefs = context.getSharedPreferences("runtime_log_cleanup", Context.MODE_PRIVATE)
        return SharedPreferencesRuntimeLogCleanupSettingsStore(prefs)
    }

    @Provides
    @Singleton
    fun provideRuntimeLogMaintenanceService(
        settingsStore: RuntimeLogCleanupSettingsStore,
    ): RuntimeLogMaintenanceService {
        return DefaultRuntimeLogMaintenanceService(settingsStore)
    }
}
