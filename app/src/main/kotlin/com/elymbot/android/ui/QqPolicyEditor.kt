package com.elymbot.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elymbot.android.R
import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsSnapshot

@Composable
internal fun QqPolicyEditor(
    original: QqPlatformSettingsSnapshot,
    busy: Boolean,
    failure: PlatformUiFailure?,
    dismiss: () -> Unit,
    save: (Long, QqPlatformSettings) -> Unit,
) {
    val initial = original.settings
    var draft by remember(original) { mutableStateOf(initial) }
    var invalidFields by remember(original) { mutableStateOf(emptySet<Int>()) }
    fun validity(field: Int, valid: Boolean) {
        invalidFields = if (valid) invalidFields - field else invalidFields + field
    }
    Sheet(stringResource(R.string.elym_qq_rules), { if (!busy) dismiss() }, maxContentHeightFraction = 0.9f, deliberateSwipe = true) {
        Text(stringResource(R.string.elym_qq_access), fontWeight = FontWeight.SemiBold)
        PolicySwitch(R.string.elym_qq_ignore_self, draft.ignoreSelfMessageEnabled) { draft = draft.copy(ignoreSelfMessageEnabled = it) }
        PolicySwitch(R.string.elym_qq_ignore_at_all, draft.ignoreAtAllEventEnabled) { draft = draft.copy(ignoreAtAllEventEnabled = it) }
        PolicySwitch(R.string.elym_qq_allow_list, draft.whitelistEnabled) { draft = draft.copy(whitelistEnabled = it) }
        PolicyLines(R.string.elym_qq_allowed_senders, initial.allowedUserIds) { draft = draft.copy(allowedUserIds = it.toSet()) }
        PolicyLines(R.string.elym_qq_allowed_private, initial.allowedPrivateUserIds) { draft = draft.copy(allowedPrivateUserIds = it.toSet()) }
        PolicyLines(R.string.elym_qq_allowed_groups, initial.allowedGroupIds) { draft = draft.copy(allowedGroupIds = it.toSet()) }
        PolicyLines(R.string.elym_qq_group_members, initial.allowedGroupMembers.map { "${it.groupId},${it.userId}" }) { lines ->
            val items = lines.mapNotNull { line ->
                val parts = line.split(',', limit = 2).map(String::trim)
                if (parts.size == 2 && parts.all(String::isNotBlank)) QqGroupMember(parts[1], parts[0]) else null
            }
            val valid = items.size == lines.size
            validity(R.string.elym_qq_group_members, valid)
            if (valid) draft = draft.copy(allowedGroupMembers = items.toSet())
        }
        PolicyLines(R.string.elym_qq_admins, initial.adminUserIds) { draft = draft.copy(adminUserIds = it.toSet()) }
        PolicySwitch(R.string.elym_qq_admin_group_bypass, draft.adminGroupBypassWhitelistEnabled) { draft = draft.copy(adminGroupBypassWhitelistEnabled = it) }
        PolicySwitch(R.string.elym_qq_admin_private_bypass, draft.adminPrivateBypassWhitelistEnabled) { draft = draft.copy(adminPrivateBypassWhitelistEnabled = it) }

        Text(stringResource(R.string.elym_qq_wake), fontWeight = FontWeight.SemiBold)
        PolicySwitch(R.string.elym_qq_private_wake, draft.privateChatRequiresWakeWord) { draft = draft.copy(privateChatRequiresWakeWord = it) }
        PolicySwitch(R.string.elym_qq_group_at, draft.replyOnAtOnlyEnabled) { draft = draft.copy(replyOnAtOnlyEnabled = it) }
        PolicySwitch(R.string.elym_qq_admin_wake, draft.wakeWordsAdminOnlyEnabled) { draft = draft.copy(wakeWordsAdminOnlyEnabled = it) }
        PolicyLines(R.string.elym_qq_wake_words, initial.wakeWords) { draft = draft.copy(wakeWords = it) }
        PolicyLines(R.string.elym_qq_triggers, initial.triggerWords) { draft = draft.copy(triggerWords = it) }
        PolicySwitch(R.string.elym_qq_keyword_detection, draft.keywordDetectionEnabled) { draft = draft.copy(keywordDetectionEnabled = it) }
        PolicyLines(R.string.elym_qq_keyword_patterns, initial.keywordPatterns) { draft = draft.copy(keywordPatterns = it) }
        PolicyLines(R.string.elym_qq_public_history_groups, initial.publicGroupHistoryGroupIds) {
            draft = draft.copy(publicGroupHistoryGroupIds = it.toSet())
        }
        PolicyLines(R.string.elym_qq_command_aliases, initial.commandAliases.map { (alias, command) -> "$alias=$command" }) { lines ->
            val aliases = lines.mapNotNull { line ->
                val parts = line.split('=', limit = 2).map(String::trim)
                if (parts.size == 2 && parts.all(String::isNotBlank)) parts[0] to parts[1] else null
            }.toMap()
            val valid = aliases.size == lines.size
            validity(R.string.elym_qq_command_aliases, valid)
            if (valid) draft = draft.copy(commandAliases = aliases)
        }
        QqRateLimitEditor(R.string.elym_qq_private_rate, initial.privateRateLimit) { value, valid ->
            validity(R.string.elym_qq_private_rate, valid)
            if (valid) draft = draft.copy(privateRateLimit = value)
        }
        QqRateLimitEditor(R.string.elym_qq_group_rate, initial.groupRateLimit) { value, valid ->
            validity(R.string.elym_qq_group_rate, valid)
            if (valid) draft = draft.copy(groupRateLimit = value)
        }
        val issue = when {
            invalidFields.isNotEmpty() || failure == PlatformUiFailure.InvalidBinding -> R.string.elym_invalid_input
            failure != null -> failure.label
            else -> null
        }
        if (issue != null) Text(stringResource(issue), color = Danger)
        Action(stringResource(R.string.elym_save), { save(original.revision, draft) }, primary = true,
            enabled = !busy && invalidFields.isEmpty() && failure != PlatformUiFailure.SettingsConflict)
    }
}

