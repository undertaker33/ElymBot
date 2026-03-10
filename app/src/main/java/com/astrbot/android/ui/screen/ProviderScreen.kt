package com.astrbot.android.ui.screen

import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProviderScreen(providerViewModel: ProviderViewModel = viewModel()) {
    val providers by providerViewModel.providers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var editingEnabled by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var model by remember { mutableStateOf("gpt-4.1-mini") }
    var apiKey by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf(ProviderType.OPENAI_COMPATIBLE) }
    var chatEnabled by remember { mutableStateOf(true) }
    var ttsEnabled by remember { mutableStateOf(false) }
    var asrEnabled by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf(emptyList<String>()) }
    var isFetchingModels by remember { mutableStateOf(false) }

    fun resetForm() {
        editingProviderId = null
        editingEnabled = true
        name = ""
        baseUrl = "https://api.openai.com/v1"
        model = "gpt-4.1-mini"
        apiKey = ""
        providerType = ProviderType.OPENAI_COMPATIBLE
        chatEnabled = true
        ttsEnabled = false
        asrEnabled = false
        fetchedModels = emptyList()
    }

    fun loadProvider(provider: ProviderProfile) {
        editingProviderId = provider.id
        editingEnabled = provider.enabled
        name = provider.name
        baseUrl = provider.baseUrl
        model = provider.model
        apiKey = provider.apiKey
        providerType = provider.providerType
        chatEnabled = ProviderCapability.CHAT in provider.capabilities
        ttsEnabled = ProviderCapability.TTS in provider.capabilities
        asrEnabled = ProviderCapability.ASR in provider.capabilities
        fetchedModels = emptyList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("模型提供商", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (editingProviderId == null) "新增提供商" else "编辑提供商",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isFetchingModels = true
                                    fetchedModels = emptyList()
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            ChatCompletionService.fetchModels(
                                                baseUrl = baseUrl,
                                                apiKey = apiKey,
                                                providerType = providerType,
                                            )
                                        }
                                    }.onSuccess { models ->
                                        fetchedModels = models
                                        if (models.isNotEmpty()) {
                                            model = models.first()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }.onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: error.javaClass.simpleName,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    isFetchingModels = false
                                }
                            },
                            enabled = !isFetchingModels,
                        ) {
                            Text("获取模型")
                        }
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("提供商类型：${providerTypeLabel(providerType)}")
                        TextButton(
                            onClick = {
                                val nextIndex = (ProviderType.entries.indexOf(providerType) + 1) % ProviderType.entries.size
                                providerType = ProviderType.entries[nextIndex]
                                fetchedModels = emptyList()
                            },
                        ) {
                            Text("切换")
                        }
                    }
                    if (fetchedModels.isNotEmpty()) {
                        Text("可选模型", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fetchedModels.take(6).forEach { fetchedModel ->
                                AssistChip(
                                    onClick = { model = fetchedModel },
                                    label = { Text(fetchedModel) },
                                )
                            }
                        }
                    }
                    CapabilitySwitch("对话", chatEnabled) { chatEnabled = it }
                    CapabilitySwitch("语音合成", ttsEnabled) { ttsEnabled = it }
                    CapabilitySwitch("语音识别", asrEnabled) { asrEnabled = it }
                    CapabilitySwitch("启用", editingEnabled) { editingEnabled = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val capabilities = buildSet {
                                    if (chatEnabled) add(ProviderCapability.CHAT)
                                    if (ttsEnabled) add(ProviderCapability.TTS)
                                    if (asrEnabled) add(ProviderCapability.ASR)
                                }
                                if (name.isBlank() || model.isBlank() || capabilities.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "请填写名称、模型，并至少启用一种能力",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@Button
                                }
                                providerViewModel.save(
                                    id = editingProviderId,
                                    name = name,
                                    baseUrl = baseUrl,
                                    model = model,
                                    providerType = providerType,
                                    apiKey = apiKey,
                                    capabilities = capabilities,
                                    enabled = editingEnabled,
                                )
                                Toast.makeText(
                                    context,
                                    if (editingProviderId == null) "已新增提供商" else "已保存提供商",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                resetForm()
                            },
                        ) {
                            Text(if (editingProviderId == null) "新增提供商" else "保存修改")
                        }
                        OutlinedButton(onClick = { resetForm() }) {
                            Text("清空表单")
                        }
                    }
                }
            }
        }
        items(providers, key = { it.id }) { provider ->
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
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Text("${providerTypeLabel(provider.providerType)} / ${provider.model}")
                            Text("ID: ${provider.id}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { providerViewModel.toggleEnabled(provider.id) },
                        )
                    }
                    Text(provider.baseUrl)
                    Text(if (provider.apiKey.isBlank()) "未设置 API Key" else "已设置 API Key")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        provider.capabilities.forEach { capability ->
                            AssistChip(onClick = {}, label = { Text(capabilityLabel(capability)) })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { loadProvider(provider) }) {
                            Text("编辑")
                        }
                        TextButton(onClick = { providerViewModel.delete(provider.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilitySwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun providerTypeLabel(type: ProviderType): String {
    return when (type) {
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.GEMINI -> "Gemini"
        ProviderType.ANTHROPIC -> "Anthropic"
        ProviderType.CUSTOM -> "自定义"
    }
}

private fun capabilityLabel(capability: ProviderCapability): String {
    return when (capability) {
        ProviderCapability.CHAT -> "对话"
        ProviderCapability.TTS -> "语音合成"
        ProviderCapability.ASR -> "语音识别"
    }
}
