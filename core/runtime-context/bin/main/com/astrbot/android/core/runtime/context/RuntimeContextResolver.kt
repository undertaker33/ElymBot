package com.astrbot.android.core.runtime.context

import java.util.UUID
import javax.inject.Inject

interface RuntimeContextResolverPort {
    fun resolve(
        event: RuntimeIngressEvent,
        bot: RuntimeBotSnapshot,
        overrideProviderId: String? = null,
        overridePersonaId: String? = null,
    ): ResolvedRuntimeContext
}

class DefaultRuntimeContextResolverPort @Inject constructor(
    private val dataPort: RuntimeContextDataPort,
) : RuntimeContextResolverPort {
    override fun resolve(
        event: RuntimeIngressEvent,
        bot: RuntimeBotSnapshot,
        overrideProviderId: String?,
        overridePersonaId: String?,
    ): ResolvedRuntimeContext {
        return RuntimeContextResolver.resolve(
            event = event,
            bot = bot,
            dataPort = dataPort,
            overrideProviderId = overrideProviderId,
            overridePersonaId = overridePersonaId,
        )
    }
}

object RuntimeContextResolver {

    fun resolve(
        event: RuntimeIngressEvent,
        bot: RuntimeBotSnapshot,
        dataPort: RuntimeContextDataPort,
        overrideProviderId: String? = null,
        overridePersonaId: String? = null,
    ): ResolvedRuntimeContext {
        val config = dataPort.resolveConfig(bot.configProfileId)

        val chatProviders = dataPort.listProviders()
            .filter { it.enabled && it.chatCapable }

        val effectiveProviderId = config.defaultChatProviderId
            .ifBlank { overrideProviderId.orEmpty() }
            .ifBlank { bot.defaultProviderId }
        val provider = chatProviders.firstOrNull { it.id == effectiveProviderId }
            ?: chatProviders.firstOrNull()
            ?: error("No enabled chat provider available")

        val effectivePersonaId = overridePersonaId
            ?: bot.defaultPersonaId
        val persona = effectivePersonaId.takeIf { it.isNotBlank() }?.let { pid ->
            dataPort.findEnabledPersona(pid)
        }

        val effectiveSessionId = event.repositorySessionId.takeIf { it.isNotBlank() }
            ?: event.conversationId
        val session = dataPort.session(effectiveSessionId)
        val contextWindow = resolveContextWindow(config)
        val messageWindow = if (event.trigger == IngressTrigger.SCHEDULED_TASK) {
            emptyList()
        } else {
            session.messages.takeLast(contextWindow)
        }
        val scheduledTaskContextWindow = if (
            event.trigger == IngressTrigger.SCHEDULED_TASK &&
            config.includeScheduledTaskConversationContext
        ) {
            session.messages
                .filter { message ->
                    message.role == "user" || message.role == "assistant"
                }
                .filterNot { message -> message.content.isBlank() }
                .takeLast(contextWindow.coerceAtLeast(0))
                .toList()
        } else {
            emptyList()
        }

        val personaToolSnapshot = persona?.let { resolvedPersona ->
            RuntimePersonaToolEnablementSnapshot(
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
            snapshot = dataPort.compatibilitySnapshotForConfig(config),
            platform = event.platform,
            trigger = event.trigger,
        )
        val promptSkills = resourceProjection.promptSkills
        val toolSkills = resourceProjection.toolSkills
        val mcpServers = resourceProjection.mcpServers
        val toolSourceContext = ToolSourceContext.fromConfigSnapshot(
            config = config,
            requestId = requestId,
            platform = event.platform,
            conversationId = event.conversationId,
            mcpServers = mcpServers,
            promptSkills = promptSkills,
            toolSkills = toolSkills,
            ingressTrigger = event.trigger,
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
            scheduledTaskContextWindow = scheduledTaskContextWindow,
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
                supportsToolCalling = provider.supportsToolCalling,
                supportsStreaming = provider.supportsStreaming,
                supportsMultimodal = provider.supportsMultimodal,
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

    private fun resolveContextWindow(
        config: RuntimeConfigSnapshot,
    ): Int {
        val configMax = config.maxContextTurns
        return if (configMax <= 0) Int.MAX_VALUE else configMax
    }

    private fun hostCapabilityToolsForConfig(config: RuntimeConfigSnapshot): Set<String> {
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
