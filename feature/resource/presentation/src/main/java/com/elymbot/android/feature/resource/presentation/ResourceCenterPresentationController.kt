package com.elymbot.android.feature.resource.presentation

import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import com.elymbot.android.feature.resource.domain.ResourceCompatibilityUseCase
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind

class ResourceCenterPresentationController(
    private val port: ResourceCenterPort,
    private val compatibilityUseCase: ResourceCompatibilityUseCase,
) {
    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem> =
        port.listResources(kind)

    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        port.saveResource(resource)

    fun deleteResource(resourceId: String) =
        port.deleteResource(resourceId)

    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        port.setProjection(projection)

    fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        port.projectionsForConfig(configId)

    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        compatibilityUseCase.snapshotForConfig(profile.toResourceConfigSnapshot())

    private fun ConfigProfile.toResourceConfigSnapshot(): ResourceConfigSnapshot =
        ResourceConfigSnapshot(
            id = id,
            mcpServers = mcpServers,
            skills = skills,
        )
}

