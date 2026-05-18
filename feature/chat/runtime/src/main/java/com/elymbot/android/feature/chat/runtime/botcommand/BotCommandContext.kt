package com.elymbot.android.feature.chat.runtime.botcommand

import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageType

enum class BotCommandSource {
    APP_CHAT,
    QQ,
}

data class BotCommandContext(
    val source: BotCommandSource,
    val languageTag: String,
    val strings: BotCommandStringResolver = BotCommandStringResolver.fallback,
    val sessionId: String,
    val session: ConversationSession,
    val sessions: List<ConversationSession>,
    val bot: BotProfile,
    val availableBots: List<BotProfile> = emptyList(),
    val config: ConfigProfile,
    val activeProviderId: String,
    val availableProviders: List<ProviderProfile> = emptyList(),
    val currentPersona: PersonaProfile?,
    val availablePersonas: List<PersonaProfile>,
    val messageType: MessageType,
    val sourceUid: String = "",
    val sourceGroupId: String = "",
    val selfId: String = "",
    val createSession: (() -> ConversationSession)? = null,
    val deleteSession: ((String) -> Unit)? = null,
    val renameSession: ((String, String) -> Unit)? = null,
    val selectSession: ((String) -> Unit)? = null,
    val updateConfig: (ConfigProfile) -> Unit = {},
    val updateBot: (BotProfile) -> Unit = {},
    val updateProvider: (ProviderProfile) -> Unit = {},
    val updateSessionServiceFlags: (Boolean?, Boolean?) -> Unit = { _, _ -> },
    val replaceMessages: (List<ConversationMessage>) -> Unit = {},
    val updateSessionBindings: (String, String, String) -> Unit = { _, _, _ -> },
)

