package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.SavedQqAccount

@Entity(tableName = "saved_qq_accounts")
data class SavedQqAccountEntity(
    @PrimaryKey val uin: String,
    val nickName: String,
    val avatarUrl: String,
    val sortIndex: Int,
    val updatedAt: Long,
)

fun SavedQqAccountEntity.toModel(): SavedQqAccount {
    return SavedQqAccount(
        uin = uin,
        nickName = nickName,
        avatarUrl = avatarUrl,
    )
}

fun SavedQqAccount.toEntity(sortIndex: Int): SavedQqAccountEntity {
    return SavedQqAccountEntity(
        uin = uin,
        nickName = nickName,
        avatarUrl = avatarUrl,
        sortIndex = sortIndex,
        updatedAt = System.currentTimeMillis(),
    )
}
