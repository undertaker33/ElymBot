package com.elymbot.android.app.integration.config

import com.elymbot.android.feature.config.data.RoomPhase3DataTransactionService
import com.elymbot.android.feature.config.domain.Phase3DataTransactionService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConfigTransactionBindings {

    @Binds
    @Singleton
    abstract fun bindPhase3DataTransactionService(
        service: RoomPhase3DataTransactionService,
    ): Phase3DataTransactionService
}
