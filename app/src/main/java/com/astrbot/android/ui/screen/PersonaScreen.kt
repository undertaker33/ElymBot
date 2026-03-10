package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.viewmodel.PersonaViewModel
import com.astrbot.android.ui.viewmodel.ProviderViewModel

@Composable
fun PersonaScreen(
    personaViewModel: PersonaViewModel = viewModel(),
    providerViewModel: ProviderViewModel = viewModel(),
) {
    val personas by personaViewModel.personas.collectAsState()
    val providers by providerViewModel.providers.collectAsState()
    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }

    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var defaultProviderId by remember { mutableStateOf("") }
    var maxContextMessages by remember { mutableStateOf("12") }
    var webSearchEnabled by remember { mutableStateOf(true) }
    var ttsEnabled by remember { mutableStateOf(false) }
    var asrEnabled by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("人格", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("人格名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("默认模型", style = MaterialTheme.typography.titleSmall)
                    if (chatProviders.isEmpty()) {
                        Text("暂无可用对话模型")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chatProviders.forEach { provider ->
                                AssistChip(
                                    onClick = { defaultProviderId = provider.id },
                                    label = {
                                        Text(
                                            if (defaultProviderId == provider.id) {
                                                "${provider.name} 已选中"
                                            } else {
                                                provider.name
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = maxContextMessages,
                        onValueChange = { maxContextMessages = it.filter(Char::isDigit) },
                        label = { Text("上下文窗口") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("系统提示词") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                    ToolSwitch("网页搜索", webSearchEnabled) { webSearchEnabled = it }
                    ToolSwitch("语音合成", ttsEnabled) { ttsEnabled = it }
                    ToolSwitch("语音识别", asrEnabled) { asrEnabled = it }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                                personaViewModel.add(
                                    name = name,
                                    systemPrompt = systemPrompt,
                                    enabledTools = buildSet {
                                        if (webSearchEnabled) add("web_search")
                                        if (ttsEnabled) add("tts")
                                        if (asrEnabled) add("asr")
                                    },
                                    defaultProviderId = defaultProviderId,
                                    maxContextMessages = maxContextMessages.toIntOrNull() ?: 12,
                                )
                                name = ""
                                systemPrompt = ""
                                defaultProviderId = ""
                                maxContextMessages = "12"
                                webSearchEnabled = true
                                ttsEnabled = false
                                asrEnabled = false
                            }
                        },
                    ) {
                        Text("新增人格")
                    }
                }
            }
        }
        items(personas, key = { it.id }) { persona ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(persona.name, style = MaterialTheme.typography.titleMedium)
                            Text("上下文窗口：${persona.maxContextMessages}")
                        }
                        Switch(
                            checked = persona.enabled,
                            onCheckedChange = { personaViewModel.toggleEnabled(persona.id) },
                        )
                    }
                    Text(persona.systemPrompt)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        persona.enabledTools.forEach { tool ->
                            AssistChip(onClick = {}, label = { Text(toolLabel(tool)) })
                        }
                    }
                    if (persona.defaultProviderId.isNotBlank()) {
                        Text("默认模型：${persona.defaultProviderId}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { personaViewModel.delete(persona.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun toolLabel(tool: String): String {
    return when (tool) {
        "web_search" -> "网页搜索"
        "tts" -> "语音合成"
        "asr" -> "语音识别"
        else -> tool
    }
}
