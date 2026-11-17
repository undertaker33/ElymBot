package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "personas",
    indices = [Index(value = ["sortOrder"])],
)
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val tagsValue: String,
    val prompt: String,
    val favorite: Boolean,
    val coverPath: String?,
    val atlasTile: Int,
    val coverX: Float,
    val coverY: Float,
    val avatarX: Float,
    val avatarY: Float,
    val sortOrder: Int,
)

@Entity(tableName = "persona_tags")
data class PersonaTagEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int,
)

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY sortOrder, id")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun get(id: String): PersonaEntity?

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM personas")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<PersonaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: PersonaEntity)

    @Update
    suspend fun update(item: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PersonaTagDao {
    @Query("SELECT name FROM persona_tags ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT name FROM persona_tags WHERE name = :name")
    suspend fun get(name: String): String?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM persona_tags")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PersonaTagEntity)
}
