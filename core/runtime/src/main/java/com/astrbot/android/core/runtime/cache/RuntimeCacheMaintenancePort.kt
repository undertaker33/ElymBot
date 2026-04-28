package com.astrbot.android.core.runtime.cache

import kotlinx.coroutines.flow.StateFlow

data class RuntimeCacheCleanupState(
    val isRunning: Boolean = false,
    val lastSummary: String = "",
)

interface RuntimeCacheMaintenancePort {
    val state: StateFlow<RuntimeCacheCleanupState>

    suspend fun clearSafeRuntimeCaches(): String
}
