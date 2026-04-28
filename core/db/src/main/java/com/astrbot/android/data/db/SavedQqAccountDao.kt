package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQqAccountDao {
    @Query("SELECT * FROM saved_qq_accounts ORDER BY sortIndex ASC, updatedAt ASC")
    fun observeAccounts(): Flow<List<SavedQqAccountEntity>>

    @Query("SELECT * FROM saved_qq_accounts ORDER BY sortIndex ASC, updatedAt ASC")
    suspend fun listAccounts(): List<SavedQqAccountEntity>

    @Upsert
    suspend fun upsertAll(entities: List<SavedQqAccountEntity>)

    @Query("DELETE FROM saved_qq_accounts WHERE uin NOT IN (:uins)")
    suspend fun deleteMissing(uins: List<String>)

    @Query("DELETE FROM saved_qq_accounts")
    suspend fun clearAll()
}
