package com.astrbot.android.feature.bot.data

import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.BotAggregate
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.BotBoundQqUinEntity
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.BotTriggerWordEntity
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.model.BotProfile
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail

class FeatureBotRepositorySelectedStateTest {
    private lateinit var originalBotDao: BotAggregateDao
    private lateinit var originalAppPreferenceDao: AppPreferenceDao
    private var originalSyncJob: Job? = null
    private lateinit var originalProfiles: List<BotProfile>
    private lateinit var originalSelectedBotId: String
    private lateinit var originalBotProfile: BotProfile

    @Before
    fun setUp() {
        originalBotDao = getField("botDao")
        originalAppPreferenceDao = getField("appPreferenceDao")
        originalSyncJob = getField("syncJob")
        originalProfiles = botProfilesFlow().value
        originalSelectedBotId = selectedBotIdFlow().value
        originalBotProfile = botProfileFlow().value
        cancelSyncJob()
    }

    @After
    fun tearDown() {
        cancelSyncJob()
        setField("botDao", originalBotDao)
        setField("appPreferenceDao", originalAppPreferenceDao)
        botProfilesFlow().value = originalProfiles
        selectedBotIdFlow().value = originalSelectedBotId
        botProfileFlow().value = originalBotProfile
        if (originalSyncJob != null) {
            invokePrivate("startStateSync")
        } else {
            setField("syncJob", null)
        }
    }

    @Test
    fun select_waits_for_persisted_selected_bot_flow_before_switching_state() {
        val alpha = botProfile(id = "bot-alpha", displayName = "Alpha")
        val beta = botProfile(id = "bot-beta", displayName = "Beta")
        val botDao = FakeBotAggregateDao(listOf(alpha, beta))
        val appPreferenceDao = FakeAppPreferenceDao(alpha.id)

        setField("botDao", botDao)
        setField("appPreferenceDao", appPreferenceDao)
        botProfilesFlow().value = listOf(alpha, beta)
        selectedBotIdFlow().value = alpha.id
        botProfileFlow().value = alpha
        invokePrivate("startStateSync")
        waitUntil("initial bot state should sync from fake persistence") {
            FeatureBotRepository.selectedBotId.value == alpha.id &&
                FeatureBotRepository.botProfile.value.id == alpha.id
        }

        FeatureBotRepository.select(beta.id)

        waitUntil("bot selection should be persisted asynchronously") {
            appPreferenceDao.upsertedValues.contains(beta.id)
        }
        assertEquals(alpha.id, FeatureBotRepository.selectedBotId.value)
        assertEquals(alpha.id, FeatureBotRepository.botProfile.value.id)

        appPreferenceDao.emit(beta.id)

        waitUntil("selected bot should switch only after persisted preference flow emits") {
            FeatureBotRepository.selectedBotId.value == beta.id &&
                FeatureBotRepository.botProfile.value.id == beta.id
        }
    }

    private fun botProfilesFlow(): MutableStateFlow<List<BotProfile>> = getField("_botProfiles")

    private fun selectedBotIdFlow(): MutableStateFlow<String> = getField("_selectedBotId")

    private fun botProfileFlow(): MutableStateFlow<BotProfile> = getField("_botProfile")

    private fun cancelSyncJob() {
        getField<Job?>("syncJob")?.cancel()
        setField("syncJob", null)
    }

    private fun invokePrivate(name: String) {
        val method = FeatureBotRepository::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(FeatureBotRepository)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(name: String): T {
        val field = FeatureBotRepository::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(FeatureBotRepository) as T
    }

    private fun setField(name: String, value: Any?) {
        val field = FeatureBotRepository::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(FeatureBotRepository, value)
    }

    private fun botProfile(id: String, displayName: String): BotProfile {
        return BotProfile(
            id = id,
            displayName = displayName,
            tag = displayName,
            accountHint = "QQ account not linked",
            triggerWords = listOf("astrbot"),
            configProfileId = FeatureConfigRepository.DEFAULT_CONFIG_ID,
        )
    }

    private fun waitUntil(message: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        fail(message)
    }
}

private class FakeBotAggregateDao(initialProfiles: List<BotProfile>) : BotAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeBotAggregates(): Flow<List<BotAggregate>> = aggregates

    override suspend fun listBotAggregates(): List<BotAggregate> = aggregates.value

    override suspend fun upsertBots(entities: List<BotEntity>) = Unit

    override suspend fun upsertBoundQqUins(entities: List<BotBoundQqUinEntity>) = Unit

    override suspend fun upsertTriggerWords(entities: List<BotTriggerWordEntity>) = Unit

    override suspend fun deleteMissingBots(ids: List<String>) = Unit

    override suspend fun clearBots() = Unit

    override suspend fun deleteBoundQqUins(botIds: List<String>) = Unit

    override suspend fun deleteTriggerWords(botIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    private fun toAggregate(profile: BotProfile): BotAggregate {
        val writeModel = profile.toWriteModel()
        return BotAggregate(
            bot = writeModel.bot,
            boundQqUins = writeModel.boundQqUins,
            triggerWords = writeModel.triggerWords,
        )
    }
}

private class FakeAppPreferenceDao(initialValue: String?) : AppPreferenceDao {
    private val values = MutableStateFlow(initialValue)
    val upsertedValues = CopyOnWriteArrayList<String?>()

    override fun observeValue(key: String): Flow<String?> = values

    override suspend fun getValue(key: String): String? = values.value

    override suspend fun upsert(entity: AppPreferenceEntity) {
        upsertedValues += entity.value
    }

    fun emit(value: String?) {
        values.value = value
    }
}
