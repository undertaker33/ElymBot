package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BotDao {
    @Query("SELECT * FROM bots ORDER BY updatedAt DESC")
    fun observeBots(): Flow<List<BotEntity>>

    @Upsert
    suspend fun upsert(entity: BotEntity)

    @Query("DELETE FROM bots WHERE id = :botId")
    suspend fun deleteById(botId: String)

    @Query("SELECT COUNT(*) FROM bots")
    suspend fun count(): Int
}
