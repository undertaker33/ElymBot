package com.elymbot.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R
import com.elymbot.core.contract.PlatformLoginChallenge
import com.elymbot.core.contract.PlatformRuntimeFailureReason
import com.elymbot.core.contract.PlatformRuntimePhase
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

internal const val QqPluginId = "qq"

@Composable
internal fun PlatformPage(
    state: PlatformConnectionsState?,
    current: Boolean,
    footerClearance: Dp,
    onQq: () -> Unit,
    onPlugins: (() -> Unit)?,
) {
    val browse = rememberScrollState()
    val connection = state?.takeIf { it.authorized }?.connections
        ?.firstOrNull { it.pluginId == QqPluginId && it.availability != PlatformAvailability.NotInstalled }
    val name = stringResource(R.string.elym_plugin_qq)
    PrimaryPage(
        R.string.elym_platform, current, footerClearance,
        canScrollBackward = { browse.canScrollBackward }, isScrollInProgress = { browse.isScrollInProgress },
    ) { query, clearance ->
        Column(Modifier.fillMaxWidth().verticalScroll(browse).padding(clearance).padding(horizontal = 18.dp)) {
            when {
                state != null && state.authorized && !state.restorationComplete ->
                    LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.elym_platform_loading)))
                connection == null -> EmptyMessage(stringResource(R.string.elym_no_platforms), R.drawable.elym_platform) {
                    if (query == null && onPlugins != null) Action(stringResource(R.string.elym_platform_find_plugins), onPlugins)
                }
                !query.matchesPageSearch(name, connection.runtime?.accountId.orEmpty()) ->
                    EmptyMessage(stringResource(R.string.elym_no_search_results), R.drawable.elym_search)
                else -> Group {
                    ListRow(name, R.drawable.elym_platform, onQq, subtitle = connection.runtime?.accountId,
                        badge = stringResource(connection.statusLabel))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QqPlatformPage(
    model: ElymBotViewModel,
    sessionId: String?,
    connection: PlatformConnectionState?,
    current: Boolean,
    back: () -> Unit,
    onPlugins: (() -> Unit)?,
) {
    var working by remember(sessionId) { mutableStateOf(false) }
    var failure by remember(sessionId) { mutableStateOf<PlatformUiFailure?>(null) }
    var chooseBinding by remember(sessionId) { mutableStateOf(false) }
    var confirmation by remember(sessionId) { mutableStateOf<QqConfirmation?>(null) }
    var editingSettings by remember(sessionId) { mutableStateOf<QqPlatformSettingsSnapshot?>(null) }
    var settingsFailure by remember(sessionId) { mutableStateOf<PlatformUiFailure?>(null) }
    val runtime = connection?.runtime
    val phase = runtime?.phase
    val active = connection != null && connection.availability == PlatformAvailability.Active && connection.enabled
    val account = runtime?.accountId
    val binding = connection?.ownerBinding
    val policyState by key(sessionId, current, active) {
        val policy = remember {
            if (current && active && sessionId != null) model.service.qqPlatformSettings(sessionId)
            else flowOf<QqPlatformSettingsUiState?>(null)
        }
        policy.collectAsStateWithLifecycle(initialValue = null)
    }
    val policy = policyState?.takeIf { it.available }?.snapshot
    fun act(request: suspend ElymBotUiService.(String) -> PlatformUiActionResult) {
        if (!current || sessionId == null || working || !active) return
        working = true
        failure = null
        model.perform {
            try {
                val result = model.service.request(sessionId)
                if (result is PlatformUiActionResult.Rejected) failure = result.reason
            } finally {
                working = false
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 5.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
            Text(stringResource(R.string.elym_plugin_qq), Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
        }
        Column(
            Modifier.fillMaxWidth().then(LocalPageBodyMotion.current).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.elym_qq_account_connection)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(account?.let { stringResource(R.string.elym_qq_account, it) } ?: stringResource(R.string.elym_qq_signed_out), color = Ink)
                    Text(stringResource(connection?.statusLabel ?: R.string.elym_qq_unavailable), color = Muted)
                    val percent = runtime?.progressPercent
                    if (percent != null && phase in listOf(PlatformRuntimePhase.PREPARING, PlatformRuntimePhase.STARTING)) {
                        LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    runtime?.failure?.let { Text(stringResource(it.reason.label), color = Danger) }
                    if (current && active && phase == PlatformRuntimePhase.WAITING_FOR_LOGIN) {
                        runtime.loginChallenge?.let { challenge ->
                            QqLoginQr(challenge, Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                    if (active && sessionId != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (phase == null || phase == PlatformRuntimePhase.STOPPED ||
                            phase == PlatformRuntimePhase.FAILED && runtime.failure?.retryable == true) {
                            Action(stringResource(R.string.elym_qq_start), { act { id -> startPlatform(id, QqPluginId) } }, primary = true, enabled = !working)
                        }
                        if (phase == PlatformRuntimePhase.WAITING_FOR_LOGIN) {
                            Action(stringResource(R.string.elym_qq_refresh_login), { act { id -> refreshPlatformLogin(id, QqPluginId) } }, enabled = !working)
                        }
                        if (account != null && (phase == PlatformRuntimePhase.CONNECTED || phase == PlatformRuntimePhase.FAILED)) {
                            Action(stringResource(R.string.elym_qq_reconnect), { act { id -> reconnectPlatform(id, QqPluginId) } }, enabled = !working)
                            Action(stringResource(R.string.elym_qq_logout), { confirmation = QqConfirmation.Logout }, enabled = !working)
                        }
                        if (phase != null && phase != PlatformRuntimePhase.STOPPED && phase != PlatformRuntimePhase.STOPPING) {
                            Action(stringResource(R.string.elym_qq_stop), { act { id -> stopPlatform(id, QqPluginId) } }, enabled = !working)
                        }
                    }
                    if (!active && onPlugins != null) Action(stringResource(R.string.elym_faq_open_plugins), onPlugins)
                }
            }
            if (binding != null || active && account != null && phase == PlatformRuntimePhase.CONNECTED) {
                Section(stringResource(R.string.elym_qq_binding)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (binding != null) {
                            Text(stringResource(R.string.elym_qq_binding_values, binding.accountId, binding.userId), color = Ink)
                            if (!binding.activeInstallation) Text(stringResource(R.string.elym_qq_binding_inactive), color = Muted)
                            if (active) Action(stringResource(R.string.elym_qq_unbind), { confirmation = QqConfirmation.Unbind }, enabled = !working)
                        }
                        if (active && account != null && phase == PlatformRuntimePhase.CONNECTED) {
                            Action(stringResource(R.string.elym_qq_bind), { chooseBinding = true }, enabled = !working)
                        }
                    }
                }
            }
            if (policy != null) Section(stringResource(R.string.elym_qq_rules)) {
                ListRow(stringResource(R.string.elym_qq_edit_rules), R.drawable.elym_sliders, {
                    settingsFailure = null
                    editingSettings = policy
                })
            }
        }
    }
    editingSettings?.takeIf { current && active && policy != null && sessionId != null }?.let { original ->
        val id = checkNotNull(sessionId)
        key(original.revision) {
            QqPolicyEditor(original, working, settingsFailure, { editingSettings = null }) { revision, draft ->
                if (!working) {
                    settingsFailure = null
                    working = true
                    model.perform {
                        try {
                            when (val result = model.service.updateQqPlatformSettings(id, revision, draft)) {
                                is QqPlatformSettingsUiResult.Saved -> editingSettings = null
                                is QqPlatformSettingsUiResult.Conflict -> settingsFailure = PlatformUiFailure.SettingsConflict
                                is QqPlatformSettingsUiResult.Rejected -> settingsFailure = result.reason
                            }
                        } finally {
                            working = false
                        }
                    }
                }
            }
        }
    }
    if (current && active && account != null && chooseBinding) {
        var userId by remember(account) { mutableStateOf("") }
        Sheet(stringResource(R.string.elym_qq_bind), { if (!working) chooseBinding = false }) {
            Text(stringResource(R.string.elym_qq_account, account), color = Ink)
            Field(stringResource(R.string.elym_qq_binding_user), userId, { userId = it.filter(Char::isDigit).take(20) })
            Action(stringResource(R.string.elym_qq_bind), {
                val selectedUser = userId
                chooseBinding = false
                act { id -> bindQqOwnerPrivate(id, account, selectedUser) }
            }, primary = true, enabled = !working && userId.isNotBlank())
        }
    }
    confirmation?.takeIf { current && active }?.let { requested ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(requested.label)) },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                act { id -> when (requested) {
                    QqConfirmation.Logout -> logoutPlatform(id, QqPluginId)
                    QqConfirmation.Unbind -> unbindQqOwnerPrivate(id)
                } }
            }) { Text(stringResource(requested.label)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.elym_cancel)) } },
        )
    }
    failure?.takeIf { current }?.let { reason ->
        AlertDialog(
            onDismissRequest = { failure = null }, text = { Text(stringResource(reason.label)) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.elym_close)) } },
        )
    }
}

