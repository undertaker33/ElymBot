package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R

@Composable
internal fun SessionsPage(
    model: ElymBotViewModel,
    persona: PersonaCard,
    back: () -> Unit,
    current: Boolean,
    managePersona: Boolean,
    onEditPersona: () -> Unit,
    onDeletePersona: () -> Unit,
    open: (String, String) -> Unit,
) {
    val service = model.service
    val sessions by remember(persona.id) { service.sessions(persona.id) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember(persona.id) { mutableStateOf(false) }
    var personaMenu by remember(persona.id) { mutableStateOf(false) }
    var selection by remember(persona.id) { mutableStateOf(emptySet<String>()) }
    var options by remember { mutableStateOf<ConversationSummary?>(null) }
    var rename by remember { mutableStateOf<ConversationSummary?>(null) }
    var deletion by remember { mutableStateOf<Set<String>?>(null) }
    val create = { model.perform { open(service.newSession(persona.id), "") } }
    val bodyMotion = LocalPageBodyMotion.current
    BackHandler(current && editing) { editing = false; selection = emptySet() }

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
            if (managePersona && !editing) {
                Box {
                    RoundAction(R.drawable.elym_more, stringResource(R.string.elym_persona_menu), { personaMenu = true })
                    DropdownMenu(current && personaMenu, { personaMenu = false }, containerColor = SurfaceColor) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.elym_edit_persona)) },
                            leadingIcon = { Glyph(R.drawable.elym_sliders) },
                            onClick = { personaMenu = false; onEditPersona() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.elym_delete_persona), color = Danger) },
                            leadingIcon = { Glyph(R.drawable.elym_close, color = Danger) },
                            onClick = { personaMenu = false; onDeletePersona() },
                        )
                    }
                }
            }
        }
        if (sessions.isEmpty()) {
            Box(bodyMotion) {
                EmptyMessage(stringResource(R.string.elym_no_sessions), R.drawable.elym_chat) {
                    Action(stringResource(R.string.elym_new_session), create, primary = true)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().then(bodyMotion),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
            ) {
                itemsIndexed(sessions, key = { _, session -> session.id }) { index, session ->
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 18.dp else 0.dp,
                        topEnd = if (index == 0) 18.dp else 0.dp,
                        bottomStart = if (index == sessions.lastIndex) 18.dp else 0.dp,
                        bottomEnd = if (index == sessions.lastIndex) 18.dp else 0.dp,
                    )
                    val singleRow = sessions.size == 1
                    val line = Line
                    val edge = if (singleRow) Modifier.border(1.dp, line, shape) else Modifier.drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(line, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                        drawLine(line, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                        if (index == 0) drawLine(line, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                        drawLine(line, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                    }
                    Column(Modifier.fillMaxWidth().clip(shape).background(SurfaceColor).then(edge)) {
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
            if (editing) Row(Modifier.fillMaxWidth().then(bodyMotion).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pluralStringResource(R.plurals.elym_selected, selection.size, selection.size), color = Muted, fontSize = 13.sp)
                Action(stringResource(R.string.elym_delete_selected), { deletion = selection }, danger = true, enabled = selection.isNotEmpty())
            }
        }
    }

    options?.takeIf { current }?.let { item ->
        Sheet(item.title, { options = null }) {
            Group {
                ListRow(stringResource(R.string.elym_rename), R.drawable.elym_sliders, { options = null; rename = item })
                HorizontalDivider(color = Line)
                ListRow(stringResource(R.string.elym_delete_session), R.drawable.elym_close, { options = null; deletion = setOf(item.id) })
            }
        }
    }
    rename?.takeIf { current }?.let { item ->
        RenameSession(item.title, { rename = null }) { edited ->
            model.perform { service.renameSession(item.id, edited); rename = null }
        }
    }
    deletion?.takeIf { current }?.let { ids ->
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
