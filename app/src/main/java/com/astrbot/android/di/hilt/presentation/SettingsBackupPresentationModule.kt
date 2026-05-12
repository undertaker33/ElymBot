package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.feature.settings.AppSettingsBackupPortAdapter
import com.astrbot.android.feature.settings.api.SettingsBackupPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsBackupPresentationModule {
    @Binds
    abstract fun bindSettingsBackupPort(
        adapter: AppSettingsBackupPortAdapter,
    ): SettingsBackupPort
}
