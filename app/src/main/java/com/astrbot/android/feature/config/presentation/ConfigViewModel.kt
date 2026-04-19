package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.di.ConfigViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.TtsVoiceReferenceAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val dependencies: ConfigViewModelDependencies,
) : ViewModel() {
    val configProfiles: StateFlow<List<ConfigProfile>> = dependencies.configProfiles
    val selectedConfigProfileId: StateFlow<String> = dependencies.selectedConfigProfileId
    val providers: StateFlow<List<ProviderProfile>> = dependencies.providers
    val bots: StateFlow<List<BotProfile>> = dependencies.bots
    val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = dependencies.ttsVoiceAssets

    fun select(profileId: String) {
        dependencies.select(profileId)
    }

    fun save(profile: ConfigProfile) {
        dependencies.save(profile)
    }

    fun create(): ConfigProfile {
        val created = dependencies.create()
        dependencies.select(created.id)
        return created
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            dependencies.deleteConfigProfile(profileId)
        }
    }

    fun resolve(profileId: String): ConfigProfile {
        return dependencies.resolve(profileId)
    }
}
