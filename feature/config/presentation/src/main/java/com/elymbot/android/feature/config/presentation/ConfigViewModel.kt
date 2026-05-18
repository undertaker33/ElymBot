package com.elymbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.core.runtime.llm.LlmConversationAttachment
import com.elymbot.android.core.runtime.llm.LlmFeatureSupportState
import com.elymbot.android.core.runtime.llm.LlmProviderProbePort
import com.elymbot.android.core.runtime.llm.LlmProviderCapability
import com.elymbot.android.core.runtime.llm.LlmProviderProfile
import com.elymbot.android.core.runtime.llm.LlmProviderType
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.Phase3DataTransactionService
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.model.chat.ConversationAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepositoryPort,
    private val providerRepository: ProviderRepositoryPort,
    private val botRepository: BotRepositoryPort,
    @Named("TtsVoiceAssets") private val ttsVoiceAssetsFlow: StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>>,
    private val phase3DataTransactionService: Phase3DataTransactionService,
    private val llmProviderProbePort: LlmProviderProbePort,
) : ViewModel() {
    val configProfiles: StateFlow<List<ConfigProfile>> = configRepository.profiles
    val selectedConfigProfileId: StateFlow<String> = configRepository.selectedProfileId
    val providers: StateFlow<List<ProviderProfile>> = providerRepository.providers
    val bots: StateFlow<List<BotProfile>> = botRepository.bots
    val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = ttsVoiceAssetsFlow

    fun select(profileId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.select(profileId)
        }
    }

    fun save(profile: ConfigProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.save(profile)
            val visibleProfile = waitForVisibleSavedProfile(profile)
            if (visibleProfile != null) {
                syncSelectedBotBinding(visibleProfile)
            }
        }
    }

    fun create(): ConfigProfile {
        val created = configRepository.create()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.select(created.id)
        }
        return created
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            phase3DataTransactionService.deleteConfigProfile(profileId)
        }
    }

    fun resolve(profileId: String): ConfigProfile {
        return configRepository.resolve(profileId)
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return llmProviderProbePort.synthesizeSpeech(
            provider = provider.toLlmProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toConversationAttachment()
    }

    private suspend fun syncSelectedBotBinding(profile: ConfigProfile) {
        if (profile.id.isBlank()) {
            return
        }
        val selectedBot = botRepository.bots.value.firstOrNull { bot ->
            bot.id == botRepository.selectedBotId.value
        } ?: return
        val updated = selectedBot.copy(
            configProfileId = profile.id,
            defaultProviderId = profile.defaultChatProviderId,
        )
        if (updated != selectedBot) {
            botRepository.save(updated)
        }
    }

    private suspend fun waitForVisibleSavedProfile(profile: ConfigProfile): ConfigProfile? {
        if (profile.id.isBlank()) {
            return null
        }
        return withTimeoutOrNull(CONFIG_SAVE_VISIBILITY_TIMEOUT_MS) {
            configRepository.profiles.first { profiles ->
                profiles.any { candidate ->
                    candidate.id == profile.id &&
                        candidate.defaultChatProviderId == profile.defaultChatProviderId
                }
            }.first { candidate -> candidate.id == profile.id }
        }
    }

    private companion object {
        private const val CONFIG_SAVE_VISIBILITY_TIMEOUT_MS = 5_000L
    }
}

private fun ProviderProfile.toLlmProviderProfile(): LlmProviderProfile {
    return LlmProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = runCatching { LlmProviderType.valueOf(providerType.name) }.getOrDefault(LlmProviderType.CUSTOM),
        apiKey = apiKey,
        capabilities = capabilities.map { LlmProviderCapability.valueOf(it.name) }.toSet(),
        enabled = enabled,
        multimodalRuleSupport = LlmFeatureSupportState.valueOf(multimodalRuleSupport.name),
        multimodalProbeSupport = LlmFeatureSupportState.valueOf(multimodalProbeSupport.name),
        nativeStreamingRuleSupport = LlmFeatureSupportState.valueOf(nativeStreamingRuleSupport.name),
        nativeStreamingProbeSupport = LlmFeatureSupportState.valueOf(nativeStreamingProbeSupport.name),
        sttProbeSupport = LlmFeatureSupportState.valueOf(sttProbeSupport.name),
        ttsProbeSupport = LlmFeatureSupportState.valueOf(ttsProbeSupport.name),
        ttsVoiceOptions = ttsVoiceOptions,
    )
}

private fun LlmConversationAttachment.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = id,
        type = type,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = base64Data,
        remoteUrl = remoteUrl,
    )
}