@Composable
private fun QqLoginQr(challenge: PlatformLoginChallenge, modifier: Modifier = Modifier) {
    val expiresAt = challenge.expiresAtEpochMillis
    var expired by remember(challenge) { mutableStateOf(expiresAt != null && expiresAt <= System.currentTimeMillis()) }
    LaunchedEffect(challenge) {
        if (expiresAt != null && !expired) {
            delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
            expired = true
        }
    }
    if (expired) {
        Text(stringResource(R.string.elym_qq_login_qr_expired), modifier, color = Muted)
    } else {
        val matrix = challenge.qrMatrix
        val quietZone = 4
        Canvas(modifier.widthIn(max = 248.dp).fillMaxWidth().aspectRatio(1f)
            .semanticsLabel(stringResource(R.string.elym_qq_login_qr))) {
            drawRect(Color.White)
            val cells = matrix.dimension + quietZone * 2
            val modulePx = (minOf(size.width, size.height) / cells).toInt()
            if (modulePx <= 0) return@Canvas
            val offsetX = ((size.width - cells * modulePx) / 2).toInt() + quietZone * modulePx
            val offsetY = ((size.height - cells * modulePx) / 2).toInt() + quietZone * modulePx
            val moduleSize = Size(modulePx.toFloat(), modulePx.toFloat())
            matrix.darkModules.forEachIndexed { index, dark ->
                if (dark) drawRect(
                    Color.Black,
                    topLeft = Offset(
                        (offsetX + index % matrix.dimension * modulePx).toFloat(),
                        (offsetY + index / matrix.dimension * modulePx).toFloat(),
                    ),
                    size = moduleSize,
                )
            }
        }
    }
}

