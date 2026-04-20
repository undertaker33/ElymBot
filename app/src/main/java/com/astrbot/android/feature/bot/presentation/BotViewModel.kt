package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.di.hilt.BotLoginState
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BotViewModel @Inject constructor(
    private val botRepository: BotRepositoryPort,
    private val configRepository: ConfigRepositoryPort,
    private val providerRepository: ProviderRepositoryPort,
    private val personaRepository: PersonaRepositoryPort,
    @BotLoginState private val loginStateFlow: StateFlow<NapCatLoginState>,
    private val phase3DataTransactionService: Phase3DataTransactionService,
) : ViewModel() {
    val botProfiles: StateFlow<List<BotProfile>> = botRepository.bots
    val selectedBotId: StateFlow<String> = botRepository.selectedBotId
    val providers: StateFlow<List<ProviderProfile>> = providerRepository.providers
    val personas: StateFlow<List<PersonaProfile>> = personaRepository.personas
    val configProfiles: StateFlow<List<ConfigProfile>> = configRepository.profiles
    val loginState: StateFlow<NapCatLoginState> = loginStateFlow
    val botProfile: StateFlow<BotProfile> = combine(botProfiles, selectedBotId) { profiles, selectedId ->
        profiles.firstOrNull { it.id == selectedId }
            ?: profiles.firstOrNull()
            ?: botRepository.currentBot()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = botRepository.currentBot(),
    )

    fun select(botId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            botRepository.select(botId)
        }
    }

    fun save(profile: BotProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            botRepository.save(profile)
        }
    }

    fun saveWithConfigModel(profile: BotProfile, defaultChatProviderId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val resolvedConfig = configRepository.resolve(profile.configProfileId)
            configRepository.save(
                resolvedConfig.copy(defaultChatProviderId = defaultChatProviderId),
            )
            botRepository.save(profile.copy(defaultProviderId = defaultChatProviderId))
        }
    }

    fun create() {
        botRepository.create()
    }

    fun deleteSelected(): Result<Unit> {
        deleteSelected {}
        return Result.success(Unit)
    }

    fun deleteSelected(onComplete: (Result<Unit>) -> Unit) {
        val botId = botProfile.value.id
        viewModelScope.launch {
            onComplete(
                runCatching {
                    phase3DataTransactionService.deleteBotProfile(botId)
                },
            )
        }
    }
}
