package com.elymbot.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.elymbot.android.R

private enum class ConfigurationCategory(val label: Int) {
    Models(R.string.elym_config_models),
    Context(R.string.elym_config_context),
    Tools(R.string.elym_config_tools),
}

@Composable
internal fun ConfigurationPage(
    model: ElymBotViewModel,
    config: ModelConfiguration,
    back: (() -> Unit)?,
    current: Boolean,
    footerClearance: Dp,
) {
    var category by rememberSaveable { mutableStateOf(ConfigurationCategory.Models) }
    val modelScroll = rememberScrollState()
    PrimaryPage(
        title = R.string.elym_config,
        current = current,
        footerClearance = footerClearance,
        back = back,
        section = category,
        canScrollBackward = { category == ConfigurationCategory.Models && modelScroll.canScrollBackward },
        isScrollInProgress = { category == ConfigurationCategory.Models && modelScroll.isScrollInProgress },
        tags = {
            ConfigurationCategory.entries.forEach { item ->
                TagChip(stringResource(item.label), item == category) { category = item }
            }
        },
    ) { query, clearance ->
        val emptyLabel = stringResource(if (query.isNullOrEmpty()) R.string.elym_config_empty else R.string.elym_no_search_results)
        when (category) {
            ConfigurationCategory.Models -> {
                val scroll = if (query == null) modelScroll else key(query) { rememberScrollState() }
                ModelConfigurationContent(model, config, scroll, current, query, clearance)
            }
            ConfigurationCategory.Context, ConfigurationCategory.Tools -> Box(Modifier.padding(clearance)) {
                EmptyMessage(emptyLabel, if (category == ConfigurationCategory.Context) R.drawable.elym_chat else R.drawable.elym_box)
            }
        }
    }
}
