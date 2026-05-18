package com.elymbot.android.di.hilt

import com.elymbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatViewModelRuntimeBindingsModule {

    @Binds
    @Singleton
    abstract fun bindChatViewModelRuntimeBindings(
        bindings: DefaultChatViewModelRuntimeBindings,
    ): ChatViewModelRuntimeBindings
}
