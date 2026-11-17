package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "command_names")
data class CommandNameEntity(
    @PrimaryKey val behaviorId: String,
    val name: String,
)

@Dao
interface CommandNameDao {
    @Query("SELECT * FROM command_names ORDER BY behaviorId")
    fun observeAll(): Flow<List<CommandNameEntity>>

    @Query("SELECT * FROM command_names ORDER BY behaviorId")
    suspend fun getAll(): List<CommandNameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CommandNameEntity>)
}
