package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.qq.domain.QqBridgeStatePort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqLoginStateBootstrapper
import com.astrbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class QqDataBootstrapModule {
    @Binds
    @Singleton
    abstract fun bindQqLoginStateBootstrapper(
        owner: NapCatLoginLocalStoreOwner,
    ): QqLoginStateBootstrapper

    @Binds
    @Singleton
    abstract fun bindQqConversationPort(
        adapter: FeatureQqConversationPortAdapter,
    ): QqConversationPort

    @Binds
    @Singleton
    abstract fun bindQqPlatformConfigPort(
        adapter: FeatureQqPlatformConfigPortAdapter,
    ): QqPlatformConfigPort

    @Binds
    @Singleton
    abstract fun bindQqLoginRepositoryPort(
        adapter: QqLoginRepositoryAdapter,
    ): QqLoginRepositoryPort

    @Binds
    @Singleton
    abstract fun bindQqBridgeStatePort(
        owner: NapCatBridgeStateOwner,
    ): QqBridgeStatePort
}
