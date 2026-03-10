package com.astrbot.android.data

import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NapCatBridgeRepository {
    private val _config = MutableStateFlow(
        NapCatBridgeConfig(
            commandPreview = "Start NapCat runtime",
            startCommand = "sh /data/local/tmp/napcat/start.sh",
            stopCommand = "sh /data/local/tmp/napcat/stop.sh",
            statusCommand = "sh /data/local/tmp/napcat/status.sh",
        ),
    )
    private val _runtimeState = MutableStateFlow(NapCatRuntimeState())

    val config: StateFlow<NapCatBridgeConfig> = _config.asStateFlow()
    val runtimeState: StateFlow<NapCatRuntimeState> = _runtimeState.asStateFlow()

    fun updateConfig(config: NapCatBridgeConfig) {
        _config.value = config
        RuntimeLogRepository.append(
            "Bridge config updated: endpoint=${config.endpoint} health=${config.healthUrl} autoStart=${config.autoStart}",
        )
    }

    fun markStarting() {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Starting",
            lastAction = "Start requested",
            lastCheckAt = System.currentTimeMillis(),
            details = "Preparing container and network installer",
            progressLabel = "Preparing start",
            progressPercent = 5,
            progressIndeterminate = false,
        )
    }

    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Running",
            lastAction = "Runtime active",
            lastCheckAt = System.currentTimeMillis(),
            pidHint = pidHint,
            details = details,
            progressLabel = "Running",
            progressPercent = 100,
            progressIndeterminate = false,
        )
    }

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    ) {
        val current = _runtimeState.value
        _runtimeState.value = current.copy(
            status = "Starting",
            lastAction = "Process started",
            lastCheckAt = System.currentTimeMillis(),
            pidHint = pidHint,
            details = details,
            progressLabel = current.progressLabel.ifBlank { "Waiting for HTTP" },
            progressIndeterminate = current.progressIndeterminate || current.progressPercent in 1..99,
        )
    }

    fun markStopped(reason: String = "Stopped manually") {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Stopped",
            lastAction = reason,
            lastCheckAt = System.currentTimeMillis(),
            pidHint = "",
            details = "Bridge is not running",
            progressLabel = "",
            progressPercent = 0,
            progressIndeterminate = false,
        )
    }

    fun markChecking() {
        _runtimeState.value = _runtimeState.value.copy(
            status = when (_runtimeState.value.status) {
                "Running" -> "Running"
                "Starting" -> "Starting"
                else -> "Checking"
            },
            lastAction = "Health check",
            lastCheckAt = System.currentTimeMillis(),
            details = "Checking NapCat runtime health",
        )
    }

    fun markError(message: String) {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Error",
            lastAction = "Bridge error",
            lastCheckAt = System.currentTimeMillis(),
            details = message,
            progressIndeterminate = false,
        )
    }

    fun updateProgress(
        label: String,
        percent: Int,
        indeterminate: Boolean,
        installerCached: Boolean = _runtimeState.value.installerCached,
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            progressLabel = label,
            progressPercent = percent.coerceIn(0, 100),
            progressIndeterminate = indeterminate,
            installerCached = installerCached,
            lastCheckAt = System.currentTimeMillis(),
        )
    }

    fun markInstallerCached(cached: Boolean) {
        _runtimeState.value = _runtimeState.value.copy(
            installerCached = cached,
            lastCheckAt = System.currentTimeMillis(),
        )
    }
}
