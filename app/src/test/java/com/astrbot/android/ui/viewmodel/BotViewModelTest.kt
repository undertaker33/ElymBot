package com.astrbot.android.ui.viewmodel

import com.astrbot.android.di.BotViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class BotViewModelTest {
    @Test
    fun save_with_config_model_updates_config_then_bot() {
        val deps = FakeBotDependencies()
        val viewModel = BotViewModel(deps)
        val bot = deps.botProfile.value.copy(configProfileId = "config-a")

        viewModel.saveWithConfigModel(bot, defaultChatProviderId = "provider-2")

        assertEquals("config-a", requireNotNull(deps.savedConfig).id)
        assertEquals("provider-2", requireNotNull(deps.savedConfig).defaultChatProviderId)
        assertEquals("provider-2", requireNotNull(deps.savedBot).defaultProviderId)
    }

    @Test
    fun delete_selected_uses_current_bot_id() {
        val deps = FakeBotDependencies(
            selectedBot = BotProfile(id = "bot-2", displayName = "Bot 2"),
        )
        val viewModel = BotViewModel(deps)

        viewModel.deleteSelected()

        assertEquals(listOf("bot-2"), deps.deletedBotIds)
    }

    private class FakeBotDependencies(
        selectedBot: BotProfile = BotProfile(id = "bot-1", displayName = "Bot 1", configProfileId = "config-a"),
    ) : BotViewModelDependencies {
        override val botProfile: StateFlow<BotProfile> = MutableStateFlow(selectedBot)
        override val botProfiles: StateFlow<List<BotProfile>> = MutableStateFlow(listOf(selectedBot))
        override val selectedBotId: StateFlow<String> = MutableStateFlow(selectedBot.id)
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())
        override val configProfiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(
            listOf(ConfigProfile(id = "config-a", defaultChatProviderId = "provider-1")),
        )
        override val loginState: StateFlow<NapCatLoginState> = MutableStateFlow(NapCatLoginState())

        var savedBot: BotProfile? = null
        var savedConfig: ConfigProfile? = null
        val deletedBotIds = mutableListOf<String>()

        override fun select(botId: String) = Unit

        override fun save(profile: BotProfile) {
            savedBot = profile
        }

        override fun saveConfig(profile: ConfigProfile) {
            savedConfig = profile
        }

        override fun create() = Unit

        override fun delete(botId: String) {
            deletedBotIds += botId
        }

        override fun resolveConfig(profileId: String): ConfigProfile {
            return configProfiles.value.first { it.id == profileId }
        }
    }
}
