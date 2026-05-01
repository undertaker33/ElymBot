package com.astrbot.android.core.runtime.context

/**
 * Platform-specific identity needed by the runtime orchestrator.
 *
 * Actual platform implementations live in their respective platform modules.
 */
interface PlatformRuntimeAdapter {
    val platform: RuntimePlatform
    val platformAdapterType: String
    val platformInstanceKey: String
}
