package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.BotDao
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.model.BotProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BotRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private val defaultBot = BotProfile(
        id = "qq-main",
        displayName = "Primary Bot",
        tag = "Default",
        accountHint = "QQ account not linked",
        triggerWords = listOf("astrbot", "assistant"),
    )

    private val _botProfiles = MutableStateFlow(listOf(defaultBot))
    private val _selectedBotId = MutableStateFlow(defaultBot.id)
    private val _botProfile = MutableStateFlow(defaultBot)

    private var botDao: BotDao = AstrBotDatabaseHolder.placeholder

    val botProfile: StateFlow<BotProfile> = _botProfile.asStateFlow()
    val botProfiles: StateFlow<List<BotProfile>> = _botProfiles.asStateFlow()
    val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        botDao = AstrBotDatabase.get(context).botDao()
        repositoryScope.launch {
            if (botDao.count() == 0) {
                botDao.upsert(defaultBot.toEntity())
            }
            botDao.observeBots().collect { entities ->
                val profiles = entities.map { it.toProfile() }
                if (profiles.isEmpty()) {
                    _botProfiles.value = listOf(defaultBot)
                    _selectedBotId.value = defaultBot.id
                    _botProfile.value = defaultBot
                    return@collect
                }

                _botProfiles.value = profiles
                val currentSelected = profiles.firstOrNull { it.id == _selectedBotId.value } ?: profiles.first()
                _selectedBotId.value = currentSelected.id
                _botProfile.value = currentSelected
                RuntimeLogRepository.append("Bot database synced: count=${profiles.size} selected=${currentSelected.id}")
            }
        }
    }

    fun select(botId: String) {
        val selected = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        _selectedBotId.value = selected.id
        _botProfile.value = selected
        RuntimeLogRepository.append("Bot selected: ${selected.displayName} (${selected.id})")
    }

    fun save(profile: BotProfile) {
        repositoryScope.launch {
            botDao.upsert(profile.toEntity())
            RuntimeLogRepository.append(
                "Bot saved: ${profile.displayName}, provider=${profile.defaultProviderId.ifBlank { "none" }}, persona=${profile.defaultPersonaId.ifBlank { "none" }}",
            )
            select(profile.id)
        }
    }

    fun create(name: String = "New Bot"): BotProfile {
        val created = BotProfile(
            id = "bot-${UUID.randomUUID()}",
            displayName = name,
        )
        repositoryScope.launch {
            botDao.upsert(created.toEntity())
            RuntimeLogRepository.append("Bot created: ${created.displayName} (${created.id})")
            select(created.id)
        }
        return created
    }

    fun delete(botId: String) {
        if (_botProfiles.value.size == 1) {
            RuntimeLogRepository.append("Bot delete skipped: last bot cannot be removed")
            return
        }

        val removed = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        repositoryScope.launch {
            botDao.deleteById(botId)
            RuntimeLogRepository.append("Bot deleted: ${removed.displayName} (${removed.id})")
        }
    }
}

private object AstrBotDatabaseHolder {
    val placeholder = object : BotDao {
        override fun observeBots() = flowOf(emptyList<BotEntity>())
        override suspend fun upsert(entity: BotEntity) = Unit
        override suspend fun deleteById(botId: String) = Unit
        override suspend fun count(): Int = 0
    }
}
