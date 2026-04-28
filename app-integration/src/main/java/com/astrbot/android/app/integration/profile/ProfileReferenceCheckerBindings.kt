package com.astrbot.android.app.integration.profile

import com.astrbot.android.core.common.profile.PersonaReferenceChecker
import com.astrbot.android.core.common.profile.ProviderReferenceChecker
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ProfileReferenceCheckerBindings {

    @Provides
    @Singleton
    fun providePersonaReferenceChecker(
        botRepositoryPort: BotRepositoryPort,
    ): PersonaReferenceChecker {
        return PersonaReferenceChecker { personaId ->
            botRepositoryPort.bots.value.any { bot -> bot.defaultPersonaId == personaId }
        }
    }

    @Provides
    @Singleton
    fun provideProviderReferenceChecker(
        botRepositoryPort: BotRepositoryPort,
        configRepositoryPort: ConfigRepositoryPort,
    ): ProviderReferenceChecker {
        return ProviderReferenceChecker { providerId ->
            configRepositoryPort.profiles.value.any { config ->
                config.defaultChatProviderId == providerId ||
                    config.defaultVisionProviderId == providerId ||
                    config.defaultSttProviderId == providerId ||
                    config.defaultTtsProviderId == providerId
            } || botRepositoryPort.bots.value.any { bot -> bot.defaultProviderId == providerId }
        }
    }
}
