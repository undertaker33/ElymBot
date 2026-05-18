package com.elymbot.android.feature.resource.data

import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.ResourceCenterCompatibilitySnapshot
import com.elymbot.android.model.ResourceConfigSnapshot
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureResourceCenterPortAdapter @Inject constructor(
    private val repository: FeatureResourceCenterRepositoryStore,
) : ResourceCenterPort {
    override val resources: StateFlow<List<ResourceCenterItem>>
        get() = repository.resources

    override val projections: StateFlow<List<ConfigResourceProjection>>
        get() = repository.projections

    override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> =
        repository.listResources(kind)

    override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        repository.saveResource(resource)

    override fun deleteResource(resourceId: String) =
        repository.deleteResource(resourceId)

    override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        repository.setProjection(projection)

    override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        repository.projectionsForConfig(configId)

    override fun compatibilitySnapshotForConfig(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot =
        repository.compatibilitySnapshotForConfig(config)
}
