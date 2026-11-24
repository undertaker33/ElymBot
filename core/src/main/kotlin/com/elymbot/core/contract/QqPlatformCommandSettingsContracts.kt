package com.elymbot.core.contract

import kotlinx.coroutines.flow.StateFlow

/** Optional App-facing capability for QQ command policy, separate from the original policy DTO. */
interface QqPlatformCommandSettingsHandle : PluginHandle {
    val commandSettings: StateFlow<QqPlatformCommandSettingsSnapshot>

    /** The revision is independent from [QqPlatformSettingsHandle.updateSettings]. */
    suspend fun updateCommandSettings(
        expectedRevision: Long,
        settings: QqPlatformCommandSettings,
    ): QqPlatformCommandSettingsUpdateResult
}

data class QqPlatformCommandSettingsSnapshot(
    val revision: Long,
    val settings: QqPlatformCommandSettings,
)

data class QqPlatformCommandSettings(
    val pluginCommandsAdminOnlyEnabled: Boolean = false,
    val replyWhenPermissionDenied: Boolean = false,
)

sealed interface QqPlatformCommandSettingsUpdateResult {
    data class Saved(val snapshot: QqPlatformCommandSettingsSnapshot) : QqPlatformCommandSettingsUpdateResult

    data class Conflict(val current: QqPlatformCommandSettingsSnapshot) : QqPlatformCommandSettingsUpdateResult

    data object Rejected : QqPlatformCommandSettingsUpdateResult
}
