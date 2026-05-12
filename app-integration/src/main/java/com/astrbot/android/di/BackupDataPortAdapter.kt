package com.astrbot.android.di

import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.AppBackupExternalState
import com.astrbot.android.core.db.backup.ConversationBackupDataPort
import com.astrbot.android.core.db.backup.ConversationImportPreview
import com.astrbot.android.core.db.backup.ConversationImportResult
import com.astrbot.android.feature.bot.data.FeatureBotRepositoryStore
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import com.astrbot.android.feature.conversation.data.FeatureConversationRepositoryStore
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryStore
import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryStore
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.astrbot.android.feature.qq.domain.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

internal class HiltAppBackupDataPort @Inject constructor(
    private val botRepository: FeatureBotRepositoryStore,
    private val providerRepository: FeatureProviderRepositoryStore,
    private val personaRepository: FeaturePersonaRepositoryStore,
    private val configRepository: FeatureConfigRepositoryStore,
    private val conversationRepository: FeatureConversationRepositoryStore,
    private val qqLoginRepository: QqLoginRepositoryPort,
) : AppBackupDataPort {
    override fun snapshotBots(): List<BotProfile> = botRepository.snapshotProfiles()

    override fun snapshotProviders(): List<ProviderProfile> = providerRepository.snapshotProfiles()

    override fun snapshotPersonas(): List<PersonaProfile> = personaRepository.snapshotProfiles()

    override fun snapshotConfigs(): List<ConfigProfile> = configRepository.snapshotProfiles()

    override fun snapshotConversations(): List<ConversationSession> = conversationRepository.snapshotSessions()

    override fun snapshotExternalState(): AppBackupExternalState {
        val loginState = qqLoginRepository.loginState.value
        return AppBackupExternalState(
            selectedBotId = botRepository.selectedBotId.value,
            selectedConfigId = configRepository.selectedProfileId.value,
            quickLoginUin = loginState.quickLoginUin,
            savedAccounts = loginState.savedAccounts,
        )
    }

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) {
        botRepository.restoreProfiles(profiles, selectedBotId)
    }

    override fun restoreProviders(profiles: List<ProviderProfile>) {
        providerRepository.restoreProfiles(profiles)
    }

    override fun restorePersonas(profiles: List<PersonaProfile>) {
        personaRepository.restoreProfiles(profiles)
    }

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) {
        configRepository.restoreProfiles(profiles, selectedConfigId)
    }

    override suspend fun restoreConversations(sessions: List<ConversationSession>) {
        conversationRepository.restoreSessionsDurable(sessions)
    }

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>) {
        qqLoginRepository.restoreSavedLoginState(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }
}

internal class HiltConversationBackupDataPort @Inject constructor(
    private val conversationRepository: FeatureConversationRepositoryStore,
    private val botRepository: BotRepositoryPort,
) : ConversationBackupDataPort {
    override val isReady: StateFlow<Boolean> = conversationRepository.isReady
    override val sessions: StateFlow<List<ConversationSession>> = conversationRepository.sessions
    override val defaultSessionTitle: String = "\u65B0\u5BF9\u8BDD"

    override fun selectedBotId(): String = botRepository.selectedBotId.value

    override fun snapshotSessions(): List<ConversationSession> = conversationRepository.snapshotSessions()

    override fun restoreSessions(restoredSessions: List<ConversationSession>) {
        conversationRepository.restoreSessions(restoredSessions)
    }

    override fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        val preview = conversationRepository.previewImportedSessions(importedSessions)
        return ConversationImportPreview(
            totalSessions = preview.totalSessions,
            duplicateSessions = preview.duplicateSessions,
            newSessions = preview.newSessions,
        )
    }

    override fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val result = conversationRepository.importSessions(
            importedSessions = importedSessions,
            overwriteDuplicates = overwriteDuplicates,
        )
        return ConversationImportResult(
            importedCount = result.importedCount,
            overwrittenCount = result.overwrittenCount,
            skippedCount = result.skippedCount,
        )
    }

    override suspend fun importSessionsDurable(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val result = conversationRepository.importSessionsDurable(
            importedSessions = importedSessions,
            overwriteDuplicates = overwriteDuplicates,
        )
        return ConversationImportResult(
            importedCount = result.importedCount,
            overwrittenCount = result.overwrittenCount,
            skippedCount = result.skippedCount,
        )
    }
}
