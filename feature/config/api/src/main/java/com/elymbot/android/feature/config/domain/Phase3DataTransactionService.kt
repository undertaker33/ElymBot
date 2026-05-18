package com.elymbot.android.feature.config.domain

interface Phase3DataTransactionService {
    suspend fun deleteConfigProfile(profileId: String)

    suspend fun deleteBotProfile(botId: String)
}
