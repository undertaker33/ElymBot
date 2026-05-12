package com.astrbot.android.feature.plugin.runtime

class PluginV2EventResultCoordinator {
    internal data class DecoratingHandlerInvocation(
        val handlerId: String,
        val priority: Int,
    ) {
        init {
            require(handlerId.isNotBlank()) { "handlerId must not be blank." }
        }
    }

    data class DecoratingRunResult(
        override val finalResult: PluginMessageEventResult,
        override val appliedHandlerIds: List<String>,
        override val stoppedByHandlerId: String? = null,
        override val mutationTrace: List<MutationTraceEntry> = emptyList(),
    ) : PluginV2DecoratingRunSnapshot

    internal suspend fun runDecorating(
        initialResult: PluginMessageEventResult,
        handlers: List<DecoratingHandlerInvocation>,
        mutate: suspend (DecoratingHandlerInvocation, PluginMessageEventResult) -> Unit,
    ): DecoratingRunResult {
        val sortedHandlers = handlers.sortedWith(
            compareByDescending<DecoratingHandlerInvocation> { it.priority }
                .thenBy { it.handlerId },
        )

        var chain = NormalizedMessageResultChain.from(initialResult)
        val appliedHandlerIds = mutableListOf<String>()
        var stoppedByHandlerId: String? = null

        for (handler in sortedHandlers) {
            if (chain.stopped) {
                break
            }

            val draft = chain.toDraft()
            val mutableResult = draft.toPublicResult()
            mutate(handler, mutableResult)

            chain = NormalizedMessageResultChain.from(
                result = mutableResult,
                handlerId = handler.handlerId,
                priority = handler.priority,
                previousTrace = chain.mutationTrace,
            )
            appliedHandlerIds += handler.handlerId

            if (mutableResult.isStopped) {
                stoppedByHandlerId = handler.handlerId
            }
        }

        return DecoratingRunResult(
            finalResult = chain.toPublicResult(),
            appliedHandlerIds = appliedHandlerIds.toList(),
            stoppedByHandlerId = stoppedByHandlerId,
            mutationTrace = chain.mutationTrace,
        )
    }

    fun buildAfterSentView(
        requestId: String,
        conversationId: String,
        sendAttemptId: String,
        platformAdapterType: String,
        platformInstanceKey: String,
        sentAtEpochMs: Long,
        deliveryStatus: PluginV2AfterSentDeliveryStatus,
        receiptIds: List<String> = emptyList(),
        deliveredEntries: List<PluginV2AfterSentDeliveredEntry> = emptyList(),
        usage: PluginLlmUsageSnapshot? = null,
    ): PluginV2AfterSentView {
        val snapshot = SentMessageSnapshot(
            requestId = requestId,
            conversationId = conversationId,
            sendAttemptId = sendAttemptId,
            platformAdapterType = platformAdapterType,
            platformInstanceKey = platformInstanceKey,
            sentAtEpochMs = sentAtEpochMs,
            deliveryStatus = deliveryStatus,
            receiptIds = receiptIds.toList(),
            deliveredEntries = deliveredEntries.toList(),
            usage = usage,
        )
        return snapshot.toPublicView()
    }

    private data class MessageResultDraft(
        val requestId: String,
        val conversationId: String,
        val text: String,
        val markdown: Boolean,
        val attachments: List<PluginMessageEventResultAttachment>,
        val shouldSend: Boolean,
        val attachmentMutationIntent: PluginMessageEventAttachmentMutationIntent,
        val stopped: Boolean,
    ) {
        fun toPublicResult(): PluginMessageEventResult {
            return PluginMessageEventResult(
                requestId = requestId,
                conversationId = conversationId,
                text = text,
                markdown = markdown,
                attachments = attachments,
                shouldSend = shouldSend,
                attachmentMutationIntent = attachmentMutationIntent,
            ).also { result ->
                if (stopped) {
                    result.stop()
                }
            }
        }

        companion object {
            fun from(result: PluginMessageEventResult): MessageResultDraft {
                return MessageResultDraft(
                    requestId = result.requestId,
                    conversationId = result.conversationId,
                    text = result.text,
                    markdown = result.markdown,
                    attachments = result.attachments.toList(),
                    shouldSend = result.shouldSend,
                    attachmentMutationIntent = result.attachmentMutationIntent,
                    stopped = result.isStopped,
                )
            }
        }
    }

