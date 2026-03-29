package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.BridgeViewModelDependencies
import com.astrbot.android.di.DefaultBridgeViewModelDependencies
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import kotlinx.coroutines.flow.StateFlow

class BridgeViewModel(
    private val dependencies: BridgeViewModelDependencies = DefaultBridgeViewModelDependencies,
) : ViewModel() {
    val config: StateFlow<NapCatBridgeConfig> = dependencies.config
    val runtimeState: StateFlow<NapCatRuntimeState> = dependencies.runtimeState

    fun saveConfig(config: NapCatBridgeConfig) {
        dependencies.saveConfig(config)
    }
}
