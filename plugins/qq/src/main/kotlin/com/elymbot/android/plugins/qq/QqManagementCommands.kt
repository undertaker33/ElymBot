package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result of consuming a QQ slash command before the host Agent handoff. */
internal data class QqSlashCommandHandling(
    val completeEvent: Boolean,
    val replyText: String? = null,
    val settingsChanged: Boolean = false,
)

/** Applies the legacy QQ command gate and the QQ-owned admin/allowlist commands. */
internal class QqManagementCommandHandler(
    private val settingsStore: QqPlatformSettingsStore,
    private val operationMutex: Mutex? = null,
    private val isCurrentAccount: (String) -> Boolean = { true },
) {
    suspend fun handle(event: OneBotInboundEvent): QqSlashCommandHandling? {
        if (!event.text.trimStart().startsWith('/')) return null
        val command = QqManagementCommand.parse(event.text)
        val currentSettings = settingsStore.settings.value.settings
        val isAdmin = event.senderId in currentSettings.adminUserIds
        val isGatedDenial = settingsStore.commandSettings.value.settings.pluginCommandsAdminOnlyEnabled && !isAdmin
        if (command == null && !isGatedDenial) return null

        val mutex = operationMutex ?: return handleCurrentAccount(event)
        return mutex.withLock {
            if (!isCurrentAccount(event.accountId)) {
                QqSlashCommandHandling(completeEvent = true)
            } else {
                handleCurrentAccount(event)
            }
        }
    }

    private suspend fun handleCurrentAccount(event: OneBotInboundEvent): QqSlashCommandHandling? {
        val currentSettings = settingsStore.settings.value.settings
        val commandSettings = settingsStore.commandSettings.value.settings
        val isAdmin = event.senderId in currentSettings.adminUserIds
        val command = QqManagementCommand.parse(event.text)

        if (commandSettings.pluginCommandsAdminOnlyEnabled && !isAdmin) return denied(commandSettings)
        if (command == null) return null
        if (!isAdmin) return denied(commandSettings)

        val action = command.toAction() ?: return QqSlashCommandHandling(
            completeEvent = true,
            replyText = command.usageText(),
        )
        return apply(action, event.senderId)
    }

    private suspend fun apply(
        action: QqManagementAction,
        actorId: String,
    ): QqSlashCommandHandling {
        var conflicts = 0
        while (conflicts < MAX_SETTINGS_CONFLICTS) {
            val snapshot = settingsStore.settings.value
            if (actorId !in snapshot.settings.adminUserIds) {
                return denied(settingsStore.commandSettings.value.settings)
            }

            val nextSettings = action.apply(snapshot.settings)
            if (nextSettings == snapshot.settings) {
                return QqSlashCommandHandling(completeEvent = true, replyText = action.acknowledgement)
            }

            when (settingsStore.updateSettings(snapshot.revision, nextSettings)) {
                is QqPlatformSettingsUpdateResult.Saved -> {
                    return QqSlashCommandHandling(
                        completeEvent = true,
                        replyText = action.acknowledgement,
                        settingsChanged = true,
                    )
                }
                is QqPlatformSettingsUpdateResult.Conflict -> conflicts += 1
                is QqPlatformSettingsUpdateResult.Rejected -> return QqSlashCommandHandling(completeEvent = false)
            }
        }
        return QqSlashCommandHandling(completeEvent = false)
    }

    private fun denied(settings: QqPlatformCommandSettings) = QqSlashCommandHandling(
        completeEvent = true,
        replyText = ADMIN_ONLY_NOTICE.takeIf { settings.replyWhenPermissionDenied },
    )

    private companion object {
        const val MAX_SETTINGS_CONFLICTS = 16
        const val ADMIN_ONLY_NOTICE = "Slash commands are restricted to administrators."
    }
}

