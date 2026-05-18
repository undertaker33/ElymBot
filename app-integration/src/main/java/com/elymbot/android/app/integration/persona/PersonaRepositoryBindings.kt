package com.elymbot.android.app.integration.persona

import com.elymbot.android.feature.persona.data.FeaturePersonaRepositoryPortAdapter
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PersonaRepositoryBindings {
    @Provides
    @Singleton
    fun providePersonaRepositoryPort(
        adapter: FeaturePersonaRepositoryPortAdapter,
    ): PersonaRepositoryPort = adapter
}
