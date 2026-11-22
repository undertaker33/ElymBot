package com.elymbot.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
    current: Boolean,
    onSessions: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
) {
    val service = model.service
    val conversation by remember(id) { service.conversation(id) }.collectAsStateWithLifecycle(Conversation())
    val sessionList by remember(persona.id, main) {
        if (main) kotlinx.coroutines.flow.flowOf(emptyList()) else service.sessions(persona.id)
    }.collectAsStateWithLifecycle(emptyList())
    val displayTitle = sessionList.firstOrNull { it.id == id }?.title ?: title
    val commandNames by service.commandNames.collectAsStateWithLifecycle(emptyMap())
    var menu by remember { mutableStateOf(false) }
    var chooseSessions by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var showCheckpoints by remember { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showMissingMainModel by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = uri.toString()
    }
    val list = rememberLazyListState()
    val last = conversation.messages.lastOrNull()
    var follow by remember { mutableStateOf(true) }
    var pendingSendScroll by remember(id) { mutableStateOf<SentDraft?>(null) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to !list.canScrollForward }.collect { (scrolling, atEnd) ->
            if (scrolling) follow = atEnd
        }
    }
    val showToolProgress = conversation.generating && conversation.toolProgressActive
    val showPendingReply = conversation.generating && (showToolProgress || last?.assistant != true)
    val showEmpty = conversation.messages.isEmpty() && !conversation.generating
    val itemCount = conversation.messages.size + listOf(showEmpty, showPendingReply, conversation.issue != null).count { it }
    val revealedSend = pendingSendScroll?.takeIf { it.hasNewEcho(conversation.messages) }
    fun submit(text: String, attachment: Uri?) {
        if (!current || submitting) return
        val sentDraft = SentDraft(text, attachment != null).watchNewEcho(conversation.messages, pendingSendScroll)
        submitting = true
        model.perform {
            try {
                service.send(id, text, attachment)
                pendingSendScroll = sentDraft
                if (text == draft.text.trim() && attachment?.toString() == image) {
                    draft = TextFieldValue()
                    image = null
                }
            } catch (failure: UiServiceException) {
                if (failure.issue != UiIssue.MainModelMissing) throw failure
                showMissingMainModel = true
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 10.dp, top = 3.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
            Box(
                Modifier.size(48.dp),
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
                DropdownMenu(current && menu, { menu = false }, containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp)) {
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
                    if (main) ChatMenuItem(R.string.elym_delete_persona, R.drawable.elym_close, danger = true) { menu = false; onDelete() }
                    else if (displayTitle.isNotBlank()) ChatMenuItem(R.string.elym_delete_session, R.drawable.elym_close, danger = true) { menu = false; delete = true }
                }
            }
        }
        HorizontalDivider(color = Line)
        FloatingComposerLayout(Modifier.weight(1f).fillMaxWidth(), timeline = { bottomPadding ->
            LaunchedEffect(current, last?.id, last?.text, itemCount, revealedSend, bottomPadding) {
                if (!current) return@LaunchedEffect
                if (revealedSend != null && pendingSendScroll == revealedSend) {
                    pendingSendScroll = null
                    follow = true
                    list.requestScrollToItem(itemCount)
                } else if (follow && itemCount > 0) {
                    list.requestScrollToItem(itemCount)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(), state = list,
                contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 19.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                if (showEmpty) item {
                    EmptyMessage(stringResource(R.string.elym_no_messages), R.drawable.elym_chat)
                }
                items(conversation.messages, key = { it.id }) { message ->
                    if (message.localReceipt) LocalChatReceipt(message.text) else MessageBubble(message, persona.name)
                }
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
                            if (conversation.canRetry) Action(stringResource(R.string.elym_retry), { model.perform { service.retry(id) } })
                        }
                    }
                }
                item { Spacer(Modifier.height(1.dp)) }
            }
        }) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = FloatingComposerHorizontalMargin, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = PaperTop,
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                shadowElevation = 5.dp,
            ) {
                Box(Modifier.padding(FloatingComposerInnerPadding)) {
                    val running = conversation.generating
                    val canSend = current && !running && !submitting && (draft.text.isNotBlank() || image != null)
                    val sendDraft = { if (canSend) submit(draft.text.trim(), image?.let(Uri::parse)) }
                    ChatComposer(
                        draft = draft,
                        onDraftChange = { draft = it },
                        commandNames = commandNames,
                        main = main,
                        enabled = current,
                        image = image,
                        onRemoveImage = { image = null },
                        onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onSend = sendDraft,
                    ) {
                        RoundAction(
                            if (running) R.drawable.elym_stop else R.drawable.elym_send,
                            stringResource(if (running) R.string.elym_stop else R.string.elym_send),
                            {
                                if (current && running) model.perform { service.stop(id) }
                                else sendDraft()
                            },
                            primary = true, enabled = current && (running || canSend), roundedSquare = true,
                        )
                    }
                }
            }
        }
    }

    if (current && showMissingMainModel) AlertDialog(
        onDismissRequest = { showMissingMainModel = false },
        text = { Text(stringResource(R.string.elym_main_missing)) },
        confirmButton = {
            TextButton(onClick = { showMissingMainModel = false }) { Text(stringResource(R.string.elym_close)) }
        },
    )

    if (current && chooseSessions) Sheet(stringResource(R.string.elym_choose_persona), { chooseSessions = false }) {
        Group {
            personas.filter { it.id != persona.id }.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(item.name, R.drawable.elym_person, { chooseSessions = false; onSessions(item.id) })
            }
        }
    }
    if (current && rename) RenameSession(displayTitle, { rename = false }) { edited ->
        model.perform { service.renameSession(id, edited); onRename(edited); rename = false }
    }
    if (current && showCheckpoints) {
        CheckpointsSheet(model, id, conversation.generating, { showCheckpoints = false })
    }
    if (current && delete) ConfirmSessionDelete(
        conversation.generating, stringResource(R.string.elym_confirm_session, displayTitle), { delete = false },
    ) { model.perform { service.deleteSessions(persona.id, setOf(id)); delete = false; back() } }
}

private data class SentDraft(
    val text: String,
    val hasAttachment: Boolean,
    val knownEchoIds: Set<String> = emptySet(),
    val expectedEchoes: Int = 1,
) {
    fun watchNewEcho(messages: List<ChatMessage>, pending: SentDraft?): SentDraft {
        val known = messages.asSequence().filter(::matches).map(ChatMessage::id).toSet()
        val unseen = if (pending != null && text == pending.text && hasAttachment == pending.hasAttachment) {
            (pending.expectedEchoes - known.count { it !in pending.knownEchoIds }).coerceAtLeast(0)
        } else {
            0
        }
        return copy(knownEchoIds = known, expectedEchoes = unseen + 1)
    }

    fun hasNewEcho(messages: List<ChatMessage>): Boolean =
        messages.count { matches(it) && it.id !in knownEchoIds } >= expectedEchoes

    private fun matches(message: ChatMessage): Boolean =
        !message.assistant && message.text == text && (message.image != null) == hasAttachment
}

@Composable
internal fun ChatMenuItem(label: Int, icon: Int, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (danger) Danger else Ink
    DropdownMenuItem(
        text = { Text(stringResource(label), color = tint, fontSize = 14.sp) },
        leadingIcon = { Glyph(icon, color = tint) },
        onClick = onClick,
        enabled = enabled,
    )
}
