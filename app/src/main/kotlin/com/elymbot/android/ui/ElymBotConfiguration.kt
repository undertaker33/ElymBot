package com.elymbot.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.elymbot.android.R

private enum class ConfigurationCategory(val label: Int) {
    Models(R.string.elym_config_models),
    Context(R.string.elym_config_context),
    Tools(R.string.elym_config_tools),
}

@Composable
internal fun ConfigurationPage(model: ElymBotViewModel, config: ModelConfiguration, back: (() -> Unit)?, current: Boolean) {
    var category by rememberSaveable { mutableStateOf(ConfigurationCategory.Models) }
    val modelScroll = rememberScrollState()
    Column {
        Header(stringResource(R.string.elym_config), back)
        TagStrip {
            ConfigurationCategory.entries.forEach { item ->
                TagChip(stringResource(item.label), item == category) { category = item }
            }
        }
        when (category) {
            ConfigurationCategory.Models -> ModelConfigurationContent(model, config, modelScroll, current)
            ConfigurationCategory.Context -> EmptyMessage(stringResource(R.string.elym_config_empty), R.drawable.elym_chat)
            ConfigurationCategory.Tools -> EmptyMessage(stringResource(R.string.elym_config_empty), R.drawable.elym_box)
        }
    }
}
