
package com.astrbot.android.core.runtime.llm

import com.astrbot.android.core.runtime.audio.AudioRuntimePort
import com.astrbot.android.di.runtime.audio.toAudioConversationAttachment
import com.astrbot.android.di.runtime.audio.toAudioProviderProfile
import com.astrbot.android.di.runtime.audio.toConversationAttachment
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject

@Deprecated(
    "Phase 9 compat facade retired to an injected instance adapter for the Phase 9 final gate; " +
        "inject AudioRuntimePort for production STT/TTS media calls.",
)
internal class LlmMediaService @Inject constructor(
    private val audioRuntimePort: AudioRuntimePort,
) {
    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        return audioRuntimePort.transcribeAudio(
            provider = provider.toAudioProviderProfile(),
            attachment = attachment.toAudioConversationAttachment(),
        )
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String = "",
        readBracketedContent: Boolean = true,
    ): ConversationAttachment {
        return audioRuntimePort.synthesizeSpeech(
            provider = provider.toAudioProviderProfile(),
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        ).toConversationAttachment()
    }
}
