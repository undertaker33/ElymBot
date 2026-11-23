package com.elymbot.android.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.elymbot.android.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppUpdatePrompt(model: ElymBotViewModel, candidate: AppUpdateCandidate?, onDismiss: () -> Unit) {
    val service = model.service
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var failure by rememberSaveable { mutableStateOf<AppUpdateInstallFailure?>(null) }
    var ticketId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionRequired by remember { mutableStateOf(false) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val completed = ticketId
        ticketId = null
        if (completed != null) model.perform { service.finishAppUpdateInstall(completed) }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRequired = false
    }

    fun prepare(offer: AppUpdateCandidate) {
        if (downloading || ticketId != null) return
        if (!service.canRequestAppUpdateInstall()) {
            permissionRequired = true
            return
        }
        downloading = true
        scope.launch {
            var unhandedTicket: String? = null
            try {
                when (val result = service.prepareAppUpdateInstall(offer.id)) {
                    is AppUpdateInstallResult.Ready -> {
                        unhandedTicket = result.ticket.id
                        ensureActive()
                        ticketId = result.ticket.id
                        val launched = try {
                            installer.launch(result.ticket.intent)
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        } catch (_: SecurityException) {
                            false
                        } catch (_: IllegalStateException) {
                            false
                        }
                        if (launched) {
                            unhandedTicket = null
                            onDismiss()
                        } else {
                            failure = AppUpdateInstallFailure.InstallerUnavailable
                        }
                    }
                    AppUpdateInstallResult.InstallerPermissionRequired -> permissionRequired = true
                    is AppUpdateInstallResult.Rejected -> failure = result.reason
                }
            } finally {
                downloading = false
                unhandedTicket?.let { id ->
                    ticketId = null
                    withContext(NonCancellable) { service.finishAppUpdateInstall(id) }
                }
            }
        }
    }

    val installFailure = failure
    when {
        installFailure != null -> AlertDialog(
            onDismissRequest = { failure = null },
            text = { Text(stringResource(installFailure.label)) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.elym_close)) } },
        )
        permissionRequired -> AlertDialog(
            onDismissRequest = { permissionRequired = false },
            text = { Text(stringResource(R.string.elym_update_permission)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        settings.launch(service.appUpdatePermissionSettingsIntent())
                    } catch (_: ActivityNotFoundException) {
                        permissionRequired = false
                        failure = AppUpdateInstallFailure.InstallerUnavailable
                    } catch (_: SecurityException) {
                        permissionRequired = false
                        failure = AppUpdateInstallFailure.InstallerUnavailable
                    }
                }) { Text(stringResource(R.string.elym_update_permissions_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { permissionRequired = false }) { Text(stringResource(R.string.elym_cancel)) }
            },
        )
        candidate != null && ticketId == null -> AlertDialog(
            onDismissRequest = { if (!downloading) onDismiss() },
            title = { Text(stringResource(R.string.elym_update_available)) },
            text = { Text(stringResource(R.string.elym_about_version, candidate.versionName)) },
            confirmButton = {
                TextButton(onClick = { prepare(candidate) }, enabled = !downloading) {
                    Text(stringResource(if (downloading) R.string.elym_update_downloading else R.string.elym_update_download))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !downloading) { Text(stringResource(R.string.elym_guide_later)) } },
        )
    }
}

private val AppUpdateInstallFailure.label: Int get() = when (this) {
    AppUpdateInstallFailure.OfferExpired -> R.string.elym_update_expired
    AppUpdateInstallFailure.InstallerUnavailable -> R.string.elym_update_installer_unavailable
    AppUpdateInstallFailure.DownloadFailed -> R.string.elym_update_download_failed
}
