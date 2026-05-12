package com.astrbot.android.core.db.backup

import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.feature.qq.domain.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession

data class AppBackupExternalState(
    val selectedBotId: String = "",
    val selectedConfigId: String = "",
    val quickLoginUin: String = "",
    val savedAccounts: List<SavedQqAccount> = emptyList(),
)

interface AppBackupDataPort {
    fun snapshotBots(): List<BotProfile>
    fun snapshotProviders(): List<ProviderProfile>
    fun snapshotPersonas(): List<PersonaProfile>
    fun snapshotConfigs(): List<ConfigProfile>
    fun snapshotConversations(): List<ConversationSession>
    fun snapshotExternalState(): AppBackupExternalState
    suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String)
    fun restoreProviders(profiles: List<ProviderProfile>)
    fun restorePersonas(profiles: List<PersonaProfile>)
    fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String)
    suspend fun restoreConversations(sessions: List<ConversationSession>)
    fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>)
}
