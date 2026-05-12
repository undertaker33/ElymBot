package com.astrbot.android.feature.qq.presentation

import com.astrbot.android.ui.viewmodel.DefaultQQLoginViewModelBindings
import com.astrbot.android.ui.viewmodel.QQLoginViewModelBindings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class QqPresentationBindingsModule {
    @Binds
    @Singleton
    abstract fun bindQqLoginViewModelBindings(
        bindings: DefaultQQLoginViewModelBindings,
    ): QQLoginViewModelBindings
}
