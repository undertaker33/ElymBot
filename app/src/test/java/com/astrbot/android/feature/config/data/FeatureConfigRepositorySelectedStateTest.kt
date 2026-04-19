package com.astrbot.android.feature.config.data

import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.ConfigAdminUidEntity
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.ConfigKeywordPatternEntity
import com.astrbot.android.data.db.ConfigMcpServerEntity
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.ConfigSkillEntity
import com.astrbot.android.data.db.ConfigTextRuleEntity
import com.astrbot.android.data.db.ConfigWakeWordEntity
import com.astrbot.android.data.db.ConfigWhitelistEntryEntity
import com.astrbot.android.data.db.ConfigWriteModel
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.ConfigProfile
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail

class FeatureConfigRepositorySelectedStateTest {
    private lateinit var originalConfigDao: ConfigAggregateDao
    private lateinit var originalAppPreferenceDao: AppPreferenceDao
    private var originalSyncJob: Job? = null
    private lateinit var originalProfiles: List<ConfigProfile>
    private lateinit var originalSelectedProfileId: String

    @Before
    fun setUp() {
        originalConfigDao = getField("configProfileDao")
        originalAppPreferenceDao = getField("appPreferenceDao")
        originalSyncJob = getField("syncJob")
        originalProfiles = profilesFlow().value
        originalSelectedProfileId = selectedProfileIdFlow().value
        cancelSyncJob()
    }

    @After
    fun tearDown() {
        cancelSyncJob()
        setField("configProfileDao", originalConfigDao)
        setField("appPreferenceDao", originalAppPreferenceDao)
        profilesFlow().value = originalProfiles
        selectedProfileIdFlow().value = originalSelectedProfileId
        if (originalSyncJob != null) {
            invokePrivate("startStateSync")
        } else {
            setField("syncJob", null)
        }
    }

    @Test
    fun select_waits_for_persisted_selected_profile_flow_before_switching_state() {
        val defaultProfile = configProfile(id = FeatureConfigRepository.DEFAULT_CONFIG_ID, name = "Default")
        val customProfile = configProfile(id = "cfg-alt", name = "Alt")
        val configDao = FakeConfigAggregateDao(listOf(defaultProfile, customProfile))
        val appPreferenceDao = FakeAppPreferenceDao(defaultProfile.id)

        bindRepository(configDao, appPreferenceDao, listOf(defaultProfile, customProfile), defaultProfile.id)

        FeatureConfigRepository.select(customProfile.id)

        waitUntil("config selection should be persisted asynchronously") {
            appPreferenceDao.upsertedValues.contains(customProfile.id)
        }
        assertEquals(defaultProfile.id, FeatureConfigRepository.selectedProfileId.value)

        appPreferenceDao.emit(customProfile.id)

        waitUntil("selected profile should switch only after persisted preference flow emits") {
            FeatureConfigRepository.selectedProfileId.value == customProfile.id
        }
    }

    @Test
    fun save_waits_for_persisted_profile_flow_before_exposing_new_profile() {
        val defaultProfile = configProfile(id = FeatureConfigRepository.DEFAULT_CONFIG_ID, name = "Default")
        val customProfile = configProfile(id = "cfg-new", name = "New Profile")
        val configDao = FakeConfigAggregateDao(listOf(defaultProfile))
        val appPreferenceDao = FakeAppPreferenceDao(defaultProfile.id)

        bindRepository(configDao, appPreferenceDao, listOf(defaultProfile), defaultProfile.id)

        FeatureConfigRepository.save(customProfile)

        waitUntil("config save should persist replacement snapshot asynchronously") {
            configDao.latestPersistedProfileIds == listOf(defaultProfile.id, customProfile.id)
        }
        assertEquals(listOf(defaultProfile.id), FeatureConfigRepository.profiles.value.map { it.id })
        assertEquals(defaultProfile.id, FeatureConfigRepository.selectedProfileId.value)

        configDao.emitPersistedProfiles()
        appPreferenceDao.emit(defaultProfile.id)

        waitUntil("profiles should refresh only after persisted DAO flow emits") {
            FeatureConfigRepository.profiles.value.map { it.id } == listOf(defaultProfile.id, customProfile.id)
        }
    }

