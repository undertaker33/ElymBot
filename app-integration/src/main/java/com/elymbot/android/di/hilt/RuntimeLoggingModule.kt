package com.elymbot.android.di.hilt

import android.content.Context
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.logging.DefaultRuntimeLogStore
import com.elymbot.android.core.logging.DefaultRuntimeLogMaintenanceService
import com.elymbot.android.core.logging.RuntimeLogCleanupSettingsStore
import com.elymbot.android.core.logging.RuntimeLogMaintenanceService
import com.elymbot.android.core.logging.RuntimeLogStore
import com.elymbot.android.core.logging.SharedPreferencesRuntimeLogCleanupSettingsStore
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
        return DefaultRuntimeLogStore()
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
