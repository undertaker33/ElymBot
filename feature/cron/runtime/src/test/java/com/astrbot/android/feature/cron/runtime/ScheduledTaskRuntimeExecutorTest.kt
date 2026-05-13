package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.runtime.context.ContextPolicy
import com.astrbot.android.core.runtime.context.DeliveryPolicy
import com.astrbot.android.core.runtime.context.IngressTrigger
import com.astrbot.android.core.runtime.context.ProviderCapabilitySnapshot
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.RuntimeBotSnapshot
import com.astrbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.context.RuntimeIngressEvent
import com.astrbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.astrbot.android.core.runtime.context.ToolSourceContext
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.LlmPipelineAdmission
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.domain.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeSnapshot
import com.astrbot.android.feature.plugin.domain.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2EventResultCoordinator
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryRequest
import com.astrbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineInput
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmStageDispatchResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ToolRegistryEntry
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTaskRuntimeExecutorTest {
    @Test
    fun execute_resolves_scheduled_task_context_and_delivers_prepared_reply_through_port() = runBlocking {
        val bot = BotProfile(
            id = "bot-1",
            displayName = "Bot",
            defaultProviderId = "provider-1",
            configProfileId = "config-1",
        )
        val resolver = RecordingRuntimeContextResolverPort()
        val deliveryPort = RecordingScheduledMessageDeliveryPort()
        val orchestrator = DeliveringRuntimeOrchestrator()

        val summary = ScheduledTaskRuntimeExecutor.execute(
            context = scheduledContext(platform = "qq", conversationId = "group:10001"),
            runtimeDependencies = ScheduledTaskRuntimeDependencies(
                llmClient = NoOpLlmClient,
                botPort = FakeBotRepositoryPort(bot),
                orchestrator = orchestrator,
                runtimeContextResolverPort = resolver,
                deliveryPort = deliveryPort,
                appChatPluginRuntime = ThrowingCronAppChatLlmPipelineRuntime,
                hostCapabilityGateway = CronNoOpPluginHostCapabilityGateway,
            ),
        )

        val ingress = resolver.capturedEvent
        assertEquals(IngressTrigger.SCHEDULED_TASK, ingress?.trigger)
        assertEquals("qq_onebot", ingress?.platform?.wireValue)
        assertEquals("group", ingress?.messageType?.wireValue)
        assertEquals("Remember to drink water.", ingress?.text)
        assertEquals("provider-1", resolver.capturedProviderOverride)
        assertEquals("persona-1", resolver.capturedPersonaOverride)
        assertEquals("scheduled_task", orchestrator.userMessage?.role)
        assertEquals("cron:job-1", orchestrator.userMessage?.id)

        val request = deliveryPort.requests.single()
        assertEquals("qq", request.platform)
        assertEquals("group:10001", request.conversationId)
        assertEquals("Time to drink water.", request.text)
        assertEquals("bot-1", request.botId)
        assertEquals("https://example.invalid/image.png", request.attachments.single().remoteUrl)

        assertEquals("qq", summary.platform)
        assertEquals("group:10001", summary.conversationId)
        assertEquals(1, summary.deliveredMessageCount)
        assertEquals(listOf("receipt-1"), summary.receiptIds)
        assertEquals("Time to drink water.", summary.textPreview)
    }

    @Test
    fun execute_rejects_empty_note_before_dispatching_runtime() = runBlocking {
        val orchestrator = DeliveringRuntimeOrchestrator()

        val failure = runCatching {
            ScheduledTaskRuntimeExecutor.execute(
                context = scheduledContext(note = "", description = "   "),
                runtimeDependencies = ScheduledTaskRuntimeDependencies(
                    llmClient = NoOpLlmClient,
                    botPort = FakeBotRepositoryPort(BotProfile(id = "bot-1", configProfileId = "config-1")),
                    orchestrator = orchestrator,
                    runtimeContextResolverPort = RecordingRuntimeContextResolverPort(),
                    deliveryPort = RecordingScheduledMessageDeliveryPort(),
                    appChatPluginRuntime = ThrowingCronAppChatLlmPipelineRuntime,
                    hostCapabilityGateway = CronNoOpPluginHostCapabilityGateway,
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is CronJobExecutionFailure)
        assertEquals("empty_note", (failure as CronJobExecutionFailure).code)
        assertEquals(false, failure.retryable)
        assertEquals(null, orchestrator.userMessage)
    }

    private fun scheduledContext(
        platform: String = "app",
        conversationId: String = "chat-1",
        note: String = "Remember to drink water.",
        description: String = "Hydration reminder",
    ): CronJobExecutionContext {
        return CronJobExecutionContext(
            jobId = "job-1",
            name = "Drink water",
            description = description,
            jobType = "active_agent",
            note = note,
            sessionId = "legacy-session",
            platform = platform,
            conversationId = conversationId,
            botId = "bot-1",
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "provider-1",
            origin = "active_capability",
            runOnce = true,
            runAt = "2026-05-09T12:00:00+08:00",
        )
    }
}

private class RecordingRuntimeContextResolverPort : RuntimeContextResolverPort {
    var capturedEvent: RuntimeIngressEvent? = null
    var capturedProviderOverride: String? = null
    var capturedPersonaOverride: String? = null

    override fun resolve(
        event: RuntimeIngressEvent,
        bot: RuntimeBotSnapshot,
        overrideProviderId: String?,
        overridePersonaId: String?,
    ): ResolvedRuntimeContext {
        capturedEvent = event
        capturedProviderOverride = overrideProviderId
        capturedPersonaOverride = overridePersonaId
        return resolvedRuntimeContext(event, bot)
    }
}

private class DeliveringRuntimeOrchestrator : RuntimeLlmOrchestratorPort {
    var userMessage: ConversationMessage? = null

    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
    ): PluginV2HostLlmDeliveryResult {
        this.userMessage = userMessage
        val pipelineResult = pluginPipelineResult(
            conversationId = ctx.conversationId,
            messageId = userMessage.id,
        )
        val prepared = callbacks.prepareReply(pipelineResult)
        val sendResult = callbacks.sendReply(prepared)
        return if (sendResult.success) {
            PluginV2HostLlmDeliveryResult.Sent(
                pipelineResult = pipelineResult,
                preparedReply = prepared,
                sendResult = sendResult,
                afterSentView = afterSentView(ctx.conversationId, prepared, sendResult),
            )
        } else {
            PluginV2HostLlmDeliveryResult.SendFailed(pipelineResult, sendResult)
        }
    }
}

private class RecordingScheduledMessageDeliveryPort : ScheduledMessageDeliveryPort {
    val requests = mutableListOf<ScheduledMessageDeliveryRequest>()

    override suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        requests += request
        return ScheduledMessageDeliveryResult(
            success = true,
            deliveredMessageCount = 1,
            receiptIds = listOf("receipt-1"),
        )
    }
}

