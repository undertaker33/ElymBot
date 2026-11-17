package com.elymbot.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R
import com.elymbot.android.data.AppearanceMode

@Composable
internal fun SettingsPage(
    model: ElymBotViewModel,
    names: Map<ChatCommand, String>,
    current: Boolean,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    Column {
        Header(stringResource(R.string.elym_settings))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
                .padding(top = 7.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(SettingsEntry(R.string.elym_appearance, R.drawable.elym_settings) { appearanceOpen = true })
            SettingsGroup(SettingsEntry(R.string.elym_command_names, R.drawable.elym_sliders) { editing = true })
        }
    }
    if (current && appearanceOpen) {
        Sheet(stringResource(R.string.elym_appearance), { appearanceOpen = false }) {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                for (mode in AppearanceMode.entries) {
                    val selected = mode == appearanceMode
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected, role = Role.RadioButton, onClick = { onAppearanceModeChange(mode) })
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(stringResource(mode.label), color = Ink, fontSize = 15.sp)
                    }
                }
            }
        }
    }
    if (current && editing) {
        val edited = remember(names) { mutableStateMapOf<ChatCommand, String>().apply { ChatCommand.entries.forEach { put(it, names[it] ?: it.defaultName) } } }
        Sheet(stringResource(R.string.elym_command_names), { editing = false }) {
            ChatCommand.entries.forEach { command ->
                val label = when (command) {
                    ChatCommand.Help -> R.string.elym_command_help
                    ChatCommand.Persona -> R.string.elym_switch_persona
                    ChatCommand.Checkpoint -> R.string.elym_checkpoints
                    ChatCommand.Reset -> R.string.elym_command_reset
                    ChatCommand.Mark -> R.string.elym_command_mark
                }
                Field(stringResource(label), edited.getValue(command), { edited[command] = it })
            }
            Action(stringResource(R.string.elym_save), {
                model.perform {
                    model.service.saveCommandNames(edited.mapValues { it.value.trim().removePrefix("/") })
                    editing = false
                }
            }, primary = true, enabled = edited.values.all { it.isNotBlank() })
        }
    }
}

private val AppearanceMode.label: Int
    get() = when (this) {
        AppearanceMode.SYSTEM -> R.string.elym_appearance_system
        AppearanceMode.LIGHT -> R.string.elym_appearance_light
        AppearanceMode.DARK -> R.string.elym_appearance_dark
    }

private data class SettingsEntry(val title: Int, val icon: Int, val onClick: () -> Unit)

@Composable
private fun SettingsGroup(vararg entries: SettingsEntry) {
    Group {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = Line)
            Row(
                Modifier.fillMaxWidth().height(58.dp)
                    .clickable(role = Role.Button, onClick = entry.onClick)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(entry.icon), null, Modifier.size(24.dp), tint = Accent)
                Text(stringResource(entry.title), Modifier.weight(1f), color = Ink, fontSize = 16.sp)
                Icon(painterResource(R.drawable.elym_chevron), null, Modifier.size(16.dp), tint = Muted)
            }
        }
    }
}
