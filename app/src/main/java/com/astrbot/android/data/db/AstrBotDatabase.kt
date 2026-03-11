package com.astrbot.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BotEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AstrBotDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao

    companion object {
        @Volatile
        private var instance: AstrBotDatabase? = null

        fun get(context: Context): AstrBotDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstrBotDatabase::class.java,
                    "astrbot-native.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
