package com.astrbot.android.app.integration.persona

import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryPortAdapter
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
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