private data class QqManagementCommand(
    val name: String,
    val rawArguments: String,
) {
    fun toAction(): QqManagementAction? {
        val arguments = rawArguments.trim().split(WHITESPACE).filter(String::isNotBlank)
        if (arguments.size != 1) return null
        val argument = arguments.single()
        return when (name) {
            "op" -> argument.takeIf(QQ_ID_PATTERN::matches)?.let { QqManagementAction.GrantAdmin(it) }
            "deop" -> argument.takeIf(QQ_ID_PATTERN::matches)?.let { QqManagementAction.RevokeAdmin(it) }
            "wl" -> parseWhitelistTarget(argument)?.let { QqManagementAction.Allow(it) }
            "dwl" -> parseWhitelistTarget(argument)?.let { QqManagementAction.Disallow(it) }
            else -> null
        }
    }

    fun usageText(): String = when (name) {
        "op" -> "Usage: /op <UID>"
        "deop" -> "Usage: /deop <UID>"
        "wl" -> "Usage: /wl <UMO>"
        else -> "Usage: /dwl <UMO>"
    }

    private fun parseWhitelistTarget(argument: String): QqWhitelistTarget? {
        if (QQ_ID_PATTERN.matches(argument)) return QqWhitelistTarget.UserOrGroup(argument)
        val match = GROUP_MEMBER_PATTERN.matchEntire(argument) ?: return null
        return QqWhitelistTarget.GroupMember(match.groupValues[1], match.groupValues[2])
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val QQ_ID_PATTERN = Regex("[0-9]{1,20}")
        private val GROUP_MEMBER_PATTERN = Regex("([0-9]{1,20})_([0-9]{1,20})")
        private val COMMAND_PATTERN = Regex("^/([^\\s/]+)(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
        private val MANAGEMENT_COMMANDS = setOf("op", "deop", "wl", "dwl")

        fun parse(text: String): QqManagementCommand? {
            val match = COMMAND_PATTERN.matchEntire(text.trim()) ?: return null
            val name = match.groupValues[1].lowercase()
            if (name !in MANAGEMENT_COMMANDS) return null
            return QqManagementCommand(name, match.groupValues.getOrElse(2) { "" })
        }
    }
}

private sealed interface QqManagementAction {
    val acknowledgement: String

    fun apply(settings: QqPlatformSettings): QqPlatformSettings

    data class GrantAdmin(val userId: String) : QqManagementAction {
        override val acknowledgement: String = "Admin authorization granted for $userId."
        override fun apply(settings: QqPlatformSettings): QqPlatformSettings =
            settings.copy(adminUserIds = settings.adminUserIds + userId)
    }

    data class RevokeAdmin(val userId: String) : QqManagementAction {
        override val acknowledgement: String = "Admin authorization revoked for $userId."
        override fun apply(settings: QqPlatformSettings): QqPlatformSettings =
            settings.copy(adminUserIds = settings.adminUserIds - userId)
    }

    data class Allow(val target: QqWhitelistTarget) : QqManagementAction {
        override val acknowledgement: String = "Added ${target.displayValue} to the whitelist."
        override fun apply(settings: QqPlatformSettings): QqPlatformSettings = target.addTo(settings)
    }

    data class Disallow(val target: QqWhitelistTarget) : QqManagementAction {
        override val acknowledgement: String = "Removed ${target.displayValue} from the whitelist."
        override fun apply(settings: QqPlatformSettings): QqPlatformSettings = target.removeFrom(settings)
    }
}

private sealed interface QqWhitelistTarget {
    val displayValue: String

    fun addTo(settings: QqPlatformSettings): QqPlatformSettings

    fun removeFrom(settings: QqPlatformSettings): QqPlatformSettings

    data class UserOrGroup(val id: String) : QqWhitelistTarget {
        override val displayValue: String = id

        override fun addTo(settings: QqPlatformSettings): QqPlatformSettings = settings.copy(
            allowedUserIds = settings.allowedUserIds + id,
            allowedGroupIds = settings.allowedGroupIds + id,
        )

        override fun removeFrom(settings: QqPlatformSettings): QqPlatformSettings = settings.copy(
            allowedUserIds = settings.allowedUserIds - id,
            allowedGroupIds = settings.allowedGroupIds - id,
        )
    }

    data class GroupMember(val userId: String, val groupId: String) : QqWhitelistTarget {
        override val displayValue: String = "${userId}_$groupId"
        private val member = QqGroupMember(userId = userId, groupId = groupId)

        override fun addTo(settings: QqPlatformSettings): QqPlatformSettings = settings.copy(
            allowedGroupMembers = settings.allowedGroupMembers + member,
        )

        override fun removeFrom(settings: QqPlatformSettings): QqPlatformSettings = settings.copy(
            allowedGroupMembers = settings.allowedGroupMembers - member,
        )
    }
}
