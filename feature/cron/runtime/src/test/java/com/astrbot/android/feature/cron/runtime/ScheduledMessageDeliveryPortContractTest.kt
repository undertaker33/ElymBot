package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.feature.bot.domain.model.BotProfile
import com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutionResult
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2ToolRegistryEntry
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMessageDeliveryPortContractTest {
    @Test
    fun callback_sendReply_forwards_prepared_reply_to_delivery_port() = runBlocking {
        val deliveryPort = ContractRecordingScheduledMessageDeliveryPort(
            result = ScheduledMessageDeliveryResult(
                success = true,
                deliveredMessageCount = 1,
                receiptIds = listOf("receipt-1"),
            ),
        )
        val callbacks = callbackFactory(deliveryPort).create(
            context = scheduledContext(platform = "qq_onebot"),
            conversationId = "group:10001",
            bot = BotProfile(id = "bot-1"),
        )
        val attachment = ConversationAttachment(
            id = "image-1",
            type = "image",
            mimeType = "image/png",
            remoteUrl = "https://example.invalid/image.png",
        )

        val result = callbacks.sendReply(
            PluginV2HostPreparedReply(
                text = "Time to drink water.",
                attachments = listOf(attachment),
                deliveredEntries = listOf(
                    PluginV2AfterSentView.DeliveredEntry(
                        entryId = "assistant-1",
                        entryType = "assistant",
                        textPreview = "Time to drink water.",
                        attachmentCount = 1,
                    ),
                ),
            ),
        )

        assertTrue(result.success)
        assertEquals(listOf("receipt-1"), result.receiptIds)
        val request = deliveryPort.requests.single()
        assertEquals("qq_onebot", request.platform)
        assertEquals("group:10001", request.conversationId)
        assertEquals("Time to drink water.", request.text)
        assertEquals(listOf(attachment), request.attachments)
        assertEquals("bot-1", request.botId)
    }

    @Test
    fun callback_sendReply_maps_delivery_failure_to_host_send_failure() = runBlocking {
        val callbacks = callbackFactory(
            ContractRecordingScheduledMessageDeliveryPort(
                result = ScheduledMessageDeliveryResult(
                    success = false,
                    deliveredMessageCount = 0,
                    errorCode = "unsupported_platform",
                    errorSummary = "",
                ),
            ),
        ).create(
            context = scheduledContext(platform = "email"),
            conversationId = "chat-1",
            bot = BotProfile(id = "bot-1"),
        )

        val result = callbacks.sendReply(PluginV2HostPreparedReply(text = "Hello"))

        assertFalse(result.success)
        assertEquals("unsupported_platform", result.errorSummary)
    }

    private fun callbackFactory(
        deliveryPort: ScheduledMessageDeliveryPort,
    ): ScheduledTaskLlmCallbacksFactory {
        return ScheduledTaskLlmCallbacksFactory(
            deliveryPort = deliveryPort,
            providerInvocationService = ScheduledTaskProviderInvocationService(ContractNoOpLlmClient),
            hostCapabilityGateway = ContractNoOpPluginHostCapabilityGateway,
        )
    }

    private fun scheduledContext(platform: String): CronJobExecutionContext {
        return CronJobExecutionContext(
            jobId = "job-1",
            name = "Drink water",
            description = "Hydration reminder",
            jobType = "active_agent",
            note = "Remember to drink water.",
            sessionId = "chat-1",
            platform = platform,
            conversationId = "chat-1",
            botId = "bot-1",
            configProfileId = "config-1",
            personaId = "",
            providerId = "provider-1",
            origin = "test",
            runOnce = true,
            runAt = "2026-05-09T12:00:00+08:00",
        )
    }
}

private class ContractRecordingScheduledMessageDeliveryPort(
    private val result: ScheduledMessageDeliveryResult,
) : ScheduledMessageDeliveryPort {
    val requests = mutableListOf<ScheduledMessageDeliveryRequest>()

    override suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        requests += request
        return result
    }
}

private object ContractNoOpLlmClient : LlmClientPort {
    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult =
        LlmInvocationResult(text = "")

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = emptyFlow()
}

private object ContractNoOpPluginHostCapabilityGateway : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        throw UnsupportedOperationException("executeHostAction should not be called in this test")
    }

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