    private data class NormalizedMessageResultChain(
        val requestId: String,
        val conversationId: String,
        val text: String,
        val markdown: Boolean,
        val attachments: List<PluginMessageEventResultAttachment>,
        val shouldSend: Boolean,
        val attachmentMutationIntent: PluginMessageEventAttachmentMutationIntent,
        val stopped: Boolean,
        val mutationTrace: List<MutationTraceEntry>,
    ) {
        fun toDraft(): MessageResultDraft {
            return MessageResultDraft(
                requestId = requestId,
                conversationId = conversationId,
                text = text,
                markdown = markdown,
                attachments = attachments.toList(),
                shouldSend = shouldSend,
                attachmentMutationIntent = attachmentMutationIntent,
                stopped = stopped,
            )
        }

        fun toPublicResult(): PluginMessageEventResult {
            return MessageResultDraft(
                requestId = requestId,
                conversationId = conversationId,
                text = text,
                markdown = markdown,
                attachments = attachments.toList(),
                shouldSend = shouldSend,
                attachmentMutationIntent = attachmentMutationIntent,
                stopped = stopped,
            ).toPublicResult()
        }

        companion object {
            fun from(
                result: PluginMessageEventResult,
                handlerId: String? = null,
                priority: Int? = null,
                previousTrace: List<MutationTraceEntry> = emptyList(),
            ): NormalizedMessageResultChain {
                val traceEntry = if (handlerId != null && priority != null) {
                    MutationTraceEntry(
                        handlerId = handlerId,
                        priority = priority,
                        text = result.text,
                        attachmentCount = result.attachments.size,
                        attachmentMutationIntent = result.attachmentMutationIntent,
                        shouldSend = result.shouldSend,
                        stopped = result.isStopped,
                    )
                } else {
                    null
                }

                return NormalizedMessageResultChain(
                    requestId = result.requestId,
                    conversationId = result.conversationId,
                    text = result.text,
                    markdown = result.markdown,
                    attachments = result.attachments.toList(),
                    shouldSend = result.shouldSend,
                    attachmentMutationIntent = result.attachmentMutationIntent,
                    stopped = result.isStopped,
                    mutationTrace = if (traceEntry == null) {
                        previousTrace.toList()
                    } else {
                        previousTrace + traceEntry
                    },
                )
            }
        }
    }

    private data class SentMessageSnapshot(
        val requestId: String,
        val conversationId: String,
        val sendAttemptId: String,
        val platformAdapterType: String,
        val platformInstanceKey: String,
        val sentAtEpochMs: Long,
        val deliveryStatus: PluginV2AfterSentDeliveryStatus,
        val receiptIds: List<String>,
        val deliveredEntries: List<PluginV2AfterSentDeliveredEntry>,
        val usage: PluginLlmUsageSnapshot?,
    ) {
        fun toPublicView(): PluginV2AfterSentView {
            return PluginV2AfterSentView(
                requestId = requestId,
                conversationId = conversationId,
                sendAttemptId = sendAttemptId,
                platformAdapterType = platformAdapterType,
                platformInstanceKey = platformInstanceKey,
                sentAtEpochMs = sentAtEpochMs,
                deliveryStatus = deliveryStatus,
                receiptIds = receiptIds.toList(),
                deliveredEntries = deliveredEntries.toList(),
                usage = usage,
            )
        }
    }

data class MutationTraceEntry(
        val handlerId: String,
        val priority: Int,
        val text: String,
        val attachmentCount: Int,
        val attachmentMutationIntent: PluginMessageEventAttachmentMutationIntent,
        val shouldSend: Boolean,
        val stopped: Boolean,
    )

}
