
package com.astrbot.android.core.runtime.llm

import com.astrbot.android.core.runtime.audio.AudioRuntimePort
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment

@Deprecated(
    "Phase 9 compat facade: inject AudioRuntimePort for production STT/TTS media calls. " +
        "This facade must be retired before the Phase 9 final gate.",
)
object LlmMediaService {
    // Explicit compat facade only. New production code must inject AudioRuntimePort instead.
    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        return ChatCompletionService.transcribeAudio(provider, attachment)
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String = "",
        readBracketedContent: Boolean = true,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(
            provider = provider,
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        )
    }
}
