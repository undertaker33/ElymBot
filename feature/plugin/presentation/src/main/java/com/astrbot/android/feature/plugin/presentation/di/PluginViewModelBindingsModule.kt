package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.astrbot.android.feature.plugin.presentation.PluginCatalogEntries
import com.astrbot.android.feature.plugin.presentation.PluginRecords
import com.astrbot.android.feature.plugin.presentation.PluginRepositorySources
import com.astrbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.ui.viewmodel.DefaultPluginViewModelBindings
import com.astrbot.android.ui.viewmodel.PluginViewModelBindings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginViewModelBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPluginViewModelBindings(
        bindings: DefaultPluginViewModelBindings,
    ): PluginViewModelBindings

    @Binds
    @Singleton
    abstract fun bindPluginManagementBindings(
        bindings: DefaultPluginViewModelBindings,
    ): PluginManagementBindings

    companion object {

        @Provides
        @PluginRecords
        fun providePluginRecords(
            pluginStateRepositoryPort: PluginStateRepositoryPort,
        ): StateFlow<@JvmSuppressWildcards List<PluginInstallRecord>> = pluginStateRepositoryPort.records

        @Provides
        @PluginRepositorySources
        fun providePluginRepositorySources(
            pluginStateRepositoryPort: PluginStateRepositoryPort,
        ): StateFlow<@JvmSuppressWildcards List<PluginRepositorySource>> = pluginStateRepositoryPort.repositorySources

        @Provides
        @PluginCatalogEntries
        fun providePluginCatalogEntries(
            pluginStateRepositoryPort: PluginStateRepositoryPort,
        ): StateFlow<@JvmSuppressWildcards List<PluginCatalogEntryRecord>> = pluginStateRepositoryPort.catalogEntries

    }
}
