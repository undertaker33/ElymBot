
package com.astrbot.android.feature.resource.data

import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.resource.ResourceCenterDao
import com.astrbot.android.data.db.resource.toEntity
import com.astrbot.android.data.db.resource.toModel
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceConfigSnapshot
import com.astrbot.android.model.SkillEntry
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Deprecated("Use ResourceCenterPort from feature/resource/domain. Direct access will be removed.")
object FeatureResourceCenterRepository {
    @Volatile
    private var delegate: FeatureResourceCenterRepositoryStore? = null

    internal fun installDelegate(store: FeatureResourceCenterRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureResourceCenterRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureResourceCenterRepository was accessed before the Hilt graph created FeatureResourceCenterRepositoryStore."
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

@Singleton
class FeatureResourceCenterRepositoryStore @Inject constructor(
    private val resourceCenterDao: ResourceCenterDao,
    private val configAggregateDao: ConfigAggregateDao,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _resources = MutableStateFlow<List<ResourceCenterItem>>(emptyList())
    private val _projections = MutableStateFlow<List<ConfigResourceProjection>>(emptyList())

    val resources: StateFlow<List<ResourceCenterItem>> = _resources.asStateFlow()
    val projections: StateFlow<List<ConfigResourceProjection>> = _projections.asStateFlow()

    init {
        FeatureResourceCenterRepository.installDelegate(this)
        runBlocking(Dispatchers.IO) {
            seedFromLegacyConfigTablesIfNeeded()
            refreshFromStorage()
        }
        repositoryScope.launch {
            combine(
                resourceCenterDao.observeResources(),
                resourceCenterDao.observeProjections(),
            ) { resourceEntities, projectionEntities ->
                resourceEntities.map { it.toModel() } to projectionEntities.map { it.toModel() }
            }.collect { (resourceModels, projectionModels) ->
                _resources.value = resourceModels
                _projections.value = projectionModels
            }
        }
    }

    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem> {
        return runBlocking(Dispatchers.IO) {
            val entities = if (kind == null) {
                resourceCenterDao.listResources()
            } else {
                resourceCenterDao.listResources(kind.name)
            }
            entities.map { it.toModel() }
        }
    }

    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem {
        val normalized = resource.copy(
            resourceId = resource.resourceId.trim(),
            name = resource.name.trim(),
        )
        require(normalized.resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(normalized.name.isNotBlank()) { "name must not be blank" }

        runBlocking(Dispatchers.IO) {
            resourceCenterDao.upsertResource(normalized.toEntity())
            refreshFromStorage()
        }
        return normalized
    }

    fun deleteResource(resourceId: String) {
        runBlocking(Dispatchers.IO) {
            resourceCenterDao.deleteResource(resourceId)
            refreshFromStorage()
        }
    }

    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection {
        val normalized = projection.copy(
            configId = projection.configId.trim(),
            resourceId = projection.resourceId.trim(),
        )
        require(normalized.configId.isNotBlank()) { "configId must not be blank" }
        require(normalized.resourceId.isNotBlank()) { "resourceId must not be blank" }

        runBlocking(Dispatchers.IO) {
            resourceCenterDao.upsertProjection(normalized.toEntity())
            refreshFromStorage()
        }
        return normalized
    }

    fun projectionsForConfig(configId: String): List<ConfigResourceProjection> {
        return runBlocking(Dispatchers.IO) {
            resourceCenterDao.projectionsForConfig(configId).map { it.toModel() }
        }
    }

    fun projectionsForConfig(profile: ConfigProfile): List<ConfigResourceProjection> {
        val stored = projectionsForConfig(profile.id)
        return stored.ifEmpty {
            ResourceCenterCompatibility.projectionsFromConfigProfile(profile).projections
        }
    }

    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot {
        return compatibilitySnapshotForConfig(profile.toResourceConfigSnapshot())
    }

    fun compatibilitySnapshotForConfig(config: ResourceConfigSnapshot): ResourceCenterCompatibilitySnapshot {
        val storedProjections = projectionsForConfig(config.id)
        if (storedProjections.isNotEmpty()) {
            val resourceIds = storedProjections.map { it.resourceId }.toSet()
            return ResourceCenterCompatibilitySnapshot(
                resources = _resources.value.filter { it.resourceId in resourceIds },
                projections = storedProjections,
            )
        }
        return ResourceCenterCompatibility.projectionsFromConfigSnapshot(config)
    }

    private suspend fun seedFromLegacyConfigTablesIfNeeded() {
        if (resourceCenterDao.countResources() > 0 || resourceCenterDao.countProjections() > 0) return
        val snapshots = configAggregateDao
            .listConfigAggregates()
            .map { aggregate -> ResourceCenterCompatibility.projectionsFromConfigSnapshot(aggregate.toResourceConfigSnapshot()) }
        val resourcesToSeed = snapshots.flatMap { it.resources }.distinctBy { it.resourceId }
        val projectionsToSeed = snapshots.flatMap { it.projections }
        if (resourcesToSeed.isNotEmpty()) {
            resourceCenterDao.upsertResources(resourcesToSeed.map { it.toEntity() })
        }
        if (projectionsToSeed.isNotEmpty()) {
            resourceCenterDao.upsertProjections(projectionsToSeed.map { it.toEntity() })
        }
    }

    private suspend fun refreshFromStorage() {
        _resources.value = resourceCenterDao.listResources().map { it.toModel() }
        _projections.value = resourceCenterDao.listProjections().map { it.toModel() }
    }

    private fun ConfigProfile.toResourceConfigSnapshot(): ResourceConfigSnapshot {
        return ResourceConfigSnapshot(
            id = id,
            mcpServers = mcpServers,
            skills = skills,
        )
    }

    private fun com.astrbot.android.data.db.ConfigAggregate.toResourceConfigSnapshot(): ResourceConfigSnapshot {
        return ResourceConfigSnapshot(
            id = config.id,
            mcpServers = mcpServers.sortedBy { it.sortIndex }.map { it.toEntry() },
            skills = skills.sortedBy { it.sortIndex }.map { it.toEntry() },
        )
    }

    private fun com.astrbot.android.data.db.ConfigMcpServerEntity.toEntry(): McpServerEntry {
        return McpServerEntry(
            serverId = serverId,
            name = name,
            url = url,
            transport = transport,
            command = command,
            args = runCatching {
                val array = JSONArray(argsJson)
                (0 until array.length()).map { index -> array.getString(index) }
            }.getOrDefault(emptyList()),
            headers = runCatching {
                val json = JSONObject(headersJson)
                json.keys().asSequence().associateWith { key -> json.getString(key) }
            }.getOrDefault(emptyMap()),
            timeoutSeconds = timeoutSeconds,
            active = active,
        )
    }

    private fun com.astrbot.android.data.db.ConfigSkillEntity.toEntry(): SkillEntry {
        return SkillEntry(
            skillId = skillId,
            name = name,
            description = description,
            content = content,
            priority = priority,
            active = active,
        )
    }
}
