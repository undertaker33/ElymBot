package com.elymbot.core.contract

import kotlinx.coroutines.flow.StateFlow

/** Optional App-facing settings capability implemented only by the QQ platform plugin. */
interface QqPlatformSettingsHandle : PluginHandle {
    val settings: StateFlow<QqPlatformSettingsSnapshot>

    /** App user changes are applied atomically against the observed revision. */
    suspend fun updateSettings(
        expectedRevision: Long,
        settings: QqPlatformSettings,
    ): QqPlatformSettingsUpdateResult
}

data class QqPlatformSettingsSnapshot(
    val revision: Long,
    val settings: QqPlatformSettings,
)

/** Persistent QQ inbound and history policy. New installations begin with no allowed sender. */
data class QqPlatformSettings(
    val ignoreSelfMessageEnabled: Boolean = true,
    val ignoreAtAllEventEnabled: Boolean = true,
    val whitelistEnabled: Boolean = true,
    /** Preserves the D07 user entry semantics: this sender is allowed in private and group chats. */
    val allowedUserIds: Set<String> = emptySet(),
    /** Narrow private-chat grant used when App binds one selected owner DM. */
    val allowedPrivateUserIds: Set<String> = emptySet(),
    val allowedGroupIds: Set<String> = emptySet(),
    val allowedGroupMembers: Set<QqGroupMember> = emptySet(),
    val adminUserIds: Set<String> = emptySet(),
    val adminGroupBypassWhitelistEnabled: Boolean = false,
    val adminPrivateBypassWhitelistEnabled: Boolean = false,
    val privateChatRequiresWakeWord: Boolean = true,
    val replyOnAtOnlyEnabled: Boolean = false,
    val wakeWordsAdminOnlyEnabled: Boolean = false,
    val wakeWords: List<String> = emptyList(),
    val triggerWords: List<String> = emptyList(),
    val keywordDetectionEnabled: Boolean = false,
    val keywordPatterns: List<String> = emptyList(),
    val publicGroupHistoryGroupIds: Set<String> = emptySet(),
    val commandAliases: Map<String, String> = emptyMap(),
    val privateRateLimit: QqPlatformRateLimitSettings? = null,
    val groupRateLimit: QqPlatformRateLimitSettings? = null,
)

data class QqGroupMember(
    val userId: String,
    val groupId: String,
)

data class QqPlatformRateLimitSettings(
    val windowMillis: Long,
    val maxMessages: Int,
    val strategy: QqPlatformRateLimitStrategy,
    val maxStashedMessages: Int,
    val maxStashAgeMillis: Long,
)

enum class QqPlatformRateLimitStrategy {
    DROP,
    STASH,
}

sealed interface QqPlatformSettingsUpdateResult {
    data class Saved(val snapshot: QqPlatformSettingsSnapshot) : QqPlatformSettingsUpdateResult

    data class Conflict(val current: QqPlatformSettingsSnapshot) : QqPlatformSettingsUpdateResult

    data class Rejected(val reason: QqPlatformSettingsRejection) : QqPlatformSettingsUpdateResult
}

enum class QqPlatformSettingsRejection {
    INVALID_SETTINGS,
    STORE_UNAVAILABLE,
}
