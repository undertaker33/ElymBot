package com.elymbot.android.feature.resource.domain

import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.StateFlow

interface ResourceCenterPort {
    val resources: StateFlow<List<ResourceCenterItem>>
    val projections: StateFlow<List<ConfigResourceProjection>>
    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem>
    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem
    fun deleteResource(resourceId: String)
    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection
    fun projectionsForConfig(configId: String): List<ConfigResourceProjection>
    fun compatibilitySnapshotForConfig(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot
}
