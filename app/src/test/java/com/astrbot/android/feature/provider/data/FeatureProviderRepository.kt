package com.astrbot.android.feature.provider.data

import com.astrbot.android.feature.provider.domain.model.FeatureSupportState
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import com.astrbot.android.feature.provider.domain.model.ProviderType
import kotlinx.coroutines.flow.StateFlow

object FeatureProviderRepository {
    @Volatile
    private var delegate: FeatureProviderRepositoryStore? = null

    internal fun installDelegate(store: FeatureProviderRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureProviderRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureProviderRepository test facade was accessed before FeatureProviderRepositoryStore was installed."
        }
    }

    val providers: StateFlow<List<ProviderProfile>>
        get() = repository().providers

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean,
        multimodalRuleSupport: FeatureSupportState,
        multimodalProbeSupport: FeatureSupportState,
        nativeStreamingRuleSupport: FeatureSupportState,
        nativeStreamingProbeSupport: FeatureSupportState,
        sttProbeSupport: FeatureSupportState,
        ttsProbeSupport: FeatureSupportState,
        ttsVoiceOptions: List<String>,
    ): ProviderProfile = repository().save(
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

    fun toggleEnabled(id: String) = repository().toggleEnabled(id)

    fun delete(id: String) = repository().delete(id)

    fun snapshotProfiles(): List<ProviderProfile> = repository().snapshotProfiles()

    fun restoreProfiles(profiles: List<ProviderProfile>) = repository().restoreProfiles(profiles)

    fun updateMultimodalProbeSupport(id: String, probeSupport: FeatureSupportState) =
        repository().updateMultimodalProbeSupport(id, probeSupport)

    fun updateNativeStreamingProbeSupport(id: String, probeSupport: FeatureSupportState) =
        repository().updateNativeStreamingProbeSupport(id, probeSupport)

    fun updateSttProbeSupport(id: String, probeSupport: FeatureSupportState) =
        repository().updateSttProbeSupport(id, probeSupport)

    fun updateTtsProbeSupport(id: String, probeSupport: FeatureSupportState) =
        repository().updateTtsProbeSupport(id, probeSupport)
}
