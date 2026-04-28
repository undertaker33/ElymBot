package com.astrbot.android.di.hilt

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
internal object QqRepositoryPortModule {

    @Provides
    @Singleton
    fun provideQqConversationPort(
        adapter: FeatureQqConversationPortAdapter,
    ): QqConversationPort = adapter

    @Provides
    @Singleton
    fun provideQqPlatformConfigPort(
        adapter: FeatureQqPlatformConfigPortAdapter,
    ): QqPlatformConfigPort = adapter
}
