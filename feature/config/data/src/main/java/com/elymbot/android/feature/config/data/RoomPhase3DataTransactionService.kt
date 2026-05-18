package com.elymbot.android.feature.config.data

import androidx.room.withTransaction
import com.elymbot.android.core.common.profile.ProfileCatalogKind
import com.elymbot.android.core.common.profile.ProfileDeletionGuard
import com.elymbot.android.data.db.AppPreferenceEntity
import com.elymbot.android.data.db.ElymBotDatabase
import com.elymbot.android.data.db.BotAggregate
import com.elymbot.android.data.db.BotWriteModel
import com.elymbot.android.data.db.ConversationAggregate
import com.elymbot.android.data.db.ConversationAggregateWriteModel
import com.elymbot.android.data.db.ConversationEntity
import com.elymbot.android.data.db.toProfile
import com.elymbot.android.data.db.toWriteModel
import com.elymbot.android.feature.config.domain.Phase3DataTransactionService
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SELECTED_CONFIG_PROFILE_PREF_KEY = "selected_config_profile_id"
private const val SELECTED_BOT_PREF_KEY = "selected_bot_id"
private const val DEFAULT_APP_CHAT_SESSION_ID = "chat-main"
private const val DEFAULT_APP_CHAT_SESSION_TITLE = "新对话"

class RoomPhase3DataTransactionService @Inject constructor(
    private val database: ElymBotDatabase,
) : Phase3DataTransactionService {
    override suspend fun deleteConfigProfile(profileId: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val configDao = database.configAggregateDao()
                val botDao = database.botAggregateDao()
                val appPreferenceDao = database.appPreferenceDao()
                val currentConfigs = configDao.listConfigAggregates().map { it.toProfile() }
                if (currentConfigs.size <= 1) return@withTransaction

                val remainingConfigs = currentConfigs.filterNot { it.id == profileId }
                if (remainingConfigs.size == currentConfigs.size) return@withTransaction

                val storedSelectedConfigId = appPreferenceDao.getValue(SELECTED_CONFIG_PROFILE_PREF_KEY)
                val resolvedSelectedConfigId = if (storedSelectedConfigId == profileId) {
                    remainingConfigs.first().id
                } else {
                    remainingConfigs.firstOrNull { it.id == storedSelectedConfigId }?.id ?: remainingConfigs.first().id
                }
                val fallbackConfig = remainingConfigs.first { it.id == resolvedSelectedConfigId }
                val updatedBots = botDao.listBotAggregates()
                    .map { aggregate ->
                        if (aggregate.bot.configProfileId == profileId) {
                            aggregate.copy(
                                bot = aggregate.bot.copy(
                                    configProfileId = fallbackConfig.id,
                                    defaultProviderId = fallbackConfig.defaultChatProviderId,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            ).toWriteModel()
                        } else {
                            aggregate.toWriteModel()
                        }
                    }

                configDao.replaceAll(
                    remainingConfigs.mapIndexed { index, profile ->
                        profile.toWriteModel(sortIndex = index)
                    },
                )
                appPreferenceDao.upsert(
                    AppPreferenceEntity(
                        key = SELECTED_CONFIG_PROFILE_PREF_KEY,
                        value = resolvedSelectedConfigId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                botDao.replaceAll(updatedBots)
            }
        }
    }

    override suspend fun deleteBotProfile(botId: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val botDao = database.botAggregateDao()
                val conversationDao = database.conversationAggregateDao()
                val appPreferenceDao = database.appPreferenceDao()
                val currentBots = botDao.listBotAggregates()
                currentBots.firstOrNull { it.bot.id == botId } ?: return@withTransaction
                ProfileDeletionGuard.requireCanDelete(
                    remainingCount = currentBots.size,
                    kind = ProfileCatalogKind.BOT,
                )

                val updatedBots = currentBots.filterNot { it.bot.id == botId }
                val storedSelectedBotId = appPreferenceDao.getValue(SELECTED_BOT_PREF_KEY)
                val resolvedSelectedBotId = if (storedSelectedBotId == botId) {
                    updatedBots.first().bot.id
                } else {
                    updatedBots.firstOrNull { it.bot.id == storedSelectedBotId }?.bot?.id ?: updatedBots.first().bot.id
                }
                val updatedSessions = conversationDao.listConversationAggregates()
                    .filterNot { it.session.botId == botId }
                    .map { it.toWriteModel() }
                    .ifEmpty {
                        listOf(
                            defaultConversationWriteModel(
                                id = DEFAULT_APP_CHAT_SESSION_ID,
                                title = DEFAULT_APP_CHAT_SESSION_TITLE,
                                botId = resolvedSelectedBotId,
                            ),
                        )
                    }

                botDao.replaceAll(updatedBots.map { it.toWriteModel() })
                appPreferenceDao.upsert(
                    AppPreferenceEntity(
                        key = SELECTED_BOT_PREF_KEY,
                        value = resolvedSelectedBotId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                conversationDao.replaceAll(updatedSessions)
            }
        }
    }

    private fun BotAggregate.toWriteModel(): BotWriteModel {
        return BotWriteModel(
            bot = bot,
            boundQqUins = boundQqUins,
            triggerWords = triggerWords,
        )
    }

    private fun ConversationAggregate.toWriteModel(): ConversationAggregateWriteModel {
        return ConversationAggregateWriteModel(
            session = session,
            messages = messageAggregates.map { aggregate -> aggregate.message },
            attachments = messageAggregates.flatMap { aggregate -> aggregate.attachments },
        )
    }

    private fun defaultConversationWriteModel(
        id: String,
        title: String,
        botId: String,
    ): ConversationAggregateWriteModel {
        return ConversationAggregateWriteModel(
            session = ConversationEntity(
                id = id,
                title = title,
                botId = botId,
                personaId = "",
                providerId = "",
                platformId = "app",
                messageType = "app",
                originSessionId = id,
                maxContextMessages = 12,
                sessionSttEnabled = true,
                sessionTtsEnabled = true,
                pinned = false,
                titleCustomized = false,
                updatedAt = System.currentTimeMillis(),
            ),
            messages = emptyList(),
            attachments = emptyList(),
        )
    }
}
