package com.elymbot.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R

@Composable
internal fun ChatPage(
    model: ElymBotViewModel,
    persona: PersonaCard,
    id: String,
    title: String,
    main: Boolean,
    personas: List<PersonaCard>,
    back: () -> Unit,
    onSessions: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onModelSettings: () -> Unit,
    onRename: (String) -> Unit,
) {
    val service = model.service
    val conversation by remember(id) { service.conversation(id) }.collectAsStateWithLifecycle(Conversation())
    val sessionList by remember(persona.id, main) {
        if (main) kotlinx.coroutines.flow.flowOf(emptyList()) else service.sessions(persona.id)
    }.collectAsStateWithLifecycle(emptyList())
    val displayTitle = sessionList.firstOrNull { it.id == id }?.title ?: title
    var menu by remember { mutableStateOf(false) }
    var choosePersona by remember { mutableStateOf(false) }
    var chooseSessions by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var showCheckpoints by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showMainModelConfig by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { image = it?.toString() }
    val list = rememberLazyListState()
    val last = conversation.messages.lastOrNull()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to !list.canScrollForward }.collect { (scrolling, atEnd) ->
            if (scrolling) follow = atEnd
        }
    }
    val showToolProgress = conversation.generating && conversation.toolProgressActive
    val showPendingReply = conversation.generating && (showToolProgress || last?.assistant != true)
    val itemCount = conversation.messages.size + (if (showPendingReply) 1 else 0) + (if (conversation.issue != null) 1 else 0)
    LaunchedEffect(last?.id, last?.text, itemCount) {
        if (follow && itemCount > 0) list.requestScrollToItem(itemCount - 1)
    }
    fun submit(text: String, attachment: Uri? = null, fromComposer: Boolean = false) {
        if (submitting) return
        submitting = true
        model.perform {
            try {
                service.send(id, text, attachment)
                if (fromComposer && text == draft.trim() && attachment?.toString() == image) {
                    draft = ""
                    image = null
                }
            } catch (failure: UiServiceException) {
                if (failure.issue != UiIssue.MainModelMissing) throw failure
                showMainModelConfig = true
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 10.dp, top = 3.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
            Box(
                Modifier.size(48.dp).then(
                    if (main) Modifier.clickable(enabled = !conversation.generating, onClickLabel = stringResource(R.string.elym_switch_persona)) { choosePersona = true }
                    else Modifier
                ),
                contentAlignment = Alignment.Center,
            ) {
                PersonaImage(persona, Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)), avatar = true)
            }
            Text(
                if (main) persona.name else displayTitle.ifEmpty { stringResource(R.string.elym_new_chat_title) },
                Modifier.weight(1f).padding(start = 4.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box {
                RoundAction(R.drawable.elym_more, stringResource(R.string.elym_chat_menu), { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp)) {
                    if (main) {
                        ChatMenuItem(R.string.elym_sessions, R.drawable.elym_chat) { menu = false; onSessions(persona.id) }
                        if (conversation.generating) ChatMenuItem(R.string.elym_other_sessions, R.drawable.elym_person) { menu = false; chooseSessions = true }
                        ChatMenuItem(R.string.elym_edit_persona, R.drawable.elym_sliders) { menu = false; onEdit() }
                    } else if (displayTitle.isNotBlank()) {
                        ChatMenuItem(R.string.elym_rename, R.drawable.elym_sliders) { menu = false; rename = true }
                    }
                    ChatMenuItem(R.string.elym_checkpoints, R.drawable.elym_clock, enabled = !conversation.generating && !submitting) {
                        menu = false
                        showCheckpoints = true
                    }
                    ChatMenuItem(R.string.elym_model_config, R.drawable.elym_sliders) { menu = false; onModelSettings() }
                    if (main) ChatMenuItem(R.string.elym_delete_persona, R.drawable.elym_close, danger = true) { menu = false; onDelete() }
                    else if (displayTitle.isNotBlank()) ChatMenuItem(R.string.elym_delete_session, R.drawable.elym_close, danger = true) { menu = false; delete = true }
                }
            }
        }
        HorizontalDivider(color = Line)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 19.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            if (conversation.messages.isEmpty() && !conversation.generating) item {
                EmptyMessage(stringResource(R.string.elym_no_messages), R.drawable.elym_chat)
            }
            items(conversation.messages, key = { it.id }) { message -> MessageBubble(message, persona.name) }
            if (showPendingReply) item {
                val pendingReply = if (showToolProgress) {
                    ChatMessage("waiting", text = stringResource(R.string.elym_tool_progress), assistant = true)
                } else {
                    ChatMessage("waiting", assistant = true, status = MessageStatus.Waiting)
                }
                MessageBubble(pendingReply, persona.name)
            }
            conversation.issue?.let { issue ->
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(10.dp)).padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(stringResource(R.string.elym_reply_failed), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(issueLabel(issue)), color = Accent, fontSize = 13.sp)
                        if (issue == UiIssue.MainModelMissing || issue == UiIssue.VisionModelMissing ||
                            issue == UiIssue.VisionCapabilityUnknown
                        ) {
                            Action(stringResource(R.string.elym_model_config), onModelSettings)
                        }
                        if (conversation.canRetry) Action(stringResource(R.string.elym_retry), { model.perform { service.retry(id) } })
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Column(Modifier.background(PaperTop).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)) {
            image?.let { uri ->
                Row(Modifier.padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Photo(Uri.parse(uri), Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                    Text(stringResource(R.string.elym_attachment), Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                    RoundAction(R.drawable.elym_close, stringResource(R.string.elym_remove_image), { image = null })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RoundAction(R.drawable.elym_photo, stringResource(R.string.elym_add_image), { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, outlined = true)
                val running = conversation.generating
                val sendDraft = { if (!running && (draft.isNotBlank() || image != null)) submit(draft.trim(), image?.let(Uri::parse), fromComposer = true) }
                Input(draft, { draft = it.take(1800) }, stringResource(R.string.elym_type_message), Modifier.weight(1f), multiline = true, compact = true, send = sendDraft)
                RoundAction(
                    if (running) R.drawable.elym_stop else R.drawable.elym_send,
                    stringResource(if (running) R.string.elym_stop else R.string.elym_send),
                    {
                        if (running) model.perform { service.stop(id) }
                        else sendDraft()
                    },
                    primary = true, enabled = running || (!submitting && (draft.isNotBlank() || image != null)), roundedSquare = true,
                )
            }
        }
    }

    if (showMainModelConfig) AlertDialog(
        onDismissRequest = { showMainModelConfig = false },
        text = { Text(stringResource(R.string.elym_main_missing)) },
        confirmButton = {
            TextButton(onClick = {
                showMainModelConfig = false
                onModelSettings()
            }) { Text(stringResource(R.string.elym_model_config)) }
        },
        dismissButton = {
            TextButton(onClick = { showMainModelConfig = false }) { Text(stringResource(R.string.elym_close)) }
        },
    )

    if (choosePersona) Sheet(stringResource(R.string.elym_switch_persona), { choosePersona = false }) {
        Group {
            personas.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(
                    item.name, R.drawable.elym_person,
                    { model.perform { service.activatePersona(item.id); choosePersona = false } },
                    subtitle = item.description, badge = if (item.id == persona.id) stringResource(R.string.elym_active) else null,
                    selected = item.id == persona.id,
                )
            }
        }
    }
    if (chooseSessions) Sheet(stringResource(R.string.elym_choose_persona), { chooseSessions = false }) {
        Group {
            personas.filter { it.id != persona.id }.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(item.name, R.drawable.elym_person, { chooseSessions = false; onSessions(item.id) })
            }
        }
    }
    if (rename) RenameSession(displayTitle, { rename = false }) { edited ->
        model.perform { service.renameSession(id, edited); onRename(edited); rename = false }
    }
    if (showCheckpoints) {
        CheckpointsSheet(model, id, conversation.generating, { showCheckpoints = false })
    }
    if (delete) ConfirmSessionDelete(
        conversation.generating, stringResource(R.string.elym_confirm_session, displayTitle), { delete = false },
    ) { model.perform { service.deleteSessions(persona.id, setOf(id)); delete = false; back() } }
}

@Composable
private fun CheckpointsSheet(model: ElymBotViewModel, sessionId: String, running: Boolean, dismiss: () -> Unit) {
    val service = model.service
    val checkpoints by remember(sessionId) { service.checkpoints(sessionId) }
        .collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf(false) }
    val enabled = !running && !pending && checkpoints.isNotEmpty()
    fun runCheckpointAction(action: suspend () -> Unit) {
        if (!enabled || pending) return
        pending = true
        model.perform {
            try {
                action()
            } finally {
                pending = false
            }
        }
    }

    Sheet(stringResource(R.string.elym_checkpoints), dismiss) {
        Action(
            stringResource(R.string.elym_save_checkpoint),
            { runCheckpointAction { service.saveCheckpoint(sessionId) } },
            primary = true,
            enabled = enabled,
        )
        if (checkpoints.isNotEmpty()) Group {
            checkpoints.sortedByDescending(ChatCheckpoint::displayNumber).forEachIndexed { index, point ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(
                    stringResource(R.string.elym_checkpoint_title, point.displayNumber),
                    R.drawable.elym_clock,
                    {
                        runCheckpointAction {
                            service.restoreCheckpoint(sessionId, point.displayNumber)
                            dismiss()
                        }
                    },
                    subtitle = pluralStringResource(
                        R.plurals.elym_checkpoint_retained_messages, point.retainedMessageCount, point.retainedMessageCount,
                    ),
                    badge = if (point.current) stringResource(R.string.elym_checkpoint_current) else null,
                    selected = point.current,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ChatMenuItem(label: Int, icon: Int, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (danger) Danger else Ink
    DropdownMenuItem(
        text = { Text(stringResource(label), color = tint, fontSize = 14.sp) },
        leadingIcon = { Glyph(icon, color = tint) },
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun MessageBubble(message: ChatMessage, persona: String) {
    val shape = if (message.assistant) RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 6.dp, 16.dp, 16.dp)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.assistant) Alignment.Start else Alignment.End,
    ) {
        if (message.assistant) Text(message.author ?: persona, Modifier.padding(start = 4.dp, bottom = 4.dp), color = Muted, fontSize = 11.sp)
        Column(
            Modifier.widthIn(max = 345.dp).background(if (message.assistant) SurfaceColor else Soft, shape)
                .then(if (message.assistant) Modifier.border(1.dp, Line, shape) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.image?.let { Photo(it, Modifier.widthIn(max = 210.dp).heightIn(max = 175.dp).clip(RoundedCornerShape(8.dp))) }
            if (message.text.isNotEmpty()) Text(message.text, color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
            else if (message.status == MessageStatus.Waiting) {
                Row(Modifier.semanticsLabel(stringResource(R.string.elym_waiting)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.size(5.dp).background(Accent, androidx.compose.foundation.shape.CircleShape)) }
                }
            }
        }
        val status = when (message.status) {
            MessageStatus.Stopped -> R.string.elym_stopped
            MessageStatus.Interrupted -> R.string.elym_interrupted
            else -> null
        }
        if (status != null) Text(stringResource(status), Modifier.padding(horizontal = 4.dp, vertical = 5.dp), color = Muted, fontSize = 11.sp)
    }
}
