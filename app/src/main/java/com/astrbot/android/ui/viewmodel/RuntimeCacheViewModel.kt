package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.core.runtime.cache.RuntimeCacheMaintenancePort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RuntimeCacheViewModel @Inject constructor(
    private val runtimeCacheMaintenancePort: RuntimeCacheMaintenancePort,
) : ViewModel() {
    val state = runtimeCacheMaintenancePort.state

    suspend fun clearSafeRuntimeCaches(): String {
        return runtimeCacheMaintenancePort.clearSafeRuntimeCaches()
    }
}
