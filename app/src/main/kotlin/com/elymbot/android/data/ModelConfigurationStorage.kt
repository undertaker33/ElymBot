package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "model_services")
data class ModelServiceEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val catalogValue: String,
)

@Entity(tableName = "model_assignments")
data class ModelAssignmentEntity(
    @PrimaryKey val role: String,
    val serviceId: String,
    val modelId: String,
)

@Entity(
    tableName = "model_probe_results",
    primaryKeys = ["serviceId", "modelId", "baseUrl", "target"],
    foreignKeys = [
        ForeignKey(
            entity = ModelServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ModelProbeEntity(
    val serviceId: String,
    val modelId: String,
    val baseUrl: String,
    val target: String,
    val status: String,
    val failureKind: String?,
    val httpStatusCode: Int?,
)

@Entity(
    tableName = "service_credentials",
    foreignKeys = [
        ForeignKey(
            entity = ModelServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CredentialEntity(
    @PrimaryKey val serviceId: String,
    val iv: ByteArray,
    val encryptedKey: ByteArray,
)

@Dao
interface ModelConfigurationDao {
    @Query("SELECT * FROM model_services ORDER BY name, id")
    fun observeServices(): Flow<List<ModelServiceEntity>>

    @Query("SELECT * FROM model_services ORDER BY name, id")
    suspend fun getServices(): List<ModelServiceEntity>

    @Query("SELECT * FROM model_services WHERE id = :id")
    suspend fun getService(id: String): ModelServiceEntity?

    @Upsert
    suspend fun upsertService(item: ModelServiceEntity)

    @Query("DELETE FROM model_services WHERE id = :id")
    suspend fun deleteService(id: String)

    @Query("DELETE FROM model_assignments WHERE serviceId = :serviceId")
    suspend fun deleteAssignmentsForService(serviceId: String)

    @Query("SELECT * FROM model_assignments ORDER BY role")
    fun observeAssignments(): Flow<List<ModelAssignmentEntity>>

    @Query("SELECT * FROM model_assignments WHERE role = :role LIMIT 1")
    suspend fun getAssignment(role: String): ModelAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(item: ModelAssignmentEntity)

    @Query("DELETE FROM model_assignments WHERE role = :role")
    suspend fun deleteAssignment(role: String)

    @Query("SELECT * FROM model_probe_results WHERE serviceId = :serviceId AND modelId = :modelId AND baseUrl = :baseUrl")
    suspend fun getProbes(serviceId: String, modelId: String, baseUrl: String): List<ModelProbeEntity>

    @Query("DELETE FROM model_probe_results WHERE serviceId = :serviceId")
    suspend fun deleteProbes(serviceId: String)

    @Query("DELETE FROM model_probe_results WHERE serviceId = :serviceId AND modelId = :modelId AND baseUrl = :baseUrl")
    suspend fun deleteProbesForModel(serviceId: String, modelId: String, baseUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProbe(item: ModelProbeEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM service_credentials WHERE serviceId = :serviceId)")
    suspend fun hasCredential(serviceId: String): Boolean
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM service_credentials WHERE serviceId = :serviceId")
    suspend fun get(serviceId: String): CredentialEntity?

    @Query("SELECT serviceId FROM service_credentials")
    fun observeServiceIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CredentialEntity)

    @Query("DELETE FROM service_credentials WHERE serviceId = :serviceId")
    suspend fun delete(serviceId: String)
}
