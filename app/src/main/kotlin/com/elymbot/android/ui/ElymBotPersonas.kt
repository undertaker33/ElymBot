package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R

@Composable
internal fun PersonaForm(persona: PersonaCard, dismiss: () -> Unit, save: (PersonaCard) -> Unit) {
    var edited by remember(persona) { mutableStateOf(persona) }
    var tags by remember(persona) { mutableStateOf(persona.tags.joinToString("、")) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) edited = edited.copy(cover = it, coverPosition = ImagePosition(), avatarPosition = ImagePosition())
    }
    val creating = persona.id.isEmpty()
    Sheet(stringResource(if (creating) R.string.elym_create_persona else R.string.elym_edit_persona), dismiss) {
        Field(stringResource(R.string.elym_name), edited.name, { edited = edited.copy(name = it.take(24)) })
        Field(stringResource(R.string.elym_description), edited.description, { edited = edited.copy(description = it.take(40)) })
        Field(stringResource(R.string.elym_tags), tags, { tags = it.take(60) }, stringResource(R.string.elym_tags_hint))
        Field(stringResource(R.string.elym_prompt), edited.prompt, { edited = edited.copy(prompt = it.take(1200)) }, multiline = true)
        Text(stringResource(R.string.elym_cover), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Action(
            stringResource(if (edited.cover == null) R.string.elym_pick_image else R.string.elym_replace_image),
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        PositionEditor(stringResource(R.string.elym_cover_position), edited, false) {
            edited = edited.copy(coverPosition = it)
        }
        PositionEditor(stringResource(R.string.elym_avatar_position), edited, true) {
            edited = edited.copy(avatarPosition = it)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.elym_add_favorite), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Checkbox(edited.favorite, { edited = edited.copy(favorite = it) }, modifier = Modifier.semanticsLabel(stringResource(R.string.elym_add_favorite)))
        }
        HorizontalDivider(color = Line)
        Action(
            stringResource(if (creating) R.string.elym_create else R.string.elym_save),
            { save(edited.copy(name = edited.name.trim(), tags = tags.split('、', '，', ',').map(String::trim).filter(String::isNotEmpty))) },
            primary = true, enabled = edited.name.isNotBlank(),
        )
    }
}

@Composable
private fun PositionEditor(label: String, persona: PersonaCard, avatar: Boolean, update: (ImagePosition) -> Unit) {
    val position = if (avatar) persona.avatarPosition else persona.coverPosition
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        PersonaImage(
            persona,
            (if (avatar) Modifier.size(72.dp) else Modifier.fillMaxWidth().aspectRatio(1.79f)).clip(RoundedCornerShape(16.dp)),
            avatar = avatar,
        )
        if (persona.cover != null) {
            Slider(position.x, { update(position.copy(x = it)) }, modifier = Modifier.semanticsLabel(stringResource(R.string.elym_horizontal)))
            Slider(position.y, { update(position.copy(y = it)) }, modifier = Modifier.semanticsLabel(stringResource(R.string.elym_vertical)))
        }
    }
}

