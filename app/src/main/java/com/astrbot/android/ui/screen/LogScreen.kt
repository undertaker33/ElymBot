package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.astrbot.android.runtime.RuntimeLogRepository

@Composable
fun LogScreen() {
    val logs by RuntimeLogRepository.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val joinedLogs = logs.joinToString(separator = "\n")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("运行日志", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(joinedLogs))
                    Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                },
                enabled = logs.isNotEmpty(),
            ) {
                Text("复制全部")
            }
            OutlinedButton(
                onClick = {
                    RuntimeLogRepository.clear()
                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                },
                enabled = logs.isNotEmpty(),
            ) {
                Text("清空")
            }
            Text(
                text = "${logs.size} 行",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        SelectionContainer {
            Text(
                text = if (logs.isEmpty()) "暂无日志" else joinedLogs,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            )
        }
    }
}
