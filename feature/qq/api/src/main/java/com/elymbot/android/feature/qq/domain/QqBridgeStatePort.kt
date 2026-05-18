package com.elymbot.android.feature.qq.domain

import com.elymbot.android.feature.qq.domain.model.NapCatBridgeConfig
import com.elymbot.android.feature.qq.domain.model.NapCatRuntimeState
import kotlinx.coroutines.flow.StateFlow

interface QqBridgeStatePort {
    val config: StateFlow<NapCatBridgeConfig>
    val runtimeState: StateFlow<NapCatRuntimeState>

    fun updateConfig(config: NapCatBridgeConfig)
}
