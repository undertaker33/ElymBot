package com.elymbot.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elymbot.android.R

internal enum class PluginTab(val title: Int) {
    Discover(R.string.elym_plugins_discover), Installed(R.string.elym_plugins_installed),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginsPage(
    model: ElymBotViewModel,
    state: PluginManagementState?,
    current: Boolean,
    footerClearance: Dp,
    tab: PluginTab,
    onTabChange: (PluginTab) -> Unit,
    onBack: (() -> Unit)?,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var offer by remember { mutableStateOf<PluginReleaseOption?>(null) }
    var removing by remember { mutableStateOf<InstalledPluginSummary?>(null) }
    var deleteData by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<PluginManagementFailure?>(null) }
    val browse = rememberScrollState()
    val selected = state?.installed?.firstOrNull { it.pluginId == selectedId }

    fun action(request: suspend ElymBotUiService.() -> PluginManagementActionResult) {
        if (!current || busy) return
        busy = true
        failure = null
        model.perform {
            try {
                val result = model.service.request()
                if (result is PluginManagementActionResult.Rejected) failure = result.reason
            } finally {
                busy = false
            }
        }
    }

    PrimaryPage(
        R.string.elym_plugins, current, footerClearance, section = tab, onBack = onBack,
        canScrollBackward = { browse.canScrollBackward }, isScrollInProgress = { browse.isScrollInProgress },
        tags = { PluginTab.entries.forEach { item -> TagChip(stringResource(item.title), item == tab) { onTabChange(item) } } },
    ) { query, clearance ->
        val scroll = if (query == null) browse else key(query) { rememberScrollState() }
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 18.dp)
                .padding(top = clearance.calculateTopPadding(), bottom = clearance.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                PluginTab.Discover -> {
                    val status = state.directoryLabel()
                    if (status != null || query == null) {
                        val refreshable = state != null && state.trustConfigurationStatus == PluginTrustConfigurationStatus.Ready &&
                            state.directoryStatus != PluginDistributionStatus.Loading
                        Group {
                            ListRow(
                                stringResource(R.string.elym_plugins_refresh), R.drawable.elym_clock,
                                { if (current) model.perform { model.service.refreshPluginDirectory() } },
                                subtitle = status?.let { stringResource(it) }, enabled = refreshable,
                            )
                        }
                    }
                    val releases = state?.releases.orEmpty().filter {
                        query.matchesPageSearch(pluginName(it.reference.pluginId), it.reference.publisherId, it.reference.version)
                    }
                    if (releases.isEmpty() && query != null) {
                        EmptyMessage(stringResource(R.string.elym_no_search_results), R.drawable.elym_search)
                    } else if (releases.isNotEmpty()) Group {
                        releases.forEachIndexed { index, release ->
                            if (index > 0) HorizontalDivider(color = Line)
                            val ref = release.reference
                            val installed = state?.installed.orEmpty().firstOrNull { it.pluginId == ref.pluginId }
                            val sameVersion = installed != null && installed.publisherId == ref.publisherId && installed.version == ref.version
                            ListRow(
                                pluginName(ref.pluginId), R.drawable.elym_plugin, {
                                    if (sameVersion) selectedId = ref.pluginId else offer = release
                                },
                                subtitle = stringResource(R.string.elym_plugin_version, ref.version),
                                badge = stringResource(when {
                                    sameVersion -> R.string.elym_plugins_installed
                                    !release.compatible -> R.string.elym_plugin_incompatible
                                    else -> R.string.elym_plugin_install
                                }),
                                enabled = !busy && (sameVersion || release.compatible &&
                                    state?.trustConfigurationStatus == PluginTrustConfigurationStatus.Ready),
                            )
                        }
                    }
                }
                PluginTab.Installed -> {
                    val plugins = state?.installed.orEmpty().filter {
                        query.matchesPageSearch(pluginName(it.pluginId), it.publisherId, it.version)
                    }
                    if (plugins.isEmpty()) EmptyMessage(
                        stringResource(when {
                            query != null -> R.string.elym_no_search_results
                            state?.restorationComplete != true -> R.string.elym_plugin_restoring
                            else -> R.string.elym_no_plugins
                        }), R.drawable.elym_plugin,
                    ) else Group {
                        plugins.forEachIndexed { index, installed ->
                            if (index > 0) HorizontalDivider(color = Line)
                            ListRow(
                                pluginName(installed.pluginId), R.drawable.elym_plugin, { selectedId = installed.pluginId },
                                subtitle = stringResource(R.string.elym_plugin_version, installed.version),
                                badge = stringResource(installed.statusLabel),
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.takeIf { current && removing == null }?.let { installed ->
        Sheet(pluginName(installed.pluginId), { selectedId = null }) {
            Text(stringResource(R.string.elym_plugin_version, installed.version), color = Ink)
            Text(stringResource(installed.statusLabel), color = Muted)
            Text(stringResource(R.string.elym_plugin_publisher, installed.publisherId), color = Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val enable = !installed.enabled || installed.availability == PluginRuntimeAvailability.Unavailable
                Action(
                    stringResource(if (enable) R.string.elym_plugin_enable else R.string.elym_plugin_disable),
                    { action { if (enable) enablePlugin(installed.pluginId) else disablePlugin(installed.pluginId) } },
                    enabled = !busy && installed.availability != PluginRuntimeAvailability.Restoring && !installed.cleanupPending,
                )
                Action(stringResource(R.string.elym_plugin_uninstall), {
                    deleteData = false
                    removing = installed
                }, danger = true, enabled = !busy && installed.availability != PluginRuntimeAvailability.Restoring)
            }
        }
    }
    offer?.takeIf { current }?.let { release ->
        val ref = release.reference
        AlertDialog(
            onDismissRequest = { offer = null },
            title = { Text(stringResource(R.string.elym_plugin_install)) },
            text = { Text(stringResource(R.string.elym_plugin_install_reference, pluginName(ref.pluginId), ref.version)) },
            confirmButton = { TextButton(onClick = {
                offer = null
                action { installPlugin(ref) }
            }) { Text(stringResource(R.string.elym_plugin_install)) } },
            dismissButton = { TextButton(onClick = { offer = null }) { Text(stringResource(R.string.elym_cancel)) } },
        )
    }
    removing?.takeIf { current }?.let { installed ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.elym_plugin_uninstall)) },
            text = {
                Column {
                    Text(pluginName(installed.pluginId))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(deleteData, { deleteData = it }, modifier = Modifier.semanticsLabel(stringResource(R.string.elym_plugin_clear_data)))
                        Text(stringResource(R.string.elym_plugin_clear_data))
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val clear = deleteData
                removing = null
                action { uninstallPlugin(installed.pluginId, clear) }
            }) { Text(stringResource(R.string.elym_plugin_uninstall)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.elym_cancel)) } },
        )
    }
    failure?.takeIf { current }?.let { reason ->
        AlertDialog(
            onDismissRequest = { failure = null },
            text = { Text(stringResource(reason.label)) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.elym_close)) } },
        )
    }
}

