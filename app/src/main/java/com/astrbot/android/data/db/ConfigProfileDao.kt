package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigProfileDao {
    @Query("SELECT * FROM config_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    fun observeProfiles(): Flow<List<ConfigProfileEntity>>

    @Query("SELECT * FROM config_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    suspend fun listProfiles(): List<ConfigProfileEntity>

    @Upsert
    suspend fun upsertAll(entities: List<ConfigProfileEntity>)

    @Query("DELETE FROM config_profiles WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("DELETE FROM config_profiles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM config_profiles")
    suspend fun count(): Int
}
