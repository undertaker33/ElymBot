package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.secret.RuntimeSecretStore
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.domain.PluginWorkspacePathPort
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.qq.domain.QqPresentationLogPort
import com.astrbot.android.feature.qq.domain.QqPluginExecutionPort
import com.astrbot.android.feature.qq.domain.QqWebUiCredentialPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object QqPhase24PortModule {
    @Provides
    @Singleton
    fun providePluginWorkspacePathPort(
        @ApplicationContext appContext: Context,
    ): PluginWorkspacePathPort = PluginWorkspacePathPort { pluginId ->
        PluginStoragePaths.fromFilesDir(appContext.filesDir)
            .privateDir(pluginId)
            .absolutePath
    }

    @Provides
    fun provideQqWebUiCredentialPort(
        runtimeSecretStore: RuntimeSecretStore,
    ): QqWebUiCredentialPort = QqWebUiCredentialPort {
        runtimeSecretStore.getOrCreateWebUiToken()
    }

    @Provides
    fun provideQqPresentationLogPort(
        runtimeLogger: RuntimeLogger,
    ): QqPresentationLogPort = QqPresentationLogPort { message ->
        runtimeLogger.append(message)
    }

    @Provides
    @Singleton
    fun provideQqPluginExecutionPort(
        engine: PluginExecutionEngine,
        pluginCatalog: ExternalPluginRuntimeCatalog,
    ): QqPluginExecutionPort = QqPluginExecutionPort { trigger, contextFactory ->
        engine.executeBatch(
            trigger = trigger,
            plugins = pluginCatalog.plugins(),
            contextFactory = contextFactory,
        )
    }
}
