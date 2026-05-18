package com.elymbot.android.feature.resource.domain

import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceConfigSnapshot
import javax.inject.Inject

class ResourceCompatibilityUseCase @Inject constructor(
    private val resourceCenterPort: ResourceCenterPort,
) {
    fun snapshotForConfig(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot =
        resourceCenterPort.compatibilitySnapshotForConfig(config)
}
