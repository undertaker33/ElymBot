package com.astrbot.android.data

import com.astrbot.android.core.db.backup.AppBackupDataRegistry
import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.AppBackupExternalState
import com.astrbot.android.core.db.backup.AppBackupAppState
import com.astrbot.android.core.db.backup.AppBackupImportMode
import com.astrbot.android.core.db.backup.AppBackupJson
import com.astrbot.android.core.db.backup.AppBackupManifest
import com.astrbot.android.core.db.backup.AppBackupModuleSnapshot
import com.astrbot.android.core.db.backup.AppBackupModules
import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.di.createProductionAppBackupDataPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppBackupRepositoryCompatibilityTest {
    private lateinit var botSnapshot: List<BotProfile>
    private lateinit var providerSnapshot: List<ProviderProfile>
    private lateinit var personaSnapshot: List<PersonaProfile>
    private lateinit var configSnapshot: List<ConfigProfile>
    private lateinit var conversationSnapshot: List<ConversationSession>
    private lateinit var selectedBotId: String
    private lateinit var selectedConfigId: String
    private lateinit var originalConversationDao: ConversationAggregateDao

    @Before
    fun captureSnapshot() {
        botSnapshot = BotRepository.snapshotProfiles()
        providerSnapshot = ProviderRepository.snapshotProfiles()
        personaSnapshot = PersonaRepository.snapshotProfiles()
        configSnapshot = ConfigRepository.snapshotProfiles()
        conversationSnapshot = ConversationRepository.snapshotSessions()
        selectedBotId = BotRepository.selectedBotId.value
        selectedConfigId = ConfigRepository.selectedProfileId.value
        originalConversationDao = conversationAggregateDao()
    }

    /**
     * Task10 Phase3 – Task E: wire AppBackupDataRegistry.port to real singleton repositories so
     * that restore calls actually reach the in-memory state (the registry defaults to a no-op
     * MissingAppBackupDataPort in unit tests).
     */
    @Before
    fun wireDataPort() {
        AppBackupDataRegistry.port = createProductionAppBackupDataPort()
    }

    @After
    fun restoreSnapshot() {
        runBlocking {
            setConversationDao(originalConversationDao)
            ProviderRepository.restoreProfiles(providerSnapshot)
            PersonaRepository.restoreProfiles(personaSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigId)
            BotRepository.restoreProfiles(botSnapshot, selectedBotId)
            ConversationRepository.restoreSessions(conversationSnapshot)
        }
        AppBackupDataRegistry.port = createProductionAppBackupDataPort()
    }

    @Test
    fun importLegacyManifest_restoresAggregateBackedRepositories() = runBlocking {
        val manifest = AppBackupJson.parseManifest(legacyManifestJson())

        val result = AppBackupRepository.importBackup(
            manifest = manifest,
            mode = AppBackupImportMode.REPLACE_ALL,
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("provider-legacy"), ProviderRepository.snapshotProfiles().map { it.id })
        assertEquals(listOf("persona-legacy"), PersonaRepository.snapshotProfiles().map { it.id })
        assertEquals(listOf("config-legacy"), ConfigRepository.snapshotProfiles().map { it.id })
        val restoredBot = BotRepository.snapshotProfiles().single()
        assertEquals("bot-legacy", restoredBot.id)
        assertEquals(listOf("10001", "10002"), restoredBot.boundQqUins)
        assertEquals(listOf("astrbot", "assistant"), restoredBot.triggerWords)
        val restoredSession = ConversationRepository.snapshotSessions().single()
        assertEquals("session-legacy", restoredSession.id)
        assertEquals("qq", restoredSession.platformId)
        assertEquals(MessageType.GroupMessage, restoredSession.messageType)
        assertEquals("group:20001:user:10001", restoredSession.originSessionId)
        assertEquals("hello from backup", restoredSession.messages.single().content)
        assertEquals("bot-legacy", BotRepository.selectedBotId.value)
        assertEquals("config-legacy", ConfigRepository.selectedProfileId.value)
    }

    @Test
    fun importLegacyManifest_fails_when_production_wiring_hits_durable_conversation_persistence_error() = runBlocking {
        setConversationDao(FailingConversationAggregateDao())
        val manifest = AppBackupJson.parseManifest(legacyManifestJson())

        val result = AppBackupRepository.importBackup(
            manifest = manifest,
            mode = AppBackupImportMode.REPLACE_ALL,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Conversation state must roll back when durable restore fails in production wiring",
            conversationSnapshot.map { it.id },
            ConversationRepository.snapshotSessions().map { it.id },
        )
    }

    @Test
    fun importBackup_rolls_back_previously_restored_modules_when_later_stage_fails() = runBlocking {
        val rollbackProbe = RollbackProbeAppBackupDataPort(
            providers = listOf(provider(id = "provider-old")),
            personas = listOf(persona(id = "persona-old", providerId = "provider-old")),
            configs = listOf(ConfigProfile(id = "config-old", defaultChatProviderId = "provider-old")),
            bots = listOf(BotProfile(id = "bot-old", displayName = "Old Bot", configProfileId = "config-old")),
            conversations = listOf(testSession(id = "session-old", botId = "bot-old")),
            selectedBotId = "bot-old",
            selectedConfigId = "config-old",
            quickLoginUin = "10001",
            savedAccounts = listOf(SavedQqAccount(uin = "10001", nickName = "Old Account")),
            failAt = RestoreStageFailure.QQ_ACCOUNTS,
        )
        AppBackupDataRegistry.port = rollbackProbe
        val manifest = AppBackupManifest(
            createdAt = 1L,
            trigger = "rollback-test",
            modules = AppBackupModules(
                providers = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(providerJson(provider(id = "provider-new"))),
                ),
                personas = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(personaJson(persona(id = "persona-new", providerId = "provider-new"))),
                ),
                configs = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(configJson(ConfigProfile(id = "config-new", defaultChatProviderId = "provider-new"))),
                ),
                bots = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(botJson(BotProfile(id = "bot-new", displayName = "New Bot", configProfileId = "config-new"))),
                ),
                conversations = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(testSession(id = "session-new", botId = "bot-new")),
                ),
                qqLogin = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(
                        qqLoginJson(
                            quickLoginUin = "20002",
                            savedAccounts = listOf(SavedQqAccount(uin = "20002", nickName = "New Account")),
                        ),
                    ),
                ),
            ),
            appState = AppBackupAppState(
                selectedBotId = "bot-new",
                selectedConfigId = "config-new",
            ),
        )

        val result = AppBackupRepository.importBackup(
            manifest = manifest,
            mode = AppBackupImportMode.REPLACE_ALL,
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("provider-old"), rollbackProbe.snapshotProviders().map { it.id })
        assertEquals(listOf("persona-old"), rollbackProbe.snapshotPersonas().map { it.id })
        assertEquals(listOf("config-old"), rollbackProbe.snapshotConfigs().map { it.id })
        assertEquals(listOf("bot-old"), rollbackProbe.snapshotBots().map { it.id })
        assertEquals(listOf("session-old"), rollbackProbe.snapshotConversations().map { it.id })
        assertEquals(
            AppBackupExternalState(
                selectedBotId = "bot-old",
                selectedConfigId = "config-old",
                quickLoginUin = "10001",
                savedAccounts = listOf(SavedQqAccount(uin = "10001", nickName = "Old Account")),
            ),
            rollbackProbe.snapshotExternalState(),
        )
    }

    private fun conversationAggregateDao(): ConversationAggregateDao {
        val field = ConversationRepository::class.java.getDeclaredField("conversationAggregateDao")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(ConversationRepository) as ConversationAggregateDao
    }

    private fun setConversationDao(dao: ConversationAggregateDao) {
        val field = ConversationRepository::class.java.getDeclaredField("conversationAggregateDao")
        field.isAccessible = true
        field.set(ConversationRepository, dao)
    }

    private fun provider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com/$id",
            model = "gpt-4.1-mini",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "secret",
            capabilities = setOf(ProviderCapability.CHAT),
        )
    }

    private fun persona(id: String, providerId: String): PersonaProfile {
        return PersonaProfile(
            id = id,
            name = id,
            systemPrompt = "prompt-$id",
            enabledTools = emptySet(),
            defaultProviderId = providerId,
        )
    }

    private fun testSession(id: String, botId: String): ConversationSession {
        return ConversationSession(
            id = id,
            title = id,
            botId = botId,
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = false,
            titleCustomized = false,
            messages = emptyList(),
        )
    }

    private fun providerJson(profile: ProviderProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("baseUrl", profile.baseUrl)
            .put("model", profile.model)
            .put("providerType", profile.providerType.name)
            .put("apiKey", profile.apiKey)
            .put("enabled", profile.enabled)
            .put("multimodalRuleSupport", profile.multimodalRuleSupport.name)
            .put("multimodalProbeSupport", profile.multimodalProbeSupport.name)
            .put("nativeStreamingRuleSupport", profile.nativeStreamingRuleSupport.name)
            .put("nativeStreamingProbeSupport", profile.nativeStreamingProbeSupport.name)
            .put("sttProbeSupport", profile.sttProbeSupport.name)
            .put("ttsProbeSupport", profile.ttsProbeSupport.name)
            .put("capabilities", JSONArray(profile.capabilities.map { it.name }))
            .put("ttsVoiceOptions", JSONArray(profile.ttsVoiceOptions))
    }

    private fun personaJson(profile: PersonaProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("tag", profile.tag)
            .put("systemPrompt", profile.systemPrompt)
            .put("enabledTools", JSONArray(profile.enabledTools.toList()))
            .put("defaultProviderId", profile.defaultProviderId)
            .put("maxContextMessages", profile.maxContextMessages)
            .put("enabled", profile.enabled)
    }

    private fun configJson(profile: ConfigProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("defaultChatProviderId", profile.defaultChatProviderId)
            .put("defaultVisionProviderId", profile.defaultVisionProviderId)
            .put("defaultSttProviderId", profile.defaultSttProviderId)
            .put("defaultTtsProviderId", profile.defaultTtsProviderId)
            .put("sttEnabled", profile.sttEnabled)
            .put("ttsEnabled", profile.ttsEnabled)
            .put("alwaysTtsEnabled", profile.alwaysTtsEnabled)
            .put("ttsReadBracketedContent", profile.ttsReadBracketedContent)
            .put("textStreamingEnabled", profile.textStreamingEnabled)
            .put("voiceStreamingEnabled", profile.voiceStreamingEnabled)
            .put("streamingMessageIntervalMs", profile.streamingMessageIntervalMs)
            .put("realWorldTimeAwarenessEnabled", profile.realWorldTimeAwarenessEnabled)
            .put("imageCaptionTextEnabled", profile.imageCaptionTextEnabled)
            .put("webSearchEnabled", profile.webSearchEnabled)
            .put("proactiveEnabled", profile.proactiveEnabled)
            .put("ttsVoiceId", profile.ttsVoiceId)
            .put("imageCaptionPrompt", profile.imageCaptionPrompt)
            .put("adminUids", JSONArray(profile.adminUids))
            .put("sessionIsolationEnabled", profile.sessionIsolationEnabled)
            .put("wakeWords", JSONArray(profile.wakeWords))
            .put("wakeWordsAdminOnlyEnabled", profile.wakeWordsAdminOnlyEnabled)
            .put("privateChatRequiresWakeWord", profile.privateChatRequiresWakeWord)
            .put("replyTextPrefix", profile.replyTextPrefix)
            .put("quoteSenderMessageEnabled", profile.quoteSenderMessageEnabled)
            .put("mentionSenderEnabled", profile.mentionSenderEnabled)
            .put("replyOnAtOnlyEnabled", profile.replyOnAtOnlyEnabled)
            .put("whitelistEnabled", profile.whitelistEnabled)
            .put("whitelistEntries", JSONArray(profile.whitelistEntries))
            .put("logOnWhitelistMiss", profile.logOnWhitelistMiss)
            .put("adminGroupBypassWhitelistEnabled", profile.adminGroupBypassWhitelistEnabled)
            .put("adminPrivateBypassWhitelistEnabled", profile.adminPrivateBypassWhitelistEnabled)
            .put("ignoreSelfMessageEnabled", profile.ignoreSelfMessageEnabled)
            .put("ignoreAtAllEventEnabled", profile.ignoreAtAllEventEnabled)
            .put("replyWhenPermissionDenied", profile.replyWhenPermissionDenied)
            .put("rateLimitWindowSeconds", profile.rateLimitWindowSeconds)
            .put("rateLimitMaxCount", profile.rateLimitMaxCount)
            .put("rateLimitStrategy", profile.rateLimitStrategy)
            .put("keywordDetectionEnabled", profile.keywordDetectionEnabled)
            .put("keywordPatterns", JSONArray(profile.keywordPatterns))
    }

    private fun botJson(profile: BotProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("platformName", profile.platformName)
            .put("displayName", profile.displayName)
            .put("tag", profile.tag)
            .put("accountHint", profile.accountHint)
            .put("boundQqUins", JSONArray(profile.boundQqUins))
            .put("triggerWords", JSONArray(profile.triggerWords))
            .put("autoReplyEnabled", profile.autoReplyEnabled)
            .put("persistConversationLocally", profile.persistConversationLocally)
            .put("bridgeMode", profile.bridgeMode)
            .put("bridgeEndpoint", profile.bridgeEndpoint)
            .put("defaultProviderId", profile.defaultProviderId)
            .put("defaultPersonaId", profile.defaultPersonaId)
            .put("configProfileId", profile.configProfileId)
            .put("status", profile.status)
    }

    private fun qqLoginJson(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    ): JSONObject {
        return JSONObject()
            .put("quickLoginUin", quickLoginUin)
            .put(
                "savedAccounts",
                JSONArray().apply {
                    savedAccounts.forEach { account ->
                        put(
                            JSONObject()
                                .put("uin", account.uin)
                                .put("nickName", account.nickName)
                                .put("avatarUrl", account.avatarUrl),
                        )
                    }
                },
            )
    }

    private fun legacyManifestJson(): JSONObject {
        return JSONObject(
            """
            {
              "schema": "astrbot-android-full-backup-v1",
              "createdAt": 1710000000000,
              "trigger": "legacy-import-test",
              "modules": {
                "bots": {
                  "count": 1,
                  "records": [
                    {
                      "id": "bot-legacy",
                      "platformName": "QQ",
                      "displayName": "Legacy Bot",
                      "tag": "Imported",
                      "accountHint": "10001",
                      "boundQqUins": ["10001", "10002"],
                      "triggerWords": ["astrbot", "assistant"],
                      "autoReplyEnabled": true,
                      "persistConversationLocally": true,
                      "bridgeMode": "NapCat local bridge",
                      "bridgeEndpoint": "ws://127.0.0.1:6199/ws",
                      "defaultProviderId": "provider-legacy",
                      "defaultPersonaId": "persona-legacy",
                      "configProfileId": "config-legacy",
                      "status": "Idle"
                    }
                  ]
                },
                "providers": {
                  "count": 1,
                  "records": [
                    {
                      "id": "provider-legacy",
                      "name": "Legacy Provider",
                      "baseUrl": "https://example.com/v1",
                      "model": "gpt-4.1-mini",
                      "providerType": "OPENAI_COMPATIBLE",
                      "apiKey": "secret",
                      "enabled": true,
                      "multimodalRuleSupport": "SUPPORTED",
                      "multimodalProbeSupport": "UNKNOWN",
                      "nativeStreamingRuleSupport": "SUPPORTED",
                      "nativeStreamingProbeSupport": "UNKNOWN",
                      "sttProbeSupport": "UNKNOWN",
                      "ttsProbeSupport": "UNKNOWN",
                      "capabilities": ["CHAT", "TTS"],
                      "ttsVoiceOptions": ["alloy", "verse"]
                    }
                  ]
                },
                "personas": {
                  "count": 1,
                  "records": [
                    {
                      "id": "persona-legacy",
                      "name": "Legacy Persona",
                      "tag": "Legacy",
                      "systemPrompt": "Keep answers concise.",
                      "enabledTools": ["search_web", "qq_send"],
                      "defaultProviderId": "provider-legacy",
                      "maxContextMessages": 16,
                      "enabled": true
                    }
                  ]
                },
                "configs": {
                  "count": 1,
                  "records": [
                    {
                      "id": "config-legacy",
                      "name": "Legacy Config",
                      "defaultChatProviderId": "provider-legacy",
                      "defaultVisionProviderId": "",
                      "defaultSttProviderId": "",
                      "defaultTtsProviderId": "provider-legacy",
                      "sttEnabled": false,
                      "ttsEnabled": true,
                      "alwaysTtsEnabled": false,
                      "ttsReadBracketedContent": true,
                      "textStreamingEnabled": true,
                      "voiceStreamingEnabled": false,
                      "streamingMessageIntervalMs": 120,
                      "realWorldTimeAwarenessEnabled": false,
                      "imageCaptionTextEnabled": true,
                      "webSearchEnabled": true,
                      "proactiveEnabled": false,
                      "ttsVoiceId": "alloy",
                      "imageCaptionPrompt": "Describe images naturally.",
                      "adminUids": ["10001"],
                      "sessionIsolationEnabled": true,
                      "wakeWords": ["astrbot"],
                      "wakeWordsAdminOnlyEnabled": false,
                      "privateChatRequiresWakeWord": false,
                      "replyTextPrefix": "[bot]",
                      "quoteSenderMessageEnabled": true,
                      "mentionSenderEnabled": true,
                      "replyOnAtOnlyEnabled": false,
                      "whitelistEnabled": true,
                      "whitelistEntries": ["group:20001"],
                      "logOnWhitelistMiss": true,
                      "adminGroupBypassWhitelistEnabled": true,
                      "adminPrivateBypassWhitelistEnabled": true,
                      "ignoreSelfMessageEnabled": true,
                      "ignoreAtAllEventEnabled": true,
                      "replyWhenPermissionDenied": true,
                      "rateLimitWindowSeconds": 30,
                      "rateLimitMaxCount": 5,
                      "rateLimitStrategy": "drop",
                      "keywordDetectionEnabled": true,
                      "keywordPatterns": ["ping", "status"]
                    }
                  ]
                },
                "conversations": {
                  "count": 1,
                  "records": [
                    {
                      "id": "session-legacy",
                      "title": "Legacy Session",
                      "botId": "bot-legacy",
                      "personaId": "persona-legacy",
                      "providerId": "provider-legacy",
                      "platformId": "qq",
                      "messageType": "group",
                      "originSessionId": "group:20001:user:10001",
                      "maxContextMessages": 16,
                      "sessionSttEnabled": false,
                      "sessionTtsEnabled": true,
                      "pinned": true,
                      "titleCustomized": true,
                      "messages": [
                        {
                          "id": "message-legacy",
                          "role": "user",
                          "content": "hello from backup",
                          "timestamp": 1710000000123,
                          "attachments": []
                        }
                      ]
                    }
                  ]
                },
                "qqLogin": {
                  "count": 0,
                  "records": []
                },
                "ttsAssets": {
                  "count": 0,
                  "records": []
                }
              },
              "appState": {
                "selectedBotId": "bot-legacy",
                "selectedConfigId": "config-legacy"
              }
            }
            """.trimIndent(),
        )
    }
}

