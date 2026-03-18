package com.astrbot.android.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.model.ConversationAttachment
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.ChatTopBar
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
) {
    val context = LocalContext.current
    val bots by chatViewModel.bots.collectAsState()
    val providers by chatViewModel.providers.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()

    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBot = chatViewModel.selectedBot()
    val messages = chatViewModel.sessionMessages(uiState.selectedSessionId)
    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
    var showQqConversations by remember { mutableStateOf(true) }
    val visibleSessions = sessions.filter { session -> showQqConversations || !session.isQqConversation() }

    var input by remember(uiState.selectedSessionId) { mutableStateOf("") }
    var pendingAttachments by remember(uiState.selectedSessionId) { mutableStateOf(emptyList<ConversationAttachment>()) }
    var selectorExpanded by remember(uiState.selectedSessionId, uiState.selectedBotId, uiState.selectedProviderId) {
        mutableStateOf(false)
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val attachment = uri?.let { loadConversationAttachment(context, it) }
        if (attachment != null) {
            pendingAttachments = pendingAttachments + attachment
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.82f),
                drawerContainerColor = MonochromeUi.drawerSurface,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MonochromeUi.drawerSurface),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MonochromeUi.cardBackground,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(R.string.chat_show_qq_title), color = MonochromeUi.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.chat_show_qq_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MonochromeUi.textSecondary,
                                    )
                                }
                                Switch(
                                    checked = showQqConversations,
                                    onCheckedChange = { showQqConversations = it },
                                    colors = monochromeSwitchColors(),
                                )
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = {
                                chatViewModel.createSession()
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = MonochromeUi.cardAltBackground,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.chat_new_conversation), color = MonochromeUi.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(visibleSessions, key = { it.id }) { session ->
                        Surface(
                            onClick = {
                                chatViewModel.selectSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (session.id == uiState.selectedSessionId) MonochromeUi.cardAltBackground else Color.Transparent,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    session.title,
                                    color = MonochromeUi.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildList {
                                        add(stringResource(R.string.chat_message_count, session.messages.size))
                                        if (session.isQqConversation()) add(stringResource(R.string.chat_message_source_qq))
                                    }.joinToString(" | "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MonochromeUi.pageBackground),
            containerColor = MonochromeUi.pageBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    ChatTopBar(
                        onOpenHistory = { scope.launch { drawerState.open() } },
                        onOpenBotSelector = { selectorExpanded = true },
                        botSelectorDropdown = {
                            DropdownMenu(
                                expanded = selectorExpanded,
                                onDismissRequest = { selectorExpanded = false },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Text(
                                    stringResource(R.string.chat_selector_bots),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                bots.forEach { bot ->
                                    DropdownMenuItem(
                                        text = { Text(bot.displayName) },
                                        onClick = {
                                            chatViewModel.selectBot(bot.id)
                                            selectorExpanded = false
                                        },
                                    )
                                }
                                Text(
                                    stringResource(R.string.chat_selector_models),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                chatProviders.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider.name) },
                                        onClick = {
                                            chatViewModel.selectProvider(provider.id)
                                            selectorExpanded = false
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MonochromeUi.elevatedSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pendingAttachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pendingAttachments.forEach { attachment ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MonochromeUi.cardAltBackground,
                                    ) {
                                        Text(
                                            text = attachment.fileName.ifBlank { stringResource(R.string.chat_image_attachment) },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            color = MonochromeUi.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                onClick = { pickImageLauncher.launch("image/*") },
                                shape = CircleShape,
                                color = MonochromeUi.iconButtonSurface,
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = stringResource(R.string.chat_add_image),
                                        tint = MonochromeUi.textPrimary,
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        chatProviders.firstOrNull { it.id == uiState.selectedProviderId }?.name
                                            ?: currentBot?.displayName
                                            ?: stringResource(R.string.chat_choose_model),
                                    )
                                },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(24.dp),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            if (uiState.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = MonochromeUi.textPrimary,
                                )
                            } else {
                                Surface(
                                    onClick = {
                                        chatViewModel.sendMessage(input, pendingAttachments)
                                        input = ""
                                        pendingAttachments = emptyList()
                                    },
                                    enabled = (input.isNotBlank() || pendingAttachments.isNotEmpty()) && chatProviders.isNotEmpty(),
                                    shape = CircleShape,
                                    color = MonochromeUi.strong,
                                ) {
                                    Box(
                                        modifier = Modifier.size(38.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Icon(
                                            Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = stringResource(R.string.chat_send),
                                            tint = MonochromeUi.strongText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.chat_empty_hint),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isUser = message.role == "user"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (isUser) stringResource(R.string.chat_role_you) else (currentBot?.displayName ?: stringResource(R.string.chat_role_assistant)),
                                color = MonochromeUi.textSecondary,
                            )
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (isUser) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (message.content.isNotBlank()) {
                                        Text(
                                            message.content,
                                            color = MonochromeUi.textPrimary,
                                        )
                                    }
                                    if (message.attachments.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.chat_image_count, message.attachments.size),
                                            color = MonochromeUi.textSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun com.astrbot.android.model.ConversationSession.isQqConversation(): Boolean {
    return id.startsWith("qq-")
}

private fun loadConversationAttachment(
    context: Context,
    uri: Uri,
): ConversationAttachment? {
    val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }.orEmpty()
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null

    return ConversationAttachment(
        id = UUID.randomUUID().toString(),
        mimeType = mimeType,
        fileName = fileName,
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}
