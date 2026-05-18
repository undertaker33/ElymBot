package com.elymbot.android.data.db

import com.elymbot.android.model.SavedQqAccount

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
