package com.elymbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R

@Composable
internal fun ModelsPage(model: ElymBotViewModel, config: ModelConfiguration, back: (() -> Unit)?) {
    var editor by remember { mutableStateOf<ModelServiceDraft?>(null) }
    var hasKey by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf<ModelRole?>(null) }
    Column {
        Header(stringResource(R.string.elym_config), back)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 7.dp, bottom = 100.dp)) {
            Section(stringResource(R.string.elym_model_services)) {
                config.services.forEach { item ->
                    ListRow(
                        item.name, R.drawable.elym_box,
                        {
                            editor = ModelServiceDraft(item.id, item.provider, item.name, item.baseUrl, modelId = item.modelId, catalog = item.catalog)
                            hasKey = item.hasKey
                        },
                        subtitle = item.modelId, badge = if (!item.hasKey) stringResource(R.string.elym_key_missing) else null,
                    )
                    HorizontalDivider(color = Line)
                }
                ListRow(stringResource(R.string.elym_add_service), R.drawable.elym_plus, { editor = ModelServiceDraft(); hasKey = false }, subtitle = stringResource(R.string.elym_openai_compatible))
            }
            Section(stringResource(R.string.elym_model_roles)) {
                ListRow(stringResource(R.string.elym_text_role), R.drawable.elym_chat, { role = ModelRole.Text }, subtitle = roleSummary(config.text, config.services))
                HorizontalDivider(color = Line)
                ListRow(stringResource(R.string.elym_vision_role), R.drawable.elym_photo, { role = ModelRole.Vision }, subtitle = roleSummary(config.vision, config.services))
            }
        }
    }
    editor?.let { initial -> ModelEditor(model, initial, hasKey, { editor = null }) }
    role?.let { selected ->
        val choice = if (selected == ModelRole.Text) config.text else config.vision
        var edited by remember(selected, choice) { mutableStateOf(choice) }
        Sheet(stringResource(if (selected == ModelRole.Text) R.string.elym_text_model else R.string.elym_vision_model), { role = null }) {
            Group {
                ListRow(stringResource(R.string.elym_unselected), R.drawable.elym_box, { edited = null }, badge = selectedBadge(edited == null))
                config.services.forEach { service ->
                    (listOf(service.modelId) + service.catalog).distinct().forEach { id ->
                        HorizontalDivider(color = Line)
                        val item = ModelChoice(service.id, id)
                        ListRow(id, R.drawable.elym_box, { edited = item }, subtitle = service.name, badge = selectedBadge(edited == item))
                    }
                }
            }
            Action(stringResource(R.string.elym_save), { model.perform { model.service.setModel(selected, edited); role = null } }, primary = true)
        }
    }
}

@Composable
private fun roleSummary(choice: ModelChoice?, services: List<ModelServiceItem>): String {
    val service = services.firstOrNull { it.id == choice?.serviceId }
    return if (service == null || choice == null) stringResource(R.string.elym_unselected)
    else stringResource(R.string.elym_role_summary, choice.modelId, service.name)
}

@Composable
private fun selectedBadge(selected: Boolean): String? = if (selected) stringResource(R.string.elym_is_selected) else null

