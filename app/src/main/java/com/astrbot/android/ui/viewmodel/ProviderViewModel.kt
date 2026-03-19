package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import kotlinx.coroutines.flow.StateFlow

class ProviderViewModel : ViewModel() {
    val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers

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
        ProviderRepository.save(
            id = id,
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
        )
    }

    fun toggleEnabled(id: String) {
        ProviderRepository.toggleEnabled(id)
    }

    fun delete(id: String) {
        ProviderRepository.delete(id)
    }

    fun updateMultimodalProbeSupport(id: String, probeSupport: FeatureSupportState) {
        ProviderRepository.updateMultimodalProbeSupport(id, probeSupport)
    }

    fun updateNativeStreamingProbeSupport(id: String, probeSupport: FeatureSupportState) {
        ProviderRepository.updateNativeStreamingProbeSupport(id, probeSupport)
    }

    fun updateSttProbeSupport(id: String, probeSupport: FeatureSupportState) {
        ProviderRepository.updateSttProbeSupport(id, probeSupport)
    }

    fun updateTtsProbeSupport(id: String, probeSupport: FeatureSupportState) {
        ProviderRepository.updateTtsProbeSupport(id, probeSupport)
    }
}
