package com.elymbot.android.di.hilt.presentation

import com.elymbot.android.feature.plugin.domain.PluginStateRepositoryPort
import com.elymbot.android.feature.plugin.presentation.PluginCatalogEntries
import com.elymbot.android.feature.plugin.presentation.PluginRecords
import com.elymbot.android.feature.plugin.presentation.PluginRepositorySources
import com.elymbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
import com.elymbot.android.model.plugin.PluginCatalogEntryRecord
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginRepositorySource
import com.elymbot.android.ui.viewmodel.DefaultPluginViewModelBindings
import com.elymbot.android.ui.viewmodel.PluginViewModelBindings
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