private class FakeBotRepositoryPort(private val bot: BotProfile) : BotRepositoryPort {
    override val bots = MutableStateFlow(listOf(bot))
    override val selectedBotId = MutableStateFlow(bot.id)
    override fun currentBot(): BotProfile = bot
    override fun snapshotProfiles(): List<BotProfile> = listOf(bot)
    override fun create(name: String): BotProfile = bot.copy(displayName = name)
    override suspend fun save(profile: BotProfile) = Unit
    override suspend fun create(profile: BotProfile) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun select(id: String) = Unit
}

private object NoOpLlmClient : LlmClientPort {
    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult =
        LlmInvocationResult(text = "")

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = emptyFlow()
}

private object ThrowingCronAppChatLlmPipelineRuntime : AppChatLlmPipelineRuntime {
    override suspend fun runLlmPipeline(input: PluginV2LlmPipelineInput): PluginV2LlmPipelineResult =
        error("The fake orchestrator should not call runLlmPipeline.")

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult = error("The fake orchestrator should not call deliverLlmPipeline.")

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult = error("The fake orchestrator should not call dispatchAfterMessageSent.")
}

private object CronNoOpPluginHostCapabilityGateway : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ) = error("executeHostAction should not be called in this test")

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext = context

    override fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext = context

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot = snapshot

    override fun executeHostBuiltinTool(args: PluginToolArgs): PluginToolResult? = null

    override fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean = true
}

