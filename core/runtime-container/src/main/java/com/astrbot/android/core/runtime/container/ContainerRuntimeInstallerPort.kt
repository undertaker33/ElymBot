package com.astrbot.android.core.runtime.container

import kotlinx.coroutines.CoroutineScope

interface ContainerRuntimeInstallerPort {
    fun warmUpAsync(scope: CoroutineScope)
    suspend fun ensureInstalled()
}