private enum class QqConfirmation(val label: Int) {
    Logout(R.string.elym_qq_logout), Unbind(R.string.elym_qq_unbind),
}

private val PlatformConnectionState.statusLabel: Int get() = when (availability) {
    PlatformAvailability.NotInstalled -> R.string.elym_plugin_not_installed
    PlatformAvailability.Disabled -> R.string.elym_plugin_disabled
    PlatformAvailability.Restoring -> R.string.elym_plugin_restoring
    PlatformAvailability.Unavailable -> R.string.elym_qq_unavailable
    PlatformAvailability.Active -> when (runtime?.phase) {
        null, PlatformRuntimePhase.STOPPED -> R.string.elym_qq_stopped
        PlatformRuntimePhase.PREPARING -> R.string.elym_qq_preparing
        PlatformRuntimePhase.STARTING -> R.string.elym_qq_starting
        PlatformRuntimePhase.WAITING_FOR_LOGIN -> R.string.elym_qq_waiting_login
        PlatformRuntimePhase.CONNECTING -> R.string.elym_qq_connecting
        PlatformRuntimePhase.CONNECTED -> R.string.elym_qq_connected
        PlatformRuntimePhase.STOPPING -> R.string.elym_qq_stopping
        PlatformRuntimePhase.FAILED -> R.string.elym_qq_failed
    }
}

private val PlatformRuntimeFailureReason.label: Int get() = when (this) {
    PlatformRuntimeFailureReason.ASSET_PREPARATION -> R.string.elym_qq_assets_failed
    PlatformRuntimeFailureReason.STARTUP -> R.string.elym_qq_start_failed
    PlatformRuntimeFailureReason.AUTHENTICATION -> R.string.elym_qq_login_failed
    PlatformRuntimeFailureReason.CONNECTION -> R.string.elym_qq_connect_failed
    PlatformRuntimeFailureReason.PROTOCOL, PlatformRuntimeFailureReason.INTERNAL -> R.string.elym_qq_failed
}

internal val PlatformUiFailure.label: Int get() = when (this) {
    PlatformUiFailure.UnauthorizedSession, PlatformUiFailure.PluginUnavailable -> R.string.elym_qq_unavailable
    PlatformUiFailure.RuntimeFailure -> R.string.elym_qq_failed
    PlatformUiFailure.InvalidBinding -> R.string.elym_qq_invalid_binding
    PlatformUiFailure.SettingsUnavailable -> R.string.elym_qq_settings_unavailable
    PlatformUiFailure.SettingsConflict -> R.string.elym_qq_settings_conflict
}
