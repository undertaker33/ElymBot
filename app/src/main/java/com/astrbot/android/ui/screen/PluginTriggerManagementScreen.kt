package com.astrbot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.ExternalPluginTriggerAvailability
import com.astrbot.android.model.plugin.ExternalPluginTriggerPolicy
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeBinder
import com.astrbot.android.ui.AppDestination
import com.astrbot.android.ui.RegisterSecondaryTopBar
import com.astrbot.android.ui.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.SecondaryTopBarSpec
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class PluginTriggerManagementSection {
    Summary,
    Readiness,
    OpenTriggers,
    ClosedTriggers,
    Notes,
}

internal enum class PluginTriggerManagementStatus {
    Ready,
    Disabled,
    MissingContract,
    MissingEntry,
    InvalidContract,
}

internal data class PluginTriggerItemUiState(
    val trigger: PluginTriggerSource,
    val availability: ExternalPluginTriggerAvailability,
)

internal data class PluginTriggerManagementState(
    val status: PluginTriggerManagementStatus,
    val statusDetail: String = "",
    val runtimeLabel: String = "js_quickjs",
    val entryPath: String = "",
    val contractVersion: String = "-",
    val canManualOpen: Boolean = false,
    val openTriggers: List<PluginTriggerItemUiState> = emptyList(),
    val closedTriggers: List<PluginTriggerItemUiState> = emptyList(),
)

@Composable
fun PluginTriggerManagementScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()

    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForDetail(pluginId)
    }

    RegisterSecondaryTopBar(
        route = AppDestination.PluginTriggers.route,
        spec = SecondaryTopBarSpec.SubPage(
            title = stringResource(R.string.plugin_trigger_title),
            onBack = onBack,
        ),
    )

    val record = uiState.selectedPlugin
    val triggerState by produceState<PluginTriggerManagementState?>(initialValue = null, record) {
        value = record?.let { selectedRecord ->
            withContext(Dispatchers.IO) {
                buildPluginTriggerManagementState(
                    binding = ExternalPluginRuntimeBinder().bind(selectedRecord),
                )
            }
        }
    }

    BoxWithTriggerPageBackground {
        if (record != null && triggerState != null) {
            PluginTriggerManagementWorkspace(
                record = record,
                state = triggerState!!,
            )
        }
    }
}

