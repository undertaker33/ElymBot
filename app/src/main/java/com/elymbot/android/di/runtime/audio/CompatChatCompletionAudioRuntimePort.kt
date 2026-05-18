package com.elymbot.android.di.runtime.audio

import android.content.Context
import com.elymbot.android.core.runtime.audio.AudioConversationAttachment
import com.elymbot.android.core.runtime.audio.AudioFeatureSupportState
import com.elymbot.android.core.runtime.audio.AudioProviderProfile
import com.elymbot.android.core.runtime.audio.AudioRuntimePort
import com.elymbot.android.core.runtime.audio.AudioSttProbeResult
import com.elymbot.android.core.runtime.audio.SherpaOnnxBridge
import com.elymbot.android.core.runtime.llm.ChatCompletionService
import com.elymbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Phase 9-B compat adapter.
 *
 * Cloud STT/TTS implementation still lives inside ChatCompletionService until the provider-specific
 * audio clients are split. Production callers should inject AudioRuntimePort; direct calls to the
 * ChatCompletionService media methods are blocked by source contracts.
 */
internal class CompatChatCompletionAudioRuntimePort @Inject constructor(
    @ApplicationContext appContext: Context,
    private val chatCompletionService: ChatCompletionService,
    private val sherpaOnnxBridge: SherpaOnnxBridge,
    private val ttsVoiceAssetPort: TtsVoiceAssetPort,
) : AudioRuntimePort {
    private val appContext = appContext.applicationContext

    override fun transcribeAudio(
        provider: AudioProviderProfile,
        attachment: AudioConversationAttachment,
    ): String {
        return chatCompletionService.transcribeAudio(
            provider = provider.toProviderProfile(),
            attachment = attachment.toConversationAttachment(),
        )
    }

    override fun probeSttSupport(provider: AudioProviderProfile): AudioSttProbeResult {
        val result = chatCompletionService.probeSttSupport(
            provider = provider.toProviderProfile(),
            context = appContext,
        )
        return AudioSttProbeResult(
            state = AudioFeatureSupportState.valueOf(result.state.name),
            transcript = result.transcript,
        )
    }

    override fun synthesizeSpeech(
        provider: AudioProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): AudioConversationAttachment {
        return chatCompletionService.synthesizeSpeech(
            provider = provider.toProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
            voiceChoicesProvider = { profile -> ttsVoiceAssetPort.listVoiceChoicesFor(profile.id) },
        ).toAudioConversationAttachment()
    }

    override fun probeTtsSupport(provider: AudioProviderProfile): AudioFeatureSupportState {
        val result = chatCompletionService.probeTtsSupport(provider.toProviderProfile())
        return AudioFeatureSupportState.valueOf(result.name)
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return sherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return sherpaOnnxBridge.isSttReady()
    }
}
