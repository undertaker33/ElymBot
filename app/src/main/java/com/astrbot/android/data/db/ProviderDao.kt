package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM provider_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    suspend fun listProviders(): List<ProviderEntity>

    @Upsert
    suspend fun upsertAll(entities: List<ProviderEntity>)

    @Query("DELETE FROM provider_profiles WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("DELETE FROM provider_profiles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM provider_profiles")
    suspend fun count(): Int
}
