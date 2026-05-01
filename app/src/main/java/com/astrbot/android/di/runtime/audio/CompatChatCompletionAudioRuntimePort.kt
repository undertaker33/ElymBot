package com.astrbot.android.di.runtime.audio

import android.content.Context
import com.astrbot.android.core.runtime.audio.AudioConversationAttachment
import com.astrbot.android.core.runtime.audio.AudioFeatureSupportState
import com.astrbot.android.core.runtime.audio.AudioProviderProfile
import com.astrbot.android.core.runtime.audio.AudioRuntimePort
import com.astrbot.android.core.runtime.audio.AudioSttProbeResult
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.llm.ChatCompletionService
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
) : AudioRuntimePort {
    private val appContext = appContext.applicationContext

    init {
        ChatCompletionService.initialize(this.appContext)
    }

    override fun transcribeAudio(
        provider: AudioProviderProfile,
        attachment: AudioConversationAttachment,
    ): String {
        return ChatCompletionService.transcribeAudio(
            provider = provider.toProviderProfile(),
            attachment = attachment.toConversationAttachment(),
        )
    }

    override fun probeSttSupport(provider: AudioProviderProfile): AudioSttProbeResult {
        val result = ChatCompletionService.probeSttSupport(
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
        return ChatCompletionService.synthesizeSpeech(
            provider = provider.toProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toAudioConversationAttachment()
    }

    override fun probeTtsSupport(provider: AudioProviderProfile): AudioFeatureSupportState {
        val result = ChatCompletionService.probeTtsSupport(provider.toProviderProfile())
        return AudioFeatureSupportState.valueOf(result.name)
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return SherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return SherpaOnnxBridge.isSttReady()
    }
}
