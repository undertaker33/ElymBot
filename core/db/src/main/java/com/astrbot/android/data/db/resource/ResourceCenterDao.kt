package com.astrbot.android.data.db.resource

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceCenterDao {
    @Query("SELECT * FROM resource_center_items ORDER BY kind ASC, name ASC, updatedAt DESC")
    fun observeResources(): Flow<List<ResourceCenterItemEntity>>

    @Query("SELECT * FROM config_resource_projections ORDER BY configId ASC, kind ASC, sortIndex ASC")
    fun observeProjections(): Flow<List<ConfigResourceProjectionEntity>>

    @Query("SELECT * FROM resource_center_items ORDER BY kind ASC, name ASC, updatedAt DESC")
    suspend fun listResources(): List<ResourceCenterItemEntity>

    @Query("SELECT * FROM resource_center_items WHERE kind = :kind ORDER BY name ASC, updatedAt DESC")
    suspend fun listResources(kind: String): List<ResourceCenterItemEntity>

    @Query("SELECT * FROM config_resource_projections ORDER BY configId ASC, kind ASC, sortIndex ASC")
    suspend fun listProjections(): List<ConfigResourceProjectionEntity>

    @Query(
        """
        SELECT * FROM config_resource_projections
        WHERE configId = :configId
        ORDER BY kind ASC, sortIndex ASC, priority DESC
        """,
    )
    suspend fun projectionsForConfig(configId: String): List<ConfigResourceProjectionEntity>

    @Query("SELECT COUNT(*) FROM resource_center_items")
    suspend fun countResources(): Int

    @Query("SELECT COUNT(*) FROM config_resource_projections")
    suspend fun countProjections(): Int

    @Upsert
    suspend fun upsertResource(entity: ResourceCenterItemEntity)

    @Upsert
    suspend fun upsertResources(entities: List<ResourceCenterItemEntity>)

    @Query("DELETE FROM resource_center_items WHERE resourceId = :resourceId")
    suspend fun deleteResource(resourceId: String)

    @Upsert
    suspend fun upsertProjection(entity: ConfigResourceProjectionEntity)

    @Upsert
    suspend fun upsertProjections(entities: List<ConfigResourceProjectionEntity>)
}
