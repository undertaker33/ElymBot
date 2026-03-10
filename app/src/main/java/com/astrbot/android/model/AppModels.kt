package com.astrbot.android.model

enum class ProviderCapability {
    CHAT,
    TTS,
    ASR,
}

enum class ProviderType {
    OPENAI_COMPATIBLE,
    DEEPSEEK,
    GEMINI,
    ANTHROPIC,
    CUSTOM,
}

data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: ProviderType,
    val apiKey: String,
    val capabilities: Set<ProviderCapability>,
    val enabled: Boolean = true,
)

data class PersonaProfile(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val enabledTools: Set<String>,
    val defaultProviderId: String = "",
    val maxContextMessages: Int = 12,
    val enabled: Boolean = true,
)

data class BotProfile(
    val id: String = "qq-main",
    val platformName: String = "QQ",
    val displayName: String = "Host Bot",
    val accountHint: String = "",
    val triggerWords: List<String> = listOf("astrbot"),
    val autoReplyEnabled: Boolean = true,
    val bridgeMode: String = "NapCat local bridge",
    val bridgeEndpoint: String = "ws://127.0.0.1:6199/ws",
    val defaultProviderId: String = "",
    val defaultPersonaId: String = "default",
    val status: String = "Idle",
)

data class ConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
)

data class ConversationSession(
    val id: String,
    val title: String,
    val personaId: String,
    val providerId: String,
    val maxContextMessages: Int,
    val messages: List<ConversationMessage>,
)

data class BotRuntimeState(
    val qqEnabled: Boolean = true,
    val napCatContainerEnabled: Boolean = true,
    val bridgeStatus: String = "Stopped",
    val botStatus: String = "Idle",
)

data class NapCatBridgeConfig(
    val runtimeMode: String = "local_container",
    val endpoint: String = "ws://127.0.0.1:6199/ws",
    val healthUrl: String = "http://127.0.0.1:6099",
    val autoStart: Boolean = false,
    val startCommand: String = "",
    val stopCommand: String = "",
    val statusCommand: String = "",
    val commandPreview: String = "Start NapCat runtime",
)

data class NapCatRuntimeState(
    val status: String = "Stopped",
    val lastAction: String = "",
    val lastCheckAt: Long = 0L,
    val pidHint: String = "",
    val details: String = "Bridge is not connected",
    val progressLabel: String = "",
    val progressPercent: Int = 0,
    val progressIndeterminate: Boolean = false,
    val installerCached: Boolean = false,
)

data class NapCatLoginState(
    val bridgeReady: Boolean = false,
    val authenticated: Boolean = false,
    val isLogin: Boolean = false,
    val isOffline: Boolean = false,
    val qrCodeUrl: String = "",
    val quickLoginUin: String = "",
    val loginError: String = "",
    val needCaptcha: Boolean = false,
    val captchaUrl: String = "",
    val needNewDevice: Boolean = false,
    val newDeviceJumpUrl: String = "",
    val newDeviceSig: String = "",
    val uin: String = "",
    val nick: String = "",
    val avatarUrl: String = "",
    val statusText: String = "Waiting for NapCat bridge",
    val lastUpdated: Long = 0L,
)
