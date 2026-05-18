package com.elymbot.android.feature.qq.presentation

import com.elymbot.android.ui.viewmodel.DefaultQQLoginViewModelBindings
import com.elymbot.android.ui.viewmodel.QQLoginViewModelBindings
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
