package com.elymbot.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elymbot.android.R

@Composable
internal fun CheckpointsSheet(model: ElymBotViewModel, sessionId: String, running: Boolean, dismiss: () -> Unit) {
    val service = model.service
    val checkpoints by remember(sessionId) { service.checkpoints(sessionId) }
        .collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatCheckpoint?>(null) }
    val enabled = !running && !pending && checkpoints.isNotEmpty()
    fun runCheckpointAction(action: suspend () -> Unit) {
        if (!enabled || pending) return
        pending = true
        model.perform {
            try {
                action()
            } finally {
                pending = false
            }
        }
    }

    Sheet(
        stringResource(R.string.elym_checkpoints),
        dismiss,
        maxContentHeightFraction = 0.88f,
        deliberateSwipe = true,
    ) {
        Action(
            stringResource(R.string.elym_save_checkpoint),
            { runCheckpointAction { service.saveCheckpoint(sessionId) } },
            primary = true,
            enabled = enabled,
        )
        if (checkpoints.isNotEmpty()) Group {
            checkpoints.sortedByDescending(ChatCheckpoint::displayNumber).forEachIndexed { index, point ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(
                    stringResource(R.string.elym_checkpoint_title, point.displayNumber),
                    R.drawable.elym_clock,
                    { selected = point },
                    subtitle = pluralStringResource(
                        R.plurals.elym_checkpoint_retained_messages, point.retainedMessageCount, point.retainedMessageCount,
                    ),
                    badge = if (point.current) stringResource(R.string.elym_checkpoint_current) else null,
                    selected = point.current,
                    enabled = enabled,
                )
            }
        }
    }
    selected?.let { point ->
        val warning = if (point.displayNumber == 1) {
            R.string.elym_restore_empty_checkpoint_message
        } else {
            R.string.elym_restore_checkpoint_message
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.elym_restore_checkpoint_title, point.displayNumber)) },
            text = { Text(stringResource(warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selected = null
                        runCheckpointAction {
                            service.restoreCheckpoint(sessionId, point.displayNumber, point.id)
                            dismiss()
                        }
                    },
                    enabled = enabled,
                ) { Text(stringResource(R.string.elym_restore_checkpoint), color = if (enabled) Danger else Color.Unspecified) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.elym_cancel)) }
            },
        )
    }
}
