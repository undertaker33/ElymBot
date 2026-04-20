package com.astrbot.android.di.hilt

import com.astrbot.android.feature.bot.data.FeatureBotRepositoryPortAdapter
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.data.FeatureConversationRepositoryPortAdapter
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryPortAdapter
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryPortAdapter
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryPortAdapter
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.data.FeatureQqConversationPortAdapter
import com.astrbot.android.feature.qq.data.FeatureQqPlatformConfigPortAdapter
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryPortModule {

    @Provides
    @Singleton
    fun provideBotRepositoryPort(): BotRepositoryPort = FeatureBotRepositoryPortAdapter()

    @Provides
    @Singleton
    fun provideConfigRepositoryPort(): ConfigRepositoryPort = FeatureConfigRepositoryPortAdapter()

    @Provides
    @Singleton
    fun providePersonaRepositoryPort(): PersonaRepositoryPort = FeaturePersonaRepositoryPortAdapter()

    @Provides
    @Singleton
    fun provideProviderRepositoryPort(): ProviderRepositoryPort = FeatureProviderRepositoryPortAdapter()

    @Provides
    @Singleton
    fun provideConversationRepositoryPort(): ConversationRepositoryPort = FeatureConversationRepositoryPortAdapter()

    @Provides
    @Singleton
    fun provideQqConversationPort(): QqConversationPort = FeatureQqConversationPortAdapter()

    @Provides
    @Singleton
    fun provideQqPlatformConfigPort(): QqPlatformConfigPort = FeatureQqPlatformConfigPortAdapter()
}
