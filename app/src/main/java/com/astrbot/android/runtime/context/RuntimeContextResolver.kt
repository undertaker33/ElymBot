package com.astrbot.android.runtime.context

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.model.usesOpenAiStyleChatApi

/**
 * Resolves a [RuntimeIngressEvent] into a complete [ResolvedRuntimeContext] by
 * looking up bot, config, persona, provider, conversation, and policy snapshots.
 *
 * This is the single authoritative place where runtime context is assembled.
 * Neither ChatViewModel nor OneBotBridgeServer should duplicate this logic.
 */
object RuntimeContextResolver {

    fun resolve(
        event: RuntimeIngressEvent,
        bot: BotProfile,
        overrideProviderId: String? = null,
        overridePersonaId: String? = null,
    ): ResolvedRuntimeContext {
        val config = ConfigRepository.resolve(bot.configProfileId)

        val chatProviders = ProviderRepository.providers.value
            .filter { it.enabled && ProviderCapability.CHAT in it.capabilities }

        val effectiveProviderId = overrideProviderId
            ?: bot.defaultProviderId
        val provider = chatProviders.firstOrNull { it.id == effectiveProviderId }
            ?: chatProviders.firstOrNull()
            ?: error("No enabled chat provider available")

        val effectivePersonaId = overridePersonaId
            ?: bot.defaultPersonaId
        val persona = effectivePersonaId.takeIf { it.isNotBlank() }?.let { pid ->
            PersonaRepository.personas.value.firstOrNull { it.id == pid && it.enabled }
        }

        val effectiveSessionId = event.repositorySessionId.takeIf { it.isNotBlank() }
            ?: event.conversationId
        val session = ConversationRepository.session(effectiveSessionId)
        val contextWindow = resolveContextWindow(config, persona)
        val messageWindow = session.messages.takeLast(contextWindow)

        val personaToolSnapshot = persona?.let {
            PersonaToolEnablementSnapshot(
                personaId = it.id,
                enabled = it.enabled,
                enabledTools = it.enabledTools,
            )
        }

        return ResolvedRuntimeContext(
            ingressEvent = event,
            bot = bot,
            config = config,
            persona = persona,
            provider = provider,
            availableProviders = chatProviders,
            conversationId = event.conversationId,
            messageWindow = messageWindow,
            contextPolicy = ContextPolicy(
                strategy = config.contextLimitStrategy,
                maxTurns = config.maxContextTurns,
                dequeueTurns = config.dequeueContextTurns,
                compressInstruction = config.llmCompressInstruction,
                compressKeepRecent = config.llmCompressKeepRecent,
                compressProviderId = config.llmCompressProviderId,
            ),
            personaToolSnapshot = personaToolSnapshot,
            providerCapabilities = ProviderCapabilitySnapshot(
                supportsToolCalling = provider.providerType.usesOpenAiStyleChatApi(),
                supportsStreaming = provider.hasNativeStreamingSupport(),
                supportsMultimodal = provider.multimodalRuleSupport == FeatureSupportState.SUPPORTED
                    || provider.multimodalProbeSupport == FeatureSupportState.SUPPORTED,
            ),
            webSearchEnabled = config.webSearchEnabled,
            proactiveEnabled = config.proactiveEnabled,
            mcpServers = config.mcpServers,
            skills = config.skills,
            deliveryPolicy = DeliveryPolicy(
                platform = event.platform,
                streamingEnabled = config.textStreamingEnabled,
                quoteSenderMessage = config.quoteSenderMessageEnabled,
                mentionSender = config.mentionSenderEnabled,
                replyTextPrefix = config.replyTextPrefix,
                ttsEnabled = config.ttsEnabled,
                alwaysTts = config.alwaysTtsEnabled,
            ),
            realWorldTimeAwarenessEnabled = config.realWorldTimeAwarenessEnabled,
        )
    }

    private fun resolveContextWindow(
        config: ConfigProfile,
        persona: PersonaProfile?,
    ): Int {
        val configMax = config.maxContextTurns
        val personaMax = persona?.maxContextMessages ?: Int.MAX_VALUE
        return when {
            configMax <= 0 -> personaMax
            else -> minOf(configMax, personaMax)
        }
    }
}
