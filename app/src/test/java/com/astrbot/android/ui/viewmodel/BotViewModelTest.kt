package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BotViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun save_with_config_model_updates_config_then_bot() {
        val botPort = FakeBotPort()
        val configPort = FakeConfigPort()
        val viewModel = BotViewModel(
            botRepository = botPort,
            configRepository = configPort,
            providerRepository = FakeProviderPort(),
            personaRepository = FakePersonaPort(),
            loginStateFlow = MutableStateFlow(NapCatLoginState()),
            phase3DataTransactionService = FakePhase3DataTransactionService(),
        )
        val bot = botPort.currentBot().copy(configProfileId = "config-a")

        viewModel.saveWithConfigModel(bot, defaultChatProviderId = "provider-2")

        assertEquals("config-a", requireNotNull(configPort.savedProfile).id)
        assertEquals("provider-2", requireNotNull(configPort.savedProfile).defaultChatProviderId)
        assertEquals("provider-2", requireNotNull(botPort.savedProfile).defaultProviderId)
    }

    @Test
    fun delete_selected_uses_current_bot_id() = runTest(dispatcher) {
        val botPort = FakeBotPort(
            selectedBot = BotProfile(id = "bot-2", displayName = "Bot 2"),
        )
        val deleteService = FakePhase3DataTransactionService()
        val viewModel = BotViewModel(
            botRepository = botPort,
            configRepository = FakeConfigPort(),
            providerRepository = FakeProviderPort(),
            personaRepository = FakePersonaPort(),
            loginStateFlow = MutableStateFlow(NapCatLoginState()),
            phase3DataTransactionService = deleteService,
        )

        viewModel.deleteSelected()

        assertEquals(emptyList<String>(), deleteService.deletedBotIds)
        advanceUntilIdle()
        assertEquals(listOf("bot-2"), deleteService.deletedBotIds)
    }

    private class FakeBotPort(
        selectedBot: BotProfile = BotProfile(id = "bot-1", displayName = "Bot 1", configProfileId = "config-a"),
    ) : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(listOf(selectedBot))
        override val selectedBotId: StateFlow<String> = MutableStateFlow(selectedBot.id)

        var savedProfile: BotProfile? = null

        override fun currentBot(): BotProfile = bots.value.first()

        override fun snapshotProfiles(): List<BotProfile> = bots.value

        override fun create(name: String): BotProfile {
            return BotProfile(id = "created-bot", displayName = name)
        }

        override suspend fun save(profile: BotProfile) {
            savedProfile = profile
        }

        override suspend fun create(profile: BotProfile) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun select(id: String) = Unit
    }

    private class FakeConfigPort : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(
            listOf(ConfigProfile(id = "config-a", defaultChatProviderId = "provider-1")),
        )
        override val selectedProfileId: StateFlow<String> = MutableStateFlow("config-a")

        var savedProfile: ConfigProfile? = null

        override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

        override fun create(name: String): ConfigProfile = ConfigProfile(id = "created-config", name = name)

        override fun resolve(id: String): ConfigProfile {
            return profiles.value.first { it.id == id }
        }

        override fun resolveExistingId(id: String?): String = id ?: selectedProfileId.value

        override suspend fun save(profile: ConfigProfile) {
            savedProfile = profile
        }

        override suspend fun delete(id: String) = Unit

        override suspend fun select(id: String) = Unit
    }

    private class FakeProviderPort : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())

        override fun snapshotProfiles(): List<ProviderProfile> = providers.value

        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> = emptyList()

        override fun toggleEnabled(id: String) = Unit

        override fun updateMultimodalProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) = Unit

        override fun updateNativeStreamingProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) = Unit

        override fun updateSttProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) = Unit

        override fun updateTtsProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) = Unit

        override suspend fun save(profile: ProviderProfile) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private class FakePersonaPort : PersonaRepositoryPort {
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())

        override fun snapshotProfiles(): List<PersonaProfile> = emptyList()

        override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> = emptyList()

        override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? = null

        override suspend fun add(profile: PersonaProfile) = Unit

        override suspend fun update(profile: PersonaProfile) = Unit

        override suspend fun toggleEnabled(id: String, enabled: Boolean) = Unit

        override suspend fun toggleEnabled(id: String) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private class FakePhase3DataTransactionService : Phase3DataTransactionService {
        val deletedBotIds = mutableListOf<String>()

        override suspend fun deleteConfigProfile(profileId: String) = Unit

        override suspend fun deleteBotProfile(botId: String) {
            deletedBotIds += botId
        }
    }
}
