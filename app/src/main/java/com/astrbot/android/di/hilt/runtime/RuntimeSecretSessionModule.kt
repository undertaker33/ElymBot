package com.astrbot.android.di.hilt.runtime

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.secret.DefaultRuntimeSecretStore
import com.astrbot.android.core.runtime.secret.RuntimeSecretStore
import com.astrbot.android.core.runtime.session.DefaultSessionLockCoordinator
import com.astrbot.android.core.runtime.session.SessionLockCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RuntimeSecretSessionBindsModule {
    @Binds
    @Singleton
    abstract fun bindSessionLockCoordinator(
        impl: DefaultSessionLockCoordinator,
    ): SessionLockCoordinator
}

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeSecretSessionProvidesModule {
    @Provides
    @Singleton
    fun provideRuntimeSecretStore(
        @ApplicationContext context: Context,
        runtimeLogger: RuntimeLogger,
    ): RuntimeSecretStore {
        return DefaultRuntimeSecretStore(
            filesDir = context.applicationContext.filesDir,
            logger = runtimeLogger,
        )
    }
}
