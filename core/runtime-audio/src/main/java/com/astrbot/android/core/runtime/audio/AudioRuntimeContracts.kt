package com.astrbot.android.core.runtime.audio

data class AudioProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: AudioProviderType,
    val apiKey: String,
    val capabilities: Set<AudioProviderCapability>,
    val enabled: Boolean = true,
    val sttProbeSupport: AudioFeatureSupportState = AudioFeatureSupportState.UNKNOWN,
    val ttsProbeSupport: AudioFeatureSupportState = AudioFeatureSupportState.UNKNOWN,
    val ttsVoiceOptions: List<String> = emptyList(),
)

enum class AudioProviderType {
    WHISPER_API,
    XINFERENCE_STT,
    BAILIAN_STT,
    SHERPA_ONNX_STT,
    OPENAI_TTS,
    BAILIAN_TTS,
    MINIMAX_TTS,
    SHERPA_ONNX_TTS,
    CUSTOM,
}

enum class AudioProviderCapability {
    STT,
    TTS,
}

enum class AudioFeatureSupportState {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

data class AudioConversationAttachment(
    val id: String,
    val type: String = "audio",
    val mimeType: String = "audio/wav",
    val fileName: String = "",
    val base64Data: String = "",
    val remoteUrl: String = "",
)

data class AudioSttProbeResult(
    val state: AudioFeatureSupportState,
    val transcript: String,
)

interface SpeechToTextPort {
    fun transcribeAudio(
        provider: AudioProviderProfile,
        attachment: AudioConversationAttachment,
    ): String

    fun probeSttSupport(provider: AudioProviderProfile): AudioSttProbeResult
}

interface TextToSpeechPort {
    fun synthesizeSpeech(
        provider: AudioProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): AudioConversationAttachment

    fun probeTtsSupport(provider: AudioProviderProfile): AudioFeatureSupportState
}

interface AudioRuntimePort : SpeechToTextPort, TextToSpeechPort {
    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean
}

data class AudioAssetSubState(
    val installed: Boolean,
    val details: String,
)

data class AudioTtsAssetState(
    val framework: AudioAssetSubState,
    val kokoro: AudioAssetSubState,
)
