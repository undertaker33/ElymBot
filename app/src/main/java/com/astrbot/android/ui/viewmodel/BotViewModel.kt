package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

class BotViewModel : ViewModel() {
    val botProfile: StateFlow<BotProfile> = BotRepository.botProfile
    val botProfiles: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas
    val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    fun select(botId: String) {
        BotRepository.select(botId)
    }

    fun save(profile: BotProfile) {
        BotRepository.save(profile)
    }

    fun saveWithConfigModel(profile: BotProfile, defaultChatProviderId: String) {
        val resolvedConfig = ConfigRepository.resolve(profile.configProfileId)
        ConfigRepository.save(
            resolvedConfig.copy(defaultChatProviderId = defaultChatProviderId),
        )
        BotRepository.save(profile.copy(defaultProviderId = defaultChatProviderId))
    }

    fun create() {
        BotRepository.create()
    }

    fun deleteSelected() {
        BotRepository.delete(botProfile.value.id)
    }
}
