package com.astrbot.android.feature.resource.data

import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.StateFlow

object FeatureResourceCenterRepository {
    @Volatile
    private var delegate: FeatureResourceCenterRepositoryStore? = null

    internal fun installDelegate(store: FeatureResourceCenterRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureResourceCenterRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureResourceCenterRepository test facade was accessed before FeatureResourceCenterRepositoryStore was installed."
        }
    }

    val resources: StateFlow<List<ResourceCenterItem>>
        get() = repository().resources

    val projections: StateFlow<List<ConfigResourceProjection>>
        get() = repository().projections

    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem> = repository().listResources(kind)

    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem = repository().saveResource(resource)

    fun deleteResource(resourceId: String) = repository().deleteResource(resourceId)

    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        repository().setProjection(projection)

    fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        repository().projectionsForConfig(configId)

    fun projectionsForConfig(profile: ConfigProfile): List<ConfigResourceProjection> =
        repository().projectionsForConfig(profile)

    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        repository().compatibilitySnapshotForConfig(profile)
}
