package com.astrbot.android.di

import com.astrbot.android.core.runtime.container.ContainerBridgeStatePort
import com.astrbot.android.feature.qq.data.NapCatBridgeRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import kotlinx.coroutines.flow.StateFlow

internal object ProductionContainerBridgeStatePort : ContainerBridgeStatePort {
    override val config: StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config
    override val runtimeState: StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState

    override fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        NapCatBridgeRepository.applyRuntimeDefaults(defaults)
    }

    override fun markStarting() = NapCatBridgeRepository.markStarting()

    override fun markRunning(pidHint: String, details: String) {
        NapCatBridgeRepository.markRunning(pidHint = pidHint, details = details)
    }

    override fun markProcessRunning(pidHint: String, details: String) {
        NapCatBridgeRepository.markProcessRunning(pidHint = pidHint, details = details)
    }

    override fun markStopped(reason: String) = NapCatBridgeRepository.markStopped(reason)

    override fun markChecking() = NapCatBridgeRepository.markChecking()

    override fun markError(message: String) = NapCatBridgeRepository.markError(message)

    override fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean) {
        NapCatBridgeRepository.updateProgress(
            label = label,
            percent = percent,
            indeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    override fun markInstallerCached(cached: Boolean) = NapCatBridgeRepository.markInstallerCached(cached)
}