@Composable
private fun PluginTriggerManagementWorkspace(
    record: PluginInstallRecord,
    state: PluginTriggerManagementState,
) {
    val sections = remember { buildTriggerManagementSections() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.PluginTriggersPageTag),
        contentPadding = PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        item {
            SecondaryTopBarPlaceholder()
        }

        items(sections, key = { it.name }) { section ->
            when (section) {
                PluginTriggerManagementSection.Summary -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_title)) {
                        Text(
                            text = record.manifestSnapshot.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                        )
                        Text(
                            text = record.manifestSnapshot.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }

                PluginTriggerManagementSection.Readiness -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_readiness_title)) {
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_runtime_label),
                            value = state.runtimeLabel,
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_binding_status_label),
                            value = triggerStatusLabel(state.status),
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_contract_version_label),
                            value = state.contractVersion,
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_entry_path_label),
                            value = state.entryPath.ifBlank { stringResource(R.string.plugin_trigger_not_available) },
                        )
                        if (state.statusDetail.isNotBlank()) {
                            Text(
                                text = state.statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }

                PluginTriggerManagementSection.OpenTriggers -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_open_title)) {
                        if (state.openTriggers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.plugin_trigger_open_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonochromeUi.textSecondary,
                            )
                        } else {
                            state.openTriggers.forEach { item ->
                                TriggerKeyValue(
                                    label = triggerLabel(item.trigger),
                                    value = stringResource(R.string.plugin_trigger_status_open),
                                )
                            }
                        }
                    }
                }

                PluginTriggerManagementSection.ClosedTriggers -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_closed_title)) {
                        if (state.closedTriggers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.plugin_trigger_closed_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonochromeUi.textSecondary,
                            )
                        } else {
                            state.closedTriggers.forEach { item ->
                                TriggerKeyValue(
                                    label = triggerLabel(item.trigger),
                                    value = stringResource(R.string.plugin_trigger_status_closed),
                                )
                            }
                        }
                    }
                }

                PluginTriggerManagementSection.Notes -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_notes_title)) {
                        Text(
                            text = if (state.canManualOpen) {
                                stringResource(R.string.plugin_trigger_notes_entry_enabled)
                            } else {
                                stringResource(R.string.plugin_trigger_notes_entry_disabled)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.plugin_trigger_notes_dispatcher),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

internal fun buildPluginTriggerManagementState(
    binding: ExternalPluginRuntimeBinding,
): PluginTriggerManagementState {
    val status = when (binding.status) {
        ExternalPluginExecutionBindingStatus.READY -> PluginTriggerManagementStatus.Ready
        ExternalPluginExecutionBindingStatus.DISABLED -> PluginTriggerManagementStatus.Disabled
        ExternalPluginExecutionBindingStatus.MISSING_CONTRACT -> PluginTriggerManagementStatus.MissingContract
        ExternalPluginExecutionBindingStatus.MISSING_ENTRY -> PluginTriggerManagementStatus.MissingEntry
        ExternalPluginExecutionBindingStatus.INVALID_CONTRACT -> PluginTriggerManagementStatus.InvalidContract
    }
    val declaredTriggers = binding.contract
        ?.supportedTriggers
        ?.toList()
        ?.sortedBy { it.ordinal }
        .orEmpty()
    val grouped = declaredTriggers.map { trigger ->
        PluginTriggerItemUiState(
            trigger = trigger,
            availability = ExternalPluginTriggerPolicy.availability(trigger),
        )
    }
    val openTriggers = grouped.filter { it.availability == ExternalPluginTriggerAvailability.OPEN_V1 }
    val closedTriggers = grouped.filter { it.availability == ExternalPluginTriggerAvailability.DECLARED_BUT_CLOSED }
    return PluginTriggerManagementState(
        status = status,
        statusDetail = binding.errorSummary,
        runtimeLabel = pluginRuntimeDisplayLabel(binding.contract?.entryPoint?.runtimeKind),
        entryPath = binding.entryAbsolutePath.ifBlank {
            binding.contract?.entryPoint?.path.orEmpty()
        },
        contractVersion = binding.contract?.contractVersion?.toString() ?: "-",
        canManualOpen = openTriggers.any { it.trigger == PluginTriggerSource.OnPluginEntryClick },
        openTriggers = openTriggers,
        closedTriggers = closedTriggers,
    )
}

internal fun pluginRuntimeDisplayLabel(runtimeKind: ExternalPluginRuntimeKind?): String {
    return when (runtimeKind) {
        ExternalPluginRuntimeKind.JsQuickJs -> "JavaScript (QuickJS)"
        null -> "JavaScript (QuickJS)"
    }
}

private fun buildTriggerManagementSections(
): List<PluginTriggerManagementSection> {
    return buildList {
        add(PluginTriggerManagementSection.Summary)
        add(PluginTriggerManagementSection.Readiness)
        add(PluginTriggerManagementSection.OpenTriggers)
        add(PluginTriggerManagementSection.ClosedTriggers)
        add(PluginTriggerManagementSection.Notes)
    }
}

@Composable
private fun TriggerSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            content()
        }
    }
}

@Composable
private fun TriggerKeyValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MonochromeUi.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
    }
}

@Composable
private fun triggerStatusLabel(status: PluginTriggerManagementStatus): String {
    return when (status) {
        PluginTriggerManagementStatus.Ready -> stringResource(R.string.plugin_trigger_binding_ready)
        PluginTriggerManagementStatus.Disabled -> stringResource(R.string.plugin_trigger_binding_disabled)
        PluginTriggerManagementStatus.MissingContract -> stringResource(R.string.plugin_trigger_binding_missing_contract)
        PluginTriggerManagementStatus.MissingEntry -> stringResource(R.string.plugin_trigger_binding_missing_entry)
        PluginTriggerManagementStatus.InvalidContract -> stringResource(R.string.plugin_trigger_binding_invalid_contract)
    }
}

@Composable
private fun triggerLabel(trigger: PluginTriggerSource): String {
    return when (trigger) {
        PluginTriggerSource.OnMessageReceived -> stringResource(R.string.plugin_trigger_on_message_received)
        PluginTriggerSource.BeforeSendMessage -> stringResource(R.string.plugin_trigger_before_send_message)
        PluginTriggerSource.AfterModelResponse -> stringResource(R.string.plugin_trigger_after_model_response)
        PluginTriggerSource.OnSchedule -> stringResource(R.string.plugin_trigger_on_schedule)
        PluginTriggerSource.OnPluginEntryClick -> stringResource(R.string.plugin_trigger_on_plugin_entry_click)
        PluginTriggerSource.OnCommand -> stringResource(R.string.plugin_trigger_on_command)
        PluginTriggerSource.OnConversationEnter -> stringResource(R.string.plugin_trigger_on_conversation_enter)
    }
}

@Composable
private fun BoxWithTriggerPageBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        content()
    }
}
