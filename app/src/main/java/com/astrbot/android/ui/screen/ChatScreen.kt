package com.astrbot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.viewmodel.ChatViewModel

private data class SelectorOption(
    val id: String,
    val label: String,
    val sublabel: String = "",
)

@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel()) {
    val bots by chatViewModel.bots.collectAsState()
    val providers by chatViewModel.providers.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val currentSession = chatViewModel.currentSession()
    val currentBot = chatViewModel.selectedBot()
    val messages = chatViewModel.sessionMessages(uiState.selectedSessionId)
    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }

    val sessionOptions = sessions.map {
        SelectorOption(
            id = it.id,
            label = it.title,
            sublabel = "${it.messages.size} 条消息",
        )
    }
    val botOptions = bots.map {
        SelectorOption(
            id = it.id,
            label = it.displayName,
            sublabel = it.accountHint.ifBlank { it.platformName },
        )
    }
    val providerOptions = chatProviders.map {
        SelectorOption(
            id = it.id,
            label = it.name,
            sublabel = it.model,
        )
    }

    var input by remember(uiState.selectedSessionId) { mutableStateOf("") }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F5F2))
            .imePadding(),
    ) {
        val wideLayout = maxWidth >= 960.dp

        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConversationRail(
                    sessions = sessions,
                    selectedSessionId = uiState.selectedSessionId,
                    onCreateSession = { chatViewModel.createSession() },
                    onDeleteSelected = { chatViewModel.deleteSelectedSession() },
                    onSelectSession = { chatViewModel.selectSession(it) },
                    modifier = Modifier
                        .width(272.dp)
                        .fillMaxHeight(),
                )
                ChatPanel(
                    sessionOptions = sessionOptions,
                    selectedSessionId = uiState.selectedSessionId,
                    onSelectSession = { chatViewModel.selectSession(it) },
                    onCreateSession = { chatViewModel.createSession() },
                    onDeleteSelected = { chatViewModel.deleteSelectedSession() },
                    botOptions = botOptions,
                    selectedBotId = uiState.selectedBotId,
                    onSelectBot = { chatViewModel.selectBot(it) },
                    providerOptions = providerOptions,
                    selectedProviderId = uiState.selectedProviderId,
                    onSelectProvider = { chatViewModel.selectProvider(it) },
                    assistantLabel = currentBot?.displayName ?: "AstrBot",
                    messages = messages.map { it.role to it.content },
                    streamingEnabled = uiState.streamingEnabled,
                    onToggleStreaming = { chatViewModel.toggleStreaming() },
                    input = input,
                    onInputChange = { input = it },
                    isSending = uiState.isSending,
                    error = uiState.error,
                    sessionTitle = currentSession?.title ?: "新对话",
                    onSend = {
                        chatViewModel.sendMessage(input)
                        input = ""
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ChatPanel(
                sessionOptions = sessionOptions,
                selectedSessionId = uiState.selectedSessionId,
                onSelectSession = { chatViewModel.selectSession(it) },
                onCreateSession = { chatViewModel.createSession() },
                onDeleteSelected = { chatViewModel.deleteSelectedSession() },
                botOptions = botOptions,
                selectedBotId = uiState.selectedBotId,
                onSelectBot = { chatViewModel.selectBot(it) },
                providerOptions = providerOptions,
                selectedProviderId = uiState.selectedProviderId,
                onSelectProvider = { chatViewModel.selectProvider(it) },
                assistantLabel = currentBot?.displayName ?: "AstrBot",
                messages = messages.map { it.role to it.content },
                streamingEnabled = uiState.streamingEnabled,
                onToggleStreaming = { chatViewModel.toggleStreaming() },
                input = input,
                onInputChange = { input = it },
                isSending = uiState.isSending,
                error = uiState.error,
                sessionTitle = currentSession?.title ?: "新对话",
                onSend = {
                    chatViewModel.sendMessage(input)
                    input = ""
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun ConversationRail(
    sessions: List<ConversationSession>,
    selectedSessionId: String,
    onCreateSession: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECE7DC)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("会话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateSession) {
                    Text("新建")
                }
                OutlinedButton(
                    onClick = onDeleteSelected,
                    enabled = sessions.isNotEmpty(),
                ) {
                    Text("删除")
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    val active = session.id == selectedSessionId
                    Card(
                        onClick = { onSelectSession(session.id) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) Color(0xFF0F172A) else Color(0xFFF9F7F1),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = session.title,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${session.messages.size} 条消息",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) {
                                    Color.White.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(
    sessionOptions: List<SelectorOption>,
    selectedSessionId: String,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSelected: () -> Unit,
    botOptions: List<SelectorOption>,
    selectedBotId: String,
    onSelectBot: (String) -> Unit,
    providerOptions: List<SelectorOption>,
    selectedProviderId: String,
    onSelectProvider: (String) -> Unit,
    assistantLabel: String,
    messages: List<Pair<String, String>>,
    streamingEnabled: Boolean,
    onToggleStreaming: () -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    error: String,
    sessionTitle: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedBot = botOptions.firstOrNull { it.id == selectedBotId } ?: botOptions.firstOrNull()
    val selectedProvider = providerOptions.firstOrNull { it.id == selectedProviderId } ?: providerOptions.firstOrNull()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatControlMenu(
            sessionOptions = sessionOptions,
            sessionTitle = sessionTitle,
            selectedSessionId = selectedSessionId,
            onSelectSession = onSelectSession,
            onCreateSession = onCreateSession,
            onDeleteSelected = onDeleteSelected,
            botOptions = botOptions,
            selectedBotLabel = selectedBot?.label ?: "未选择机器人",
            selectedBotId = selectedBotId,
            onSelectBot = onSelectBot,
            providerOptions = providerOptions,
            selectedProviderLabel = selectedProvider?.label ?: "未选择模型",
            selectedProviderId = selectedProviderId,
            onSelectProvider = onSelectProvider,
        )

        MessageStream(
            sessionTitle = sessionTitle,
            messages = messages,
            assistantLabel = assistantLabel,
            modifier = Modifier.weight(1f),
        )

        ComposerBar(
            input = input,
            onInputChange = onInputChange,
            isSending = isSending,
            hasProviders = providerOptions.isNotEmpty(),
            error = error,
            streamingEnabled = streamingEnabled,
            onToggleStreaming = onToggleStreaming,
            onSend = onSend,
        )
    }
}

@Composable
private fun ChatControlMenu(
    sessionOptions: List<SelectorOption>,
    sessionTitle: String,
    selectedSessionId: String,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSelected: () -> Unit,
    botOptions: List<SelectorOption>,
    selectedBotLabel: String,
    selectedBotId: String,
    onSelectBot: (String) -> Unit,
    providerOptions: List<SelectorOption>,
    selectedProviderLabel: String,
    selectedProviderId: String,
    onSelectProvider: (String) -> Unit,
) {
    var expanded by remember(sessionOptions, selectedSessionId, selectedBotId, selectedProviderId) {
        mutableStateOf(false)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = sessionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$selectedBotLabel · $selectedProviderLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "打开聊天控制")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Text(
                    text = "会话",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                sessionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (option.sublabel.isNotBlank()) {
                                    Text(
                                        text = option.sublabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectSession(option.id)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("新建会话") },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = {
                        onCreateSession()
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除当前会话") },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    onClick = {
                        onDeleteSelected()
                        expanded = false
                    },
                )

                HorizontalDivider()
                Text(
                    text = "机器人",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (botOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无机器人") },
                        onClick = { expanded = false },
                    )
                } else {
                    botOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (option.sublabel.isNotBlank()) {
                                        Text(
                                            text = option.sublabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectBot(option.id)
                                expanded = false
                            },
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    text = "模型",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (providerOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无模型") },
                        onClick = { expanded = false },
                    )
                } else {
                    providerOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (option.sublabel.isNotBlank()) {
                                        Text(
                                            text = option.sublabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectProvider(option.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStream(
    sessionTitle: String,
    messages: List<Pair<String, String>>,
    assistantLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7)),
    ) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("开始新的对话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    sessionTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(messages) { (role, content) ->
                    MessageBubble(
                        role = role,
                        content = content,
                        assistantLabel = assistantLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    role: String,
    content: String,
    assistantLabel: String,
) {
    val isUser = role == "user"
    val bubbleColor = if (isUser) Color(0xFF0F172A) else Color(0xFFEAE5DA)
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isUser) {
                AvatarDot(
                    label = assistantLabel.take(1).uppercase(),
                    containerColor = Color(0xFFD8E7D0),
                    textColor = Color(0xFF26421A),
                )
            }
            Text(
                text = if (isUser) "你" else assistantLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            if (isUser) {
                AvatarDot(
                    label = "我",
                    containerColor = Color(0xFF0F172A),
                    textColor = Color.White,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isUser) 24.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 24.dp,
            ),
            color = bubbleColor,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun AvatarDot(
    label: String,
    containerColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComposerBar(
    input: String,
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    hasProviders: Boolean,
    error: String,
    streamingEnabled: Boolean,
    onToggleStreaming: () -> Unit,
    onSend: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("消息") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(22.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (error.isNotBlank()) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = if (hasProviders) "就绪" else "请先配置模型",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    TextButton(
                        onClick = onToggleStreaming,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (streamingEnabled) "流式输出：开" else "流式输出：关")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    Button(
                        onClick = onSend,
                        enabled = input.isNotBlank() && !isSending && hasProviders,
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }
}
