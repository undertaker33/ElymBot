package com.astrbot.android.feature.bot.data

import com.astrbot.android.feature.bot.domain.model.BotProfile
import kotlinx.coroutines.flow.StateFlow

object FeatureBotRepository {
    const val PREF_SELECTED_BOT_ID = "selected_bot_id"

    @Volatile
    private var delegate: FeatureBotRepositoryStore? = null

    internal fun installDelegate(store: FeatureBotRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureBotRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureBotRepository test facade was accessed before a test installed FeatureBotRepositoryStore."
        }
    }

    val botProfile: StateFlow<BotProfile>
        get() = repository().botProfile

    val botProfiles: StateFlow<List<BotProfile>>
        get() = repository().botProfiles

    val selectedBotId: StateFlow<String>
        get() = repository().selectedBotId

    fun select(botId: String) = repository().select(botId)

    fun save(profile: BotProfile) = repository().save(profile)

    fun create(name: String = "New Bot"): BotProfile = repository().create(name)

    fun snapshotProfiles(): List<BotProfile> = repository().snapshotProfiles()

    suspend fun restoreProfiles(
        profiles: List<BotProfile>,
        selectedBotId: String?,
    ) = repository().restoreProfiles(profiles, selectedBotId)

    fun delete(botId: String) = repository().delete(botId)

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) =
        repository().replaceConfigBinding(deletedConfigId, fallbackConfigId)

    fun resolveBoundBot(selfId: String): BotProfile? = repository().resolveBoundBot(selfId)

    fun shouldPersistConversation(botId: String): Boolean = repository().shouldPersistConversation(botId)
}
