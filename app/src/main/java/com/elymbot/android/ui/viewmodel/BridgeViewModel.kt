package com.elymbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.elymbot.android.core.runtime.container.RuntimeBridgeController
import com.elymbot.android.di.hilt.BridgeConfig
import com.elymbot.android.di.hilt.BridgeConfigSaver
import com.elymbot.android.di.hilt.BridgeRuntimeState
import com.elymbot.android.model.NapCatBridgeConfig
import com.elymbot.android.model.NapCatRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeConfig val config: StateFlow<NapCatBridgeConfig>,
    @BridgeRuntimeState val runtimeState: StateFlow<NapCatRuntimeState>,
    private val configSaver: BridgeConfigSaver,
    private val runtimeBridgeController: RuntimeBridgeController,
) : ViewModel() {

    fun saveConfig(config: NapCatBridgeConfig) {
        configSaver.save(config)
    }

    fun startBridge() {
        runtimeBridgeController.startBridge()
    }

    fun stopBridge() {
        runtimeBridgeController.stopBridge()
    }

    fun checkBridge() {
        runtimeBridgeController.checkBridge()
    }
}
