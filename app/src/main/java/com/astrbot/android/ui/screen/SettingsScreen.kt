package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.ui.viewmodel.BridgeViewModel

@Composable
fun SettingsScreen(bridgeViewModel: BridgeViewModel = viewModel()) {
    val context = LocalContext.current
    val bridgeConfig by bridgeViewModel.config.collectAsState()
    val runtimeState by bridgeViewModel.runtimeState.collectAsState()

    var endpoint by remember(bridgeConfig.endpoint) { mutableStateOf(bridgeConfig.endpoint) }
    var healthUrl by remember(bridgeConfig.healthUrl) { mutableStateOf(bridgeConfig.healthUrl) }
    var startCommand by remember(bridgeConfig.startCommand) { mutableStateOf(bridgeConfig.startCommand) }
    var stopCommand by remember(bridgeConfig.stopCommand) { mutableStateOf(bridgeConfig.stopCommand) }
    var statusCommand by remember(bridgeConfig.statusCommand) { mutableStateOf(bridgeConfig.statusCommand) }
    var commandPreview by remember(bridgeConfig.commandPreview) { mutableStateOf(bridgeConfig.commandPreview) }
    var autoStart by remember(bridgeConfig.autoStart) { mutableStateOf(bridgeConfig.autoStart) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("桥接地址") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = healthUrl,
                    onValueChange = { healthUrl = it },
                    label = { Text("健康检查地址") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commandPreview,
                    onValueChange = { commandPreview = it },
                    label = { Text("命令预览") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startCommand,
                    onValueChange = { startCommand = it },
                    label = { Text("启动命令") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stopCommand,
                    onValueChange = { stopCommand = it },
                    label = { Text("停止命令") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = statusCommand,
                    onValueChange = { statusCommand = it },
                    label = { Text("状态命令") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("自动启动 NapCat")
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                }
                Button(
                    onClick = {
                        bridgeViewModel.saveConfig(
                            NapCatBridgeConfig(
                                endpoint = endpoint,
                                healthUrl = healthUrl,
                                startCommand = startCommand,
                                stopCommand = stopCommand,
                                statusCommand = statusCommand,
                                commandPreview = commandPreview,
                                autoStart = autoStart,
                            ),
                        )
                    },
                ) {
                    Text("保存桥接配置")
                }
            }
        }
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("桥接运行状态", style = MaterialTheme.typography.titleMedium)
                Text("状态：${runtimeState.status}")
                Text("最后动作：${runtimeState.lastAction}")
                Text("详情：${runtimeState.details}")
                Button(onClick = { ContainerBridgeController.start(context) }) {
                    Text("启动桥接")
                }
                Button(onClick = { ContainerBridgeController.check(context) }) {
                    Text("检查桥接")
                }
                Button(onClick = { ContainerBridgeController.stop(context) }) {
                    Text("停止桥接")
                }
            }
        }
    }
}
