package com.astrbot.android.feature.plugin.domain

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluginHostVersion

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SupportedPluginProtocolVersion