private fun resolvedRuntimeContext(
    event: RuntimeIngressEvent,
    bot: RuntimeBotSnapshot,
): ResolvedRuntimeContext {
    val config = RuntimeConfigSnapshot(id = "config-1", defaultChatProviderId = "provider-1")
    val provider = RuntimeProviderSnapshot(
        id = "provider-1",
        name = "Provider",
        baseUrl = "https://example.invalid/v1",
        model = "model-1",
        providerType = "CUSTOM",
        apiKey = "",
        capabilities = setOf("CHAT"),
        enabled = true,
    )
    return ResolvedRuntimeContext(
        requestId = "ctx-1",
        ingressEvent = event,
        bot = bot,
        config = config,
        persona = null,
        provider = provider,
        availableProviders = listOf(provider),
        conversationId = event.conversationId,
        messageWindow = emptyList(),
        contextPolicy = ContextPolicy(
            strategy = config.contextLimitStrategy,
            maxTurns = config.maxContextTurns,
            dequeueTurns = config.dequeueContextTurns,
            compressInstruction = config.llmCompressInstruction,
            compressKeepRecent = config.llmCompressKeepRecent,
            compressProviderId = config.llmCompressProviderId,
        ),
        personaToolSnapshot = null,
        providerCapabilities = ProviderCapabilitySnapshot(
            supportsToolCalling = false,
            supportsStreaming = false,
            supportsMultimodal = false,
        ),
        webSearchEnabled = false,
        proactiveEnabled = false,
        mcpServers = emptyList(),
        skills = emptyList(),
        promptSkills = emptyList(),
        toolSkills = emptyList(),
        toolSourceContext = ToolSourceContext.fromConfigSnapshot(
            config = config,
            requestId = "ctx-1",
            platform = event.platform,
            conversationId = event.conversationId,
            ingressTrigger = event.trigger,
        ),
        deliveryPolicy = DeliveryPolicy(
            platform = event.platform,
            streamingEnabled = false,
            quoteSenderMessage = false,
            mentionSender = false,
            replyTextPrefix = "",
            ttsEnabled = false,
            alwaysTts = false,
        ),
        realWorldTimeAwarenessEnabled = false,
    )
}

private fun pluginPipelineResult(
    conversationId: String,
    messageId: String,
): PluginV2LlmPipelineResult {
    val admission = LlmPipelineAdmission(
        requestId = "req-1",
        conversationId = conversationId,
        messageIds = listOf(messageId),
        llmInputSnapshot = "Remember to drink water.",
        routingTarget = AppChatLlm.AppChat,
        streamingMode = PluginV2StreamingMode.NON_STREAM,
    )
    val response = PluginLlmResponse(
        requestId = "req-1",
        providerId = "provider-1",
        modelId = "model-1",
        text = "Time to drink water.",
    )
    val sendable = PluginMessageEventResult(
        requestId = "req-1",
        conversationId = conversationId,
        text = "Time to drink water.",
        attachments = listOf(
            PluginMessageEventResult.Attachment(
                uri = "https://example.invalid/image.png",
                mimeType = "image/png",
            ),
        ),
    )
    return PluginV2LlmPipelineResult(
        admission = admission,
        finalRequest = PluginProviderRequest(
            requestId = "req-1",
            availableProviderIds = listOf("provider-1"),
            availableModelIdsByProvider = mapOf("provider-1" to listOf("model-1")),
            conversationId = conversationId,
            messageIds = listOf(messageId),
            llmInputSnapshot = "Remember to drink water.",
            selectedProviderId = "provider-1",
            selectedModelId = "model-1",
        ),
        finalResponse = response,
        sendableResult = sendable,
        hookInvocationTrace = emptyList(),
        decoratingRunResult = PluginV2EventResultCoordinator.DecoratingRunResult(
            finalResult = sendable,
            appliedHandlerIds = emptyList(),
        ),
    )
}

private fun afterSentView(
    conversationId: String,
    prepared: com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply,
    sendResult: PluginV2HostSendResult,
): PluginV2AfterSentView {
    return PluginV2AfterSentView(
        requestId = "req-1",
        conversationId = conversationId,
        sendAttemptId = "send-1",
        platformAdapterType = "scheduled_task",
        platformInstanceKey = "cron:job-1",
        sentAtEpochMs = 1L,
        deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
        receiptIds = sendResult.receiptIds,
        deliveredEntries = prepared.deliveredEntries,
    )
}
