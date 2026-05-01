package com.astrbot.android.core.runtime.container

data class ContainerBridgeConfig(
    val runtimeMode: String = "local_container",
    val endpoint: String = "ws://127.0.0.1:6199/ws",
    val healthUrl: String = "http://127.0.0.1:6099",
    val autoStart: Boolean = false,
    val commandPreview: String = "Start NapCat runtime",
)

enum class ContainerRuntimeStatus(val label: String) {
    STOPPED("Stopped"),
    CHECKING("Checking"),
    STARTING("Starting"),
    RUNNING("Running"),
    ERROR("Error"),
}

data class ContainerRuntimeState(
    val statusType: ContainerRuntimeStatus = ContainerRuntimeStatus.STOPPED,
    val lastAction: String = "",
    val lastCheckAt: Long = 0L,
    val pidHint: String = "",
    val details: String = "Bridge is not connected",
    val progressLabel: String = "",
    val progressPercent: Int = 0,
    val progressIndeterminate: Boolean = false,
    val installerCached: Boolean = false,
) {
    val status: String
        get() = statusType.label

    fun blocksAutoStart(): Boolean {
        return statusType == ContainerRuntimeStatus.RUNNING || statusType == ContainerRuntimeStatus.STARTING
    }
}
