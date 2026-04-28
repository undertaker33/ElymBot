package com.astrbot.android.app.integration.bot

import com.astrbot.android.feature.bot.data.FeatureBotRepositoryPortAdapter
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object BotRepositoryBindings {

    @Provides
    @Singleton
    fun provideBotRepositoryPort(
        adapter: FeatureBotRepositoryPortAdapter,
    ): BotRepositoryPort = adapter
}
