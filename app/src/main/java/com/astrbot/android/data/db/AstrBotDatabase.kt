package com.astrbot.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BotEntity::class, ConversationEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AstrBotDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: AstrBotDatabase? = null

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        botId TEXT NOT NULL,
                        personaId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        maxContextMessages INTEGER NOT NULL,
                        sessionSttEnabled INTEGER NOT NULL,
                        sessionTtsEnabled INTEGER NOT NULL,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        titleCustomized INTEGER NOT NULL DEFAULT 0,
                        messagesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE conversations ADD COLUMN titleCustomized INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        botId TEXT NOT NULL,
                        personaId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        platformId TEXT NOT NULL,
                        messageType TEXT NOT NULL,
                        originSessionId TEXT NOT NULL,
                        maxContextMessages INTEGER NOT NULL,
                        sessionSttEnabled INTEGER NOT NULL,
                        sessionTtsEnabled INTEGER NOT NULL,
                        pinned INTEGER NOT NULL,
                        titleCustomized INTEGER NOT NULL,
                        messagesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO conversations_new (
                        id, title, botId, personaId, providerId,
                        platformId, messageType, originSessionId,
                        maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                        pinned, titleCustomized, messagesJson, updatedAt
                    )
                    SELECT
                        id, title, botId, personaId, providerId,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'qq'
                            WHEN id LIKE 'qq-%-group-%' THEN 'qq'
                            ELSE 'app'
                        END AS platformId,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'friend'
                            WHEN id LIKE 'qq-%-group-%' THEN 'group'
                            ELSE 'other'
                        END AS messageType,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'friend:' || substr(id, instr(id, '-private-') + 9)
                            WHEN id LIKE 'qq-%-group-%' THEN
                                CASE
                                    WHEN instr(substr(id, instr(id, '-group-') + 7), '-user-') > 0 THEN
                                        'group:' ||
                                        substr(
                                            substr(id, instr(id, '-group-') + 7),
                                            1,
                                            instr(substr(id, instr(id, '-group-') + 7), '-user-') - 1
                                        ) ||
                                        ':user:' ||
                                        substr(
                                            substr(id, instr(id, '-group-') + 7),
                                            instr(substr(id, instr(id, '-group-') + 7), '-user-') + 6
                                        )
                                    ELSE 'group:' || substr(id, instr(id, '-group-') + 7)
                                END
                            ELSE id
                        END AS originSessionId,
                        maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                        pinned, titleCustomized, messagesJson, updatedAt
                    FROM conversations
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE conversations")
                database.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
            }
        }

        fun get(context: Context): AstrBotDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstrBotDatabase::class.java,
                    "astrbot-native.db",
                )
                    .addMigrations(migration2To3, migration3To4)
                    .addMigrations(migration4To5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
