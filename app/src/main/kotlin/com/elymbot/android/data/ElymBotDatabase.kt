package com.elymbot.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonaEntity::class,
        ConversationEntity::class,
        TurnEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        CheckpointEntity::class,
        HistorySummaryEntity::class,
        ModelServiceEntity::class,
        ModelAssignmentEntity::class,
        ModelProbeEntity::class,
        CommandNameEntity::class,
        CredentialEntity::class,
        PersonaTagEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ElymBotDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun personaTagDao(): PersonaTagDao
    abstract fun conversationDao(): ConversationDao
    abstract fun turnDao(): TurnDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun historySummaryDao(): HistorySummaryDao
    abstract fun modelConfigurationDao(): ModelConfigurationDao
    abstract fun credentialDao(): CredentialDao
    abstract fun commandNameDao(): CommandNameDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `persona_tags` (`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`name`))",
                )

                val names = linkedSetOf<String>()
                database.query("SELECT `tagsValue` FROM `personas` ORDER BY `sortOrder`, `id`").use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getString(0).split('\u001f')
                            .filter(String::isNotEmpty)
                            .forEach { names.add(it) }
                    }
                }

                names.forEachIndexed { index, name ->
                    database.execSQL(
                        "INSERT INTO `persona_tags` (`name`, `sortOrder`) VALUES (?, ?)",
                        arrayOf<Any>(name, index.toLong()),
                    )
                }
            }
        }

        fun open(context: Context): ElymBotDatabase =
            Room.databaseBuilder(context.applicationContext, ElymBotDatabase::class.java, "elymbot.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
