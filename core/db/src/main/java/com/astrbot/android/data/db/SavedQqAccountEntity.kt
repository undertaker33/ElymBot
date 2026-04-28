package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_qq_accounts")
data class SavedQqAccountEntity(
    @PrimaryKey val uin: String,
    val nickName: String,
    val avatarUrl: String,
    val sortIndex: Int,
    val updatedAt: Long,
)