@Composable
private fun pluginName(id: String): String = when (id) {
    "scheduled-tasks" -> stringResource(R.string.elym_scheduled_tasks)
    "qq" -> stringResource(R.string.elym_plugin_qq)
    else -> id
}

private fun PluginManagementState?.directoryLabel(): Int? = when {
    this == null || directoryStatus == PluginDistributionStatus.Loading -> R.string.elym_plugins_loading
    trustConfigurationStatus == PluginTrustConfigurationStatus.NotConfigured -> R.string.elym_plugins_unconfigured
    trustConfigurationStatus == PluginTrustConfigurationStatus.Invalid -> R.string.elym_plugins_trust_invalid
    usingLastGoodDirectory -> R.string.elym_plugins_cached
    directoryStatus == PluginDistributionStatus.Ready -> null
    directoryStatus == PluginDistributionStatus.NetworkError -> R.string.elym_plugins_network_error
    directoryStatus == PluginDistributionStatus.InvalidDirectory -> R.string.elym_plugins_directory_invalid
    else -> R.string.elym_plugins_directory_missing
}

private val InstalledPluginSummary.statusLabel: Int get() = when {
    cleanupPending -> R.string.elym_plugin_cleanup_pending
    availability == PluginRuntimeAvailability.Restoring -> R.string.elym_plugin_restoring
    availability == PluginRuntimeAvailability.Active -> R.string.elym_plugin_active
    availability == PluginRuntimeAvailability.Disabled -> R.string.elym_plugin_disabled
    else -> R.string.elym_plugin_unavailable
}

private val PluginManagementFailure.label: Int get() = when (this) {
    PluginManagementFailure.TrustNotConfigured -> R.string.elym_plugins_unconfigured
    PluginManagementFailure.DirectoryUnavailable -> R.string.elym_plugins_directory_missing
    PluginManagementFailure.ReleaseNotVerified -> R.string.elym_plugins_directory_invalid
    PluginManagementFailure.IncompatibleRelease -> R.string.elym_plugin_incompatible
    PluginManagementFailure.PluginNotInstalled -> R.string.elym_plugin_not_installed
    PluginManagementFailure.RuntimeUnavailable -> R.string.elym_plugin_unavailable
    PluginManagementFailure.InstallFailed -> R.string.elym_plugin_install_failed
    PluginManagementFailure.UninstallCleanupPending -> R.string.elym_plugin_cleanup_pending
}
