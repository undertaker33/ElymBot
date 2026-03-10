package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import kotlinx.coroutines.flow.StateFlow

class BridgeViewModel : ViewModel() {
    val config: StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config
    val runtimeState: StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState

    fun saveConfig(config: NapCatBridgeConfig) {
        NapCatBridgeRepository.updateConfig(config)
    }
}