private enum class Provider(val key: String, val label: Int, val url: String, val model: String) {
    OpenAi("openai", R.string.elym_provider_openai, "https://api.openai.com/v1", "gpt-4.1-mini"),
    DeepSeek("deepseek", R.string.elym_provider_deepseek, "https://api.deepseek.com", "deepseek-flash"),
    Kimi("kimi", R.string.elym_provider_kimi, "https://api.moonshot.cn/v1", "kimi-k2.6"),
    Qwen("qwen", R.string.elym_provider_qwen, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    OpenRouter("openrouter", R.string.elym_provider_openrouter, "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini"),
    Groq("groq", R.string.elym_provider_groq, "https://api.groq.com/openai/v1", "openai/gpt-oss-20b"),
    Custom("custom", R.string.elym_provider_custom, "", ""),
}

@Composable
private fun ModelEditor(model: ElymBotViewModel, initial: ModelServiceDraft, hasKey: Boolean, dismiss: () -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ModelServiceDraft?>(null) }
    var delete by remember { mutableStateOf(false) }
    val provider = Provider.entries.firstOrNull { it.key == draft.provider }
    val providerName = stringResource(provider?.label ?: R.string.elym_select_provider)
    val validConnection = draft.baseUrl.isNotBlank() && (hasKey || draft.apiKey.isNotBlank())
    val validModel = validConnection && draft.modelId.isNotBlank()
    val service = model.service
    Sheet(stringResource(if (initial.id == null) R.string.elym_add_service else R.string.elym_edit_service), dismiss) {
        Text(stringResource(R.string.elym_provider), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box {
            Action(providerName, { providerMenu = true })
            DropdownMenu(providerMenu, { providerMenu = false }, containerColor = SurfaceColor) {
                Provider.entries.forEach { item ->
                    val label = stringResource(item.label)
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            draft = draft.copy(provider = item.key, name = if (item == Provider.Custom) "" else label, baseUrl = item.url, modelId = item.model, catalog = emptyList())
                            providerMenu = false
                        },
                    )
                }
            }
        }
        Field(stringResource(R.string.elym_base_url), draft.baseUrl, { draft = draft.copy(baseUrl = it, catalog = emptyList()) }, stringResource(R.string.elym_url_hint))
        Field(stringResource(R.string.elym_key), draft.apiKey, { draft = draft.copy(apiKey = it) }, stringResource(if (hasKey) R.string.elym_saved_key else R.string.elym_key_hint), password = true)
        Field(stringResource(R.string.elym_default_model), draft.modelId, { draft = draft.copy(modelId = it.take(200)) }, stringResource(R.string.elym_model_hint))
        Field(stringResource(R.string.elym_service_name), draft.name, { draft = draft.copy(name = it.take(40)) }, stringResource(R.string.elym_optional))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(
                stringResource(if (fetching) R.string.elym_probing else R.string.elym_probe_list),
                {
                    fetching = true
                    model.perform {
                        try {
                            val catalog = service.fetchModels(draft)
                            draft = draft.copy(catalog = catalog)
                            modelList = catalog
                        } finally {
                            fetching = false
                        }
                    }
                }, enabled = validConnection && !fetching,
            )
            Action(stringResource(R.string.elym_probe_capabilities), { probe = draft }, enabled = validModel)
        }
        HorizontalDivider(color = Line)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.elym_save), {
                model.perform {
                    service.saveModelService(draft.copy(name = draft.name.trim().ifEmpty { providerName }, baseUrl = draft.baseUrl.trim(), modelId = draft.modelId.trim()))
                    dismiss()
                }
            }, primary = true, enabled = provider != null && validModel)
            if (initial.id != null) Action(stringResource(R.string.elym_delete_service), { delete = true }, danger = true)
        }
    }
    modelList?.let { catalog ->
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(draft.modelId) }
        ProbeDialog(stringResource(R.string.elym_probe_result), { modelList = null }) {
            Text(pluralStringResource(R.plurals.elym_models_found, catalog.size, catalog.size), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(draft.baseUrl, color = Muted, fontSize = 12.sp)
            Input(query, { query = it }, stringResource(R.string.elym_search_models))
            catalog.filter { it.contains(query.trim(), ignoreCase = true) }.forEach { id ->
                ListRow(id, R.drawable.elym_box, { selected = id }, badge = selectedBadge(selected == id), trailing = false)
            }
            Action(stringResource(R.string.elym_use_model), { draft = draft.copy(modelId = selected); modelList = null }, primary = true, enabled = selected in catalog)
        }
    }
    probe?.let { input ->
        val result by remember(input) { service.probeModel(input) }.collectAsStateWithLifecycle(ModelProbe())
        ProbeDialog(stringResource(R.string.elym_probe_capabilities), { probe = null }) {
            Text(input.modelId, color = Muted, fontSize = 12.sp)
            Capability(R.string.elym_model_available, result.availability, result.failuresByTarget[ProbeTarget.Availability])
            Capability(R.string.elym_vision_support, result.vision, result.failuresByTarget[ProbeTarget.Vision])
            Capability(R.string.elym_streaming_support, result.streaming, result.failuresByTarget[ProbeTarget.Streaming])
            result.issue?.let { Text(stringResource(issueLabel(it)), color = Danger, fontSize = 13.sp) }
        }
    }
    if (delete) ProbeDialog(stringResource(R.string.elym_delete_service), { delete = false }) {
        Text(stringResource(R.string.elym_confirm_service, initial.name), color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.elym_delete_service), {
                val id = initial.id ?: return@Action
                model.perform { service.deleteModelService(id); dismiss() }
            }, danger = true)
            Action(stringResource(R.string.elym_cancel), { delete = false })
        }
    }
}

@Composable
private fun ProbeDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().heightIn(max = 510.dp).background(SurfaceColor, shape).padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                RoundAction(R.drawable.elym_close, stringResource(R.string.elym_close), dismiss)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun Capability(label: Int, status: ProbeStatus, failure: ProbeFailure?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(PaperTop, shape).border(1.dp, Line, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val statusLabel = when (status) {
            ProbeStatus.Waiting -> R.string.elym_probe_waiting
            ProbeStatus.Supported -> if (label == R.string.elym_model_available) R.string.elym_available else R.string.elym_supported
            ProbeStatus.Unsupported -> if (label == R.string.elym_model_available) R.string.elym_unavailable else R.string.elym_unsupported
            ProbeStatus.Unknown -> R.string.elym_unknown
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(label), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Badge(stringResource(statusLabel), status == ProbeStatus.Supported)
        }
        if (failure != null) {
            val explanation = stringResource(issueLabel(failure.issue))
            val statusCode = failure.httpStatusCode
            Text(
                if (statusCode == null) explanation else stringResource(R.string.elym_http_issue, explanation, statusCode),
                color = Danger,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun CommandsPage(model: ElymBotViewModel, names: Map<ChatCommand, String>) {
    var editing by remember { mutableStateOf(false) }
    Column {
        Header(stringResource(R.string.elym_settings))
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Group {
                ListRow(stringResource(R.string.elym_command_names), R.drawable.elym_sliders, { editing = true })
            }
        }
    }
    if (editing) {
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
