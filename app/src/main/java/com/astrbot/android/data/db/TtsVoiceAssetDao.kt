package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsVoiceAssetDao {
    @Query("SELECT * FROM tts_voice_assets ORDER BY createdAt DESC")
    fun observeAssets(): Flow<List<TtsVoiceAssetEntity>>

    @Query("SELECT * FROM tts_voice_assets ORDER BY createdAt DESC")
    suspend fun listAssets(): List<TtsVoiceAssetEntity>

    @Upsert
    suspend fun upsertAll(entities: List<TtsVoiceAssetEntity>)

    @Query("DELETE FROM tts_voice_assets WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("DELETE FROM tts_voice_assets")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM tts_voice_assets")
    suspend fun count(): Int
}
