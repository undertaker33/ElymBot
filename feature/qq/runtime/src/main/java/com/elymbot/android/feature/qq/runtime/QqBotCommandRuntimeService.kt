package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.chat.runtime.botcommand.BotCommandContext
import com.elymbot.android.feature.chat.runtime.botcommand.BotCommandRouter
import com.elymbot.android.feature.chat.runtime.botcommand.BotCommandSource
import com.elymbot.android.feature.chat.runtime.botcommand.BotCommandStringResolver
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.qq.domain.IncomingQqMessage
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqReplyPayload
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.model.chat.ConversationSession
import kotlinx.coroutines.runBlocking

internal class QqBotCommandRuntimeService(
    private val botPort: BotRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val replySender: QqReplySender,
    private val profileResolver: QqRuntimeProfileResolver,
    private val currentLanguageTag: () -> String,
    private val strings: BotCommandStringResolver = BotCommandStringResolver.fallback,
    private val log: (String) -> Unit = {},
) {
    fun handle(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = message.text.trim()
        if (
            !QqSlashCommandPermissionPolicy.canTrigger(
                adminOnlyEnabled = config.pluginCommandsAdminOnlyEnabled,
                isAdmin = message.senderId in config.adminUids,
            )
        ) {
            log("QQ bot command blocked by admin-only permission: command=${trimmedText.substringBefore(' ')} user=${message.senderId}")
            if (config.replyWhenPermissionDenied) {
                replySender.send(
                    QqReplyPayload(
                        conversationId = message.conversationId,
                        messageType = message.messageType,
                        text = QqSlashCommandPermissionPolicy.ADMIN_ONLY_NOTICE,
                    ),
                    message,
                )
            }
            return true
        }
        val result = BotCommandRouter.handle(
            input = trimmedText,
            context = BotCommandContext(
                source = BotCommandSource.QQ,
                languageTag = currentLanguageTag(),
                strings = strings,
                sessionId = sessionId,
                session = session,
                sessions = conversationPort.sessions(),
                bot = bot,
                availableBots = profileResolver.availableBots(),
                config = config,
                activeProviderId = profileResolver.resolveProvider(bot, session.providerId)?.id ?: session.providerId,
                availableProviders = profileResolver.availableChatProviders(),
                currentPersona = currentPersona,
                availablePersonas = profileResolver.availablePersonas(),
                messageType = message.messageType,
                sourceUid = message.senderId,
                sourceGroupId = message.groupIdOrBlank,
                selfId = message.selfId,
                deleteSession = { targetSessionId ->
                    conversationPort.deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    conversationPort.renameSession(targetSessionId, title)
                },
                updateConfig = { updatedConfig ->
                    runBlocking { configPort.save(updatedConfig) }
                },
                updateBot = { updatedBot ->
                    runBlocking { botPort.save(updatedBot) }
                },
                updateProvider = { updatedProvider ->
                    runBlocking { providerPort.save(updatedProvider) }
                },
                updateSessionServiceFlags = { sttEnabled, ttsEnabled ->
                    conversationPort.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    conversationPort.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    conversationPort.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = personaId,
                        botId = botId,
                    )
                },
            ),
        )
        if (!result.handled) {
            return false
        }
        result.replyText?.let { reply ->
            replySender.send(
                QqReplyPayload(
                    conversationId = message.conversationId,
                    messageType = message.messageType,
                    text = reply,
                ),
                message,
            )
        }
        log("Bot command handled via router: ${trimmedText.substringBefore(' ')} session=$sessionId")
        return result.stopModelDispatch
    }
}