    @Test
    fun delete_waits_for_persisted_profile_and_selection_flows_before_switching_state() {
        val defaultProfile = configProfile(id = FeatureConfigRepository.DEFAULT_CONFIG_ID, name = "Default")
        val customProfile = configProfile(id = "cfg-alt", name = "Alt")
        val configDao = FakeConfigAggregateDao(listOf(defaultProfile, customProfile))
        val appPreferenceDao = FakeAppPreferenceDao(customProfile.id)

        bindRepository(configDao, appPreferenceDao, listOf(defaultProfile, customProfile), customProfile.id)

        val fallbackId = FeatureConfigRepository.delete(customProfile.id)

        waitUntil("config delete should persist replacement snapshot asynchronously") {
            configDao.latestPersistedProfileIds == listOf(defaultProfile.id) &&
                appPreferenceDao.upsertedValues.contains(defaultProfile.id)
        }
        assertEquals(defaultProfile.id, fallbackId)
        assertEquals(customProfile.id, FeatureConfigRepository.selectedProfileId.value)
        assertEquals(listOf(defaultProfile.id, customProfile.id), FeatureConfigRepository.profiles.value.map { it.id })

        configDao.emitPersistedProfiles()
        appPreferenceDao.emit(defaultProfile.id)

        waitUntil("delete should switch selection only after persisted flows emit") {
            FeatureConfigRepository.selectedProfileId.value == defaultProfile.id &&
                FeatureConfigRepository.profiles.value.map { it.id } == listOf(defaultProfile.id)
        }
    }

    @Test
    fun restore_profiles_waits_for_persisted_flows_before_switching_state() {
        val defaultProfile = configProfile(id = FeatureConfigRepository.DEFAULT_CONFIG_ID, name = "Default")
        val restoredProfile = configProfile(id = "cfg-restore", name = "Restored")
        val configDao = FakeConfigAggregateDao(listOf(defaultProfile))
        val appPreferenceDao = FakeAppPreferenceDao(defaultProfile.id)

        bindRepository(configDao, appPreferenceDao, listOf(defaultProfile), defaultProfile.id)

        FeatureConfigRepository.restoreProfiles(
            profiles = listOf(defaultProfile, restoredProfile),
            selectedProfileId = restoredProfile.id,
        )

        waitUntil("restore should persist replacement snapshot asynchronously") {
            configDao.latestPersistedProfileIds == listOf(defaultProfile.id, restoredProfile.id) &&
                appPreferenceDao.upsertedValues.contains(restoredProfile.id)
        }
        assertEquals(defaultProfile.id, FeatureConfigRepository.selectedProfileId.value)
        assertEquals(listOf(defaultProfile.id), FeatureConfigRepository.profiles.value.map { it.id })

        configDao.emitPersistedProfiles()
        appPreferenceDao.emit(restoredProfile.id)

        waitUntil("restore should switch state only after persisted flows emit") {
            FeatureConfigRepository.selectedProfileId.value == restoredProfile.id &&
                FeatureConfigRepository.profiles.value.map { it.id } == listOf(defaultProfile.id, restoredProfile.id)
        }
    }

    private fun bindRepository(
        configDao: FakeConfigAggregateDao,
        appPreferenceDao: FakeAppPreferenceDao,
        profiles: List<ConfigProfile>,
        selectedProfileId: String,
    ) {
        setField("configProfileDao", configDao)
        setField("appPreferenceDao", appPreferenceDao)
        profilesFlow().value = profiles
        selectedProfileIdFlow().value = selectedProfileId
        invokePrivate("startStateSync")
        waitUntil("initial config state should sync from fake persistence") {
            FeatureConfigRepository.profiles.value.map { it.id } == profiles.map { it.id } &&
                FeatureConfigRepository.selectedProfileId.value == selectedProfileId
        }
    }

