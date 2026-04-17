package com.astrbot.android.runtime.context

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.ResourceCenterRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.model.usesOpenAiStyleChatApi
import com.astrbot.android.runtime.plugin.toolsource.ToolSourceContext
import java.util.UUID

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
        val contextWindow = resolveContextWindow(config)
        val messageWindow = session.messages.takeLast(contextWindow)

        val personaToolSnapshot = persona?.let { resolvedPersona ->
            PersonaToolEnablementSnapshot(
                personaId = resolvedPersona.id,
                enabled = resolvedPersona.enabled,
                enabledTools = resolvedPersona.enabledTools
                    .toMutableSet()
                    .apply { addAll(hostCapabilityToolsForConfig(config)) }
                    .toSet(),
            )
        }

        val requestId = UUID.randomUUID().toString()
        val resourceProjection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
            snapshot = ResourceCenterRepository.compatibilitySnapshotForConfig(config),
            platform = event.platform,
            trigger = event.trigger,
        )
        val promptSkills = resourceProjection.promptSkills
        val toolSkills = resourceProjection.toolSkills
        val mcpServers = resourceProjection.mcpServers
        val toolSourceContext = ToolSourceContext.fromConfigProfile(
            config = config,
            requestId = requestId,
            platform = event.platform,
            conversationId = event.conversationId,
            mcpServers = mcpServers,
            promptSkills = promptSkills,
            toolSkills = toolSkills,
        )

        return ResolvedRuntimeContext(
            requestId = requestId,
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
            mcpServers = mcpServers,
            skills = config.skills,
            promptSkills = promptSkills,
            toolSkills = toolSkills,
            toolSourceContext = toolSourceContext,
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

    /**
     * Context window is derived solely from config — persona no longer
     * participates in context strategy (Phase 1 boundary).
     */
    private fun resolveContextWindow(
        config: ConfigProfile,
    ): Int {
        val configMax = config.maxContextTurns
        return if (configMax <= 0) Int.MAX_VALUE else configMax
    }

    private fun hostCapabilityToolsForConfig(config: ConfigProfile): Set<String> {
        return buildSet {
            if (config.webSearchEnabled) {
                add("web_search")
            }
            if (config.proactiveEnabled) {
                add("create_future_task")
                add("delete_future_task")
                add("list_future_tasks")
            }
        }
    }
}
