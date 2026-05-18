package com.elymbot.android.di.hilt

import com.elymbot.android.app.integration.plugin.PluginRuntimeObservationPortAdapter
import com.elymbot.android.feature.plugin.domain.PluginRuntimeObservationPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface PluginRuntimeObservationModule {
    @Binds
    @Singleton
    fun bindPluginRuntimeObservationPort(
        adapter: PluginRuntimeObservationPortAdapter,
    ): PluginRuntimeObservationPort
}
