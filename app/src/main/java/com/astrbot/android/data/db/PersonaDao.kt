package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM persona_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    fun observePersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM persona_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    suspend fun listPersonas(): List<PersonaEntity>

    @Upsert
    suspend fun upsertAll(entities: List<PersonaEntity>)

    @Query("DELETE FROM persona_profiles WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("DELETE FROM persona_profiles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM persona_profiles")
    suspend fun count(): Int
}
