package com.elymbot.android.di.hilt

import com.elymbot.android.model.NapCatBridgeConfig
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BotLoginState

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class QqLoginState

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BridgeConfig

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BridgeRuntimeState

/**
 * Dependency-driven bridge config save operation, injected into BridgeViewModel
 * so the shell never imports feature/data packages directly.
 */
fun interface BridgeConfigSaver {
    fun save(config: NapCatBridgeConfig)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginRecords

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginRepositorySources

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginCatalogEntries

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginGovernanceReadModels

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class TtsVoiceAssets