    private fun profilesFlow(): MutableStateFlow<List<ConfigProfile>> = getField("_profiles")

    private fun selectedProfileIdFlow(): MutableStateFlow<String> = getField("_selectedProfileId")

    private fun cancelSyncJob() {
        getField<Job?>("syncJob")?.cancel()
        setField("syncJob", null)
    }

    private fun invokePrivate(name: String) {
        val method = FeatureConfigRepository::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(FeatureConfigRepository)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(name: String): T {
        val field = FeatureConfigRepository::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(FeatureConfigRepository) as T
    }

    private fun setField(name: String, value: Any?) {
        val field = FeatureConfigRepository::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(FeatureConfigRepository, value)
    }

    private fun configProfile(id: String, name: String): ConfigProfile {
        return ConfigProfile(
            id = id,
            name = name,
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

private class FakeConfigAggregateDao(initialProfiles: List<ConfigProfile>) : ConfigAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))
    private var persistedAggregates: List<ConfigAggregate> = aggregates.value
    var latestPersistedProfileIds: List<String> = initialProfiles.map { it.id }
        private set

    override fun observeConfigAggregates(): Flow<List<ConfigAggregate>> = aggregates

    override suspend fun listConfigAggregates(): List<ConfigAggregate> = aggregates.value

    override suspend fun replaceAll(writeModels: List<ConfigWriteModel>) {
        persistedAggregates = writeModels.map(::toAggregate)
        latestPersistedProfileIds = persistedAggregates.map { it.config.id }
    }

    override suspend fun upsertConfigs(entities: List<ConfigProfileEntity>) = Unit

    override suspend fun upsertAdminUids(entities: List<ConfigAdminUidEntity>) = Unit

    override suspend fun upsertWakeWords(entities: List<ConfigWakeWordEntity>) = Unit

    override suspend fun upsertWhitelistEntries(entities: List<ConfigWhitelistEntryEntity>) = Unit

    override suspend fun upsertKeywordPatterns(entities: List<ConfigKeywordPatternEntity>) = Unit

    override suspend fun upsertTextRules(entities: List<ConfigTextRuleEntity>) = Unit

    override suspend fun upsertMcpServers(entities: List<ConfigMcpServerEntity>) = Unit

    override suspend fun upsertSkills(entities: List<ConfigSkillEntity>) = Unit

    override suspend fun deleteMissingConfigs(ids: List<String>) = Unit

    override suspend fun clearConfigs() = Unit

    override suspend fun deleteAdminUids(configIds: List<String>) = Unit

    override suspend fun deleteWakeWords(configIds: List<String>) = Unit

    override suspend fun deleteWhitelistEntries(configIds: List<String>) = Unit

    override suspend fun deleteKeywordPatterns(configIds: List<String>) = Unit

    override suspend fun deleteTextRules(configIds: List<String>) = Unit

    override suspend fun deleteMcpServers(configIds: List<String>) = Unit

    override suspend fun deleteSkills(configIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    fun emitPersistedProfiles() {
        aggregates.value = persistedAggregates
    }

    private fun toAggregate(profile: ConfigProfile): ConfigAggregate = toAggregate(profile.toWriteModel(sortIndex = 0))

    private fun toAggregate(writeModel: ConfigWriteModel): ConfigAggregate {
        return ConfigAggregate(
            config = writeModel.config,
            adminUids = writeModel.adminUids,
            wakeWords = writeModel.wakeWords,
            whitelistEntries = writeModel.whitelistEntries,
            keywordPatterns = writeModel.keywordPatterns,
            textRules = listOf(writeModel.textRule),
            mcpServers = writeModel.mcpServers,
            skills = writeModel.skills,
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
