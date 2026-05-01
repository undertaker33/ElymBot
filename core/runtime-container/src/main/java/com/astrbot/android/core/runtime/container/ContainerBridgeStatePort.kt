package com.astrbot.android.core.runtime.container

import kotlinx.coroutines.flow.StateFlow

interface ContainerBridgeStatePort {
    val config: StateFlow<ContainerBridgeConfig>
    val runtimeState: StateFlow<ContainerRuntimeState>

    fun applyRuntimeDefaults(defaults: ContainerBridgeConfig)
    fun markStarting()
    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    )

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    )

    fun markStopped(reason: String = "Stopped manually")
    fun markChecking()
    fun markError(message: String)
    fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean)
    fun markInstallerCached(cached: Boolean)
}
