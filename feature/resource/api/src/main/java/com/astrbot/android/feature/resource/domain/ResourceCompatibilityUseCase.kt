package com.astrbot.android.feature.resource.domain

import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceConfigSnapshot
import javax.inject.Inject

class ResourceCompatibilityUseCase @Inject constructor(
    private val resourceCenterPort: ResourceCenterPort,
) {
    fun snapshotForConfig(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot =
        resourceCenterPort.compatibilitySnapshotForConfig(config)
}
