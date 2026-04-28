package com.astrbot.android.di.hilt

import com.astrbot.android.feature.chat.data.FeatureConversationRepositoryPortAdapter
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
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
    fun provideConversationRepositoryPort(
        adapter: FeatureConversationRepositoryPortAdapter,
    ): ConversationRepositoryPort = adapter
}