@Composable
internal fun SessionsPage(model: ElymBotViewModel, persona: PersonaCard, back: () -> Unit, open: (String, String) -> Unit) {
    val service = model.service
    val sessions by remember(persona.id) { service.sessions(persona.id) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember(persona.id) { mutableStateOf(false) }
    var selection by remember(persona.id) { mutableStateOf(emptySet<String>()) }
    var options by remember { mutableStateOf<ConversationSummary?>(null) }
    var rename by remember { mutableStateOf<ConversationSummary?>(null) }
    var deletion by remember { mutableStateOf<Set<String>?>(null) }
    val create = { model.perform { open(service.newSession(persona.id), "") } }
    BackHandler(editing) { editing = false; selection = emptySet() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 5.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), {
                if (editing) { editing = false; selection = emptySet() } else back()
            })
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.elym_sessions), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(persona.name, color = Muted, fontSize = 12.sp)
            }
            if (sessions.isNotEmpty()) {
                TextButton(onClick = {
                    editing = !editing
                    selection = emptySet()
                }) { Text(stringResource(if (editing) R.string.elym_cancel else R.string.elym_select)) }
                if (!editing) RoundAction(R.drawable.elym_plus, stringResource(R.string.elym_new_session), create, primary = true)
            }
        }
        if (sessions.isEmpty()) {
            EmptyMessage(stringResource(R.string.elym_no_sessions), R.drawable.elym_chat) {
                Action(stringResource(R.string.elym_new_session), create, primary = true)
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 14.dp)) {
                Group {
                    sessions.forEachIndexed { index, session ->
                        if (index > 0) HorizontalDivider(color = Line)
                        val chosen = session.id in selection
                        val toggle = { selection = if (chosen) selection - session.id else selection + session.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (editing) Checkbox(chosen, { toggle() }, enabled = !session.running, modifier = Modifier.semanticsLabel(session.title))
                            ListRow(
                                session.title, R.drawable.elym_chat,
                                { if (editing) { if (!session.running) toggle() } else open(session.id, session.title) },
                                Modifier.weight(1f),
                                subtitle = if (session.running) stringResource(R.string.elym_generating) else session.preview ?: stringResource(R.string.elym_image_message),
                                badge = if (session.unread) stringResource(R.string.elym_unread) else null,
                                trailing = false,
                            )
                            if (!editing) RoundAction(R.drawable.elym_more, stringResource(R.string.elym_session_menu, session.title), { options = session })
                        }
                    }
                }
            }
            if (editing) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pluralStringResource(R.plurals.elym_selected, selection.size, selection.size), color = Muted, fontSize = 13.sp)
                Action(stringResource(R.string.elym_delete_selected), { deletion = selection }, danger = true, enabled = selection.isNotEmpty())
            }
        }
    }

    options?.let { item ->
        Sheet(item.title, { options = null }) {
            Group {
                ListRow(stringResource(R.string.elym_rename), R.drawable.elym_sliders, { options = null; rename = item })
                HorizontalDivider(color = Line)
                ListRow(stringResource(R.string.elym_delete_session), R.drawable.elym_close, { options = null; deletion = setOf(item.id) })
            }
        }
    }
    rename?.let { item ->
        RenameSession(item.title, { rename = null }) { edited ->
            model.perform { service.renameSession(item.id, edited); rename = null }
        }
    }
    deletion?.let { ids ->
        val title = sessions.firstOrNull { it.id in ids }?.title.orEmpty()
        ConfirmSessionDelete(
            sessions.any { it.id in ids && it.running },
            if (ids.size == 1) stringResource(R.string.elym_confirm_session, title)
            else pluralStringResource(R.plurals.elym_confirm_sessions, ids.size, ids.size),
            { deletion = null },
        ) {
            model.perform {
                service.deleteSessions(persona.id, ids)
                deletion = null
                selection = emptySet()
                editing = false
            }
        }
    }
}

@Composable
internal fun RenameSession(title: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var edited by remember(title) { mutableStateOf(title) }
    Sheet(stringResource(R.string.elym_rename_session), dismiss) {
        Field(stringResource(R.string.elym_session_title), edited, { edited = it.take(60) })
        Action(stringResource(R.string.elym_save), { save(edited.trim()) }, primary = true, enabled = edited.isNotBlank())
    }
}

@Composable
internal fun ConfirmSessionDelete(running: Boolean, message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    Sheet(stringResource(if (running) R.string.elym_session_busy else R.string.elym_delete_session), dismiss) {
        Text(if (running) stringResource(R.string.elym_stop_before_delete) else message, color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!running) Action(stringResource(R.string.elym_delete_session), confirm, danger = true)
            Action(stringResource(R.string.elym_cancel), dismiss)
        }
    }
}
