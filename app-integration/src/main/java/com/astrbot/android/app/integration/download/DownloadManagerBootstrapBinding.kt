package com.astrbot.android.app.integration.download

import com.astrbot.android.download.DownloadManagerBootstrap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface DownloadManagerBootstrapPort {
    fun ensureReady()
}

internal class HiltDownloadManagerBootstrapPort @Inject constructor(
    @Suppress("UNUSED_PARAMETER") bootstrap: DownloadManagerBootstrap,
) : DownloadManagerBootstrapPort {
    override fun ensureReady() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DownloadManagerBootstrapModule {
    @Binds
    @Singleton
    abstract fun bindDownloadManagerBootstrapPort(
        port: HiltDownloadManagerBootstrapPort,
    ): DownloadManagerBootstrapPort
}