@Composable
private fun PolicySwitch(label: Int, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val text = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(end = 8.dp), color = Ink)
        Switch(enabled, onChange, Modifier.semanticsLabel(text))
    }
}

@Composable
private fun PolicyLines(label: Int, initial: Collection<String>, onChange: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    Field(stringResource(label), text, { value ->
        text = value
        onChange(value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
    }, multiline = true)
}

@Composable
private fun QqRateLimitEditor(
    title: Int,
    original: QqPlatformRateLimitSettings?,
    onChange: (QqPlatformRateLimitSettings?, Boolean) -> Unit,
) {
    var enabled by remember { mutableStateOf(original != null) }
    var window by remember { mutableStateOf(original?.windowMillis?.toString().orEmpty()) }
    var messages by remember { mutableStateOf(original?.maxMessages?.toString().orEmpty()) }
    var stash by remember { mutableStateOf(original?.strategy == QqPlatformRateLimitStrategy.STASH) }
    var capacity by remember { mutableStateOf(original?.maxStashedMessages?.toString().orEmpty()) }
    var age by remember { mutableStateOf(original?.maxStashAgeMillis?.toString().orEmpty()) }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(enabled, window, messages, stash, capacity, age) {
        if (!enabled) change(null, true)
        else {
            val duration = window.toLongOrNull()?.takeIf { it > 0 }
            val count = messages.toIntOrNull()?.takeIf { it > 0 }
            val limit = if (stash) capacity.toIntOrNull()?.takeIf { it > 0 } else original?.maxStashedMessages ?: 0
            val maxAge = if (stash) age.toLongOrNull()?.takeIf { it > 0 } else original?.maxStashAgeMillis ?: 1L
            val rule = if (duration != null && count != null && limit != null && maxAge != null) {
                QqPlatformRateLimitSettings(duration, count,
                    if (stash) QqPlatformRateLimitStrategy.STASH else QqPlatformRateLimitStrategy.DROP,
                    limit, maxAge)
            } else null
            change(rule, rule != null)
        }
    }
    PolicySwitch(title, enabled) { enabled = it }
    AnimatedVisibility(enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.elym_qq_rate_window), window, { window = it.filter(Char::isDigit) })
            Field(stringResource(R.string.elym_qq_rate_messages), messages, { messages = it.filter(Char::isDigit) })
            PolicySwitch(R.string.elym_qq_rate_stash, stash) { stash = it }
            if (stash) {
                Field(stringResource(R.string.elym_qq_rate_capacity), capacity, { capacity = it.filter(Char::isDigit) })
                Field(stringResource(R.string.elym_qq_rate_max_age), age, { age = it.filter(Char::isDigit) })
            }
        }
    }
}
