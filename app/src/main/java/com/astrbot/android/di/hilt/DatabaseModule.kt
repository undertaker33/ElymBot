package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideAstrBotDatabase(
        @ApplicationContext appContext: Context,
    ): AstrBotDatabase = AstrBotDatabase.get(appContext)
}
