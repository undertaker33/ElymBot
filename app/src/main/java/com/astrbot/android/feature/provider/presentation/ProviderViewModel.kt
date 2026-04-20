package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.provider.runtime.ProviderRuntimePort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providerRepository: ProviderRepositoryPort,
    private val configRepository: ConfigRepositoryPort,
    private val providerRuntime: ProviderRuntimePort,
) : ViewModel() {
    data class SttProbeResult(
        val state: FeatureSupportState,
        val transcript: String,
    )

    val providers: StateFlow<List<ProviderProfile>> = providerRepository.providers
    val configProfiles: StateFlow<List<ConfigProfile>> = configRepository.profiles
    val selectedConfigProfileId: StateFlow<String> = configRepository.selectedProfileId

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean = true,
        multimodalRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        multimodalProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        nativeStreamingRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        nativeStreamingProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        sttProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        ttsProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        ttsVoiceOptions: List<String> = emptyList(),
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            providerRepository.save(
                ProviderProfile(
                    id = id ?: "",
                    name = name,
                    baseUrl = baseUrl,
                    model = model,
                    providerType = providerType,
                    apiKey = apiKey,
                    capabilities = capabilities,
                    enabled = enabled,
                    multimodalRuleSupport = multimodalRuleSupport,
                    multimodalProbeSupport = multimodalProbeSupport,
                    nativeStreamingRuleSupport = nativeStreamingRuleSupport,
                    nativeStreamingProbeSupport = nativeStreamingProbeSupport,
                    sttProbeSupport = sttProbeSupport,
                    ttsProbeSupport = ttsProbeSupport,
                    ttsVoiceOptions = ttsVoiceOptions,
                ),
            )
        }
    }

    fun toggleEnabled(id: String) {
        providerRepository.toggleEnabled(id)
    }

    fun delete(id: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            providerRepository.delete(id)
        }
    }

    fun updateMultimodalProbeSupport(id: String, probeSupport: FeatureSupportState) {
        providerRepository.updateMultimodalProbeSupport(id, probeSupport)
    }

    fun updateNativeStreamingProbeSupport(id: String, probeSupport: FeatureSupportState) {
        providerRepository.updateNativeStreamingProbeSupport(id, probeSupport)
    }

    fun updateSttProbeSupport(id: String, probeSupport: FeatureSupportState) {
        providerRepository.updateSttProbeSupport(id, probeSupport)
    }

    fun updateTtsProbeSupport(id: String, probeSupport: FeatureSupportState) {
        providerRepository.updateTtsProbeSupport(id, probeSupport)
    }

    fun saveConfig(profile: ConfigProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.save(profile)
        }
    }

    suspend fun fetchModels(provider: ProviderProfile): List<String> {
        return withContext(Dispatchers.IO) {
            providerRuntime.fetchModels(provider)
        }
    }

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return providerRuntime.detectMultimodalRule(provider)
    }

    suspend fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            providerRuntime.probeMultimodalSupport(provider)
        }
    }

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return providerRuntime.detectNativeStreamingRule(provider)
    }

    suspend fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            providerRuntime.probeNativeStreamingSupport(provider)
        }
    }

    suspend fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
        val result = withContext(Dispatchers.IO) {
            providerRuntime.probeSttSupport(provider)
        }
        return SttProbeResult(
            state = result.state,
            transcript = result.transcript,
        )
    }

    suspend fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            providerRuntime.probeTtsSupport(provider)
        }
    }

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return providerRuntime.listVoiceChoicesFor(provider)
    }

    fun ttsAssetState(context: android.content.Context): com.astrbot.android.core.runtime.audio.SherpaOnnxAssetManager.TtsAssetState {
        return providerRuntime.ttsAssetState(context)
    }

    fun isSherpaFrameworkReady(): Boolean {
        return providerRuntime.isSherpaFrameworkReady()
    }

    fun isSherpaSttReady(): Boolean {
        return providerRuntime.isSherpaSttReady()
    }

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return withContext(Dispatchers.IO) {
            providerRuntime.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
        }
    }
}