private class FailingConversationAggregateDao : ConversationAggregateDao() {
    override fun observeConversationAggregates() = kotlinx.coroutines.flow.flowOf(emptyList<ConversationAggregate>())

    override suspend fun listConversationAggregates(): List<ConversationAggregate> = emptyList()

    override suspend fun upsertSessions(entities: List<ConversationEntity>) = Unit

    override suspend fun upsertMessages(entities: List<ConversationMessageEntity>) = Unit

    override suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>) = Unit

    override suspend fun deleteMissingSessions(ids: List<String>) = Unit

    override suspend fun clearSessions() = Unit

    override suspend fun deleteMessagesForSessions(sessionIds: List<String>) = Unit

    override suspend fun count(): Int = 0

    override suspend fun replaceAll(writeModels: List<com.astrbot.android.data.db.ConversationAggregateWriteModel>) {
        error("forced durable conversation failure")
    }
}

private enum class RestoreStageFailure {
    BOTS,
    PROVIDERS,
    PERSONAS,
    CONFIGS,
    CONVERSATIONS,
    QQ_ACCOUNTS,
}

private class RollbackProbeAppBackupDataPort(
    providers: List<ProviderProfile>,
    personas: List<PersonaProfile>,
    configs: List<ConfigProfile>,
    bots: List<BotProfile>,
    conversations: List<ConversationSession>,
    selectedBotId: String,
    selectedConfigId: String,
    quickLoginUin: String,
    savedAccounts: List<SavedQqAccount>,
    private val failAt: RestoreStageFailure,
) : AppBackupDataPort {
    private var providersState: List<ProviderProfile> = providers
    private var personasState: List<PersonaProfile> = personas
    private var configsState: List<ConfigProfile> = configs
    private var botsState: List<BotProfile> = bots
    private var conversationsState: List<ConversationSession> = conversations
    private var externalState = AppBackupExternalState(
        selectedBotId = selectedBotId,
        selectedConfigId = selectedConfigId,
        quickLoginUin = quickLoginUin,
        savedAccounts = savedAccounts,
    )

    override fun snapshotBots(): List<BotProfile> = botsState

    override fun snapshotProviders(): List<ProviderProfile> = providersState

    override fun snapshotPersonas(): List<PersonaProfile> = personasState

    override fun snapshotConfigs(): List<ConfigProfile> = configsState

    override fun snapshotConversations(): List<ConversationSession> = conversationsState

    override fun snapshotExternalState(): AppBackupExternalState = externalState

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) {
        maybeThrow(RestoreStageFailure.BOTS)
        botsState = profiles
        externalState = externalState.copy(selectedBotId = selectedBotId)
    }

    override fun restoreProviders(profiles: List<ProviderProfile>) {
        maybeThrow(RestoreStageFailure.PROVIDERS)
        providersState = profiles
    }

    override fun restorePersonas(profiles: List<PersonaProfile>) {
        maybeThrow(RestoreStageFailure.PERSONAS)
        personasState = profiles
    }

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) {
        maybeThrow(RestoreStageFailure.CONFIGS)
        configsState = profiles
        externalState = externalState.copy(selectedConfigId = selectedConfigId)
    }

    override suspend fun restoreConversations(sessions: List<ConversationSession>) {
        maybeThrow(RestoreStageFailure.CONVERSATIONS)
        conversationsState = sessions
    }

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>) {
        maybeThrow(RestoreStageFailure.QQ_ACCOUNTS)
        externalState = externalState.copy(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }

    private fun maybeThrow(stage: RestoreStageFailure) {
        if (stage == failAt) {
            error("forced restore failure at $stage")
        }
    }
}
