package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundResult
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.PlatformDeliveryFailure
import com.elymbot.core.contract.PlatformDeliveryResult
import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings

internal sealed interface QqOneBotIngressResult {
    data object Ignored : QqOneBotIngressResult

    data class Invalid(val error: OneBotPayloadError) : QqOneBotIngressResult

    data object Duplicate : QqOneBotIngressResult

    data class Dropped(val reason: QqInboundDropReason) : QqOneBotIngressResult

    data class RateLimited(
        val disposition: QqRateLimitDisposition,
        val replayResults: List<PlatformInboundResult>,
    ) : QqOneBotIngressResult

    data class Forwarded(
        val hostResult: PlatformInboundResult,
        val replayResults: List<PlatformInboundResult> = emptyList(),
    ) : QqOneBotIngressResult

    data class CommandHandled(
        val settingsChanged: Boolean,
        val retryable: Boolean,
        val replyResult: PlatformDeliveryResult?,
    ) : QqOneBotIngressResult
}

private sealed interface QqOneBotDispatchResult {
    data class Host(val receipt: PlatformInboundResult) : QqOneBotDispatchResult

    data class Command(
        val settingsChanged: Boolean,
        val retryable: Boolean,
        val replyResult: PlatformDeliveryResult?,
    ) : QqOneBotDispatchResult
}

/** Formal OneBot event entry used by the socket adapter and synthetic contract checks. */
internal class QqOneBotInboundAdapter(
    private val parser: OneBotMessageParser = OneBotMessageParser(),
    private val policy: QqInboundPolicy = QqInboundPolicy(),
    private val deduplicator: QqEventDeduplicator = QqEventDeduplicator(),
    private val rateLimiter: QqRateLimiter<PendingQqInbound> = QqRateLimiter(
        nowMillis = { System.currentTimeMillis() },
    ),
    private val mediaSources: QqMediaSourceRegistry = QqMediaSourceRegistry(),
    private val commandHandler: QqManagementCommandHandler? = null,
    private val currentSettingsProvider: (() -> QqPlatformSettings)? = null,
    private val currentCommandSettingsProvider: (() -> QqPlatformCommandSettings)? = null,
    private val commandReplySender: suspend (OneBotInboundEvent, String) -> PlatformDeliveryResult = { _, _ ->
        PlatformDeliveryResult.Failed(PlatformDeliveryFailure.NOT_CONNECTED, retryable = true)
    },
) {
    suspend fun accept(
        payload: String,
        configuration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): QqOneBotIngressResult {
        val event = when (val parsed = parser.parse(payload)) {
            OneBotPayloadParseResult.Ignored -> return QqOneBotIngressResult.Ignored
            is OneBotPayloadParseResult.Invalid -> return QqOneBotIngressResult.Invalid(parsed.error)
            is OneBotPayloadParseResult.Message -> parsed.event
        }
        val reservation = when (val result = deduplicator.reserve(event.eventKey)) {
            is QqEventDeduplicator.ReservationResult.Reserved -> result.reservation
            QqEventDeduplicator.ReservationResult.Duplicate -> return QqOneBotIngressResult.Duplicate
            QqEventDeduplicator.ReservationResult.Invalid -> {
                return QqOneBotIngressResult.Invalid(OneBotPayloadError.MISSING_MESSAGE_ID)
            }
        }
        val decision = try {
            policy.evaluate(event, configuration)
        } catch (failure: Throwable) {
            reservation.release()
            throw failure
        }

        return when (decision) {
            is QqInboundDecision.Dropped -> {
                sendPolicyNotice(reservation, event, decision.reason)
                QqOneBotIngressResult.Dropped(decision.reason)
            }
            is QqInboundDecision.ContextOnly -> {
                val pending = PendingQqInbound(event, reservation)
                dispatch(pending, decision.envelope, decision.event, sink).toIngressResult()
            }
            is QqInboundDecision.Trigger -> acceptTriggered(decision, event, configuration, reservation, sink)
        }
    }

    suspend fun releaseReady(
        sourceKey: String,
        configuration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): List<PlatformInboundResult> {
        val currentSettings = currentSettingsProvider?.invoke() ?: configuration
        val release = rateLimiter.releaseReady(sourceKey, currentSettings.rateLimitFor(sourceKey))
        release.discarded.forEach { it.reservation.release() }
        return dispatchReady(release.readyToReplay, configuration, sink)
    }

    suspend fun releaseReady(
        configuration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): List<PlatformInboundResult> {
        val results = mutableListOf<PlatformInboundResult>()
        rateLimiter.stashedSourceKeys().forEach { sourceKey ->
            results += releaseReady(sourceKey, configuration, sink)
        }
        return results
    }

    fun hasPendingStash(): Boolean = rateLimiter.stashedSourceKeys().isNotEmpty()

    fun clearStashes() {
        rateLimiter.clear().forEach { it.reservation.complete() }
    }

    private suspend fun acceptTriggered(
        decision: QqInboundDecision.Trigger,
        sourceEvent: OneBotInboundEvent,
        configuration: QqPlatformSettings,
        reservation: QqEventDeduplicator.Reservation,
        sink: PlatformInboundSink,
    ): QqOneBotIngressResult {
        val pending = PendingQqInbound(sourceEvent, reservation)
        val rule = decision.rateLimitRule
        if (rule == null) {
            return try {
                val released = rateLimiter.releaseReady(decision.rateLimitKey, rule)
                released.discarded.forEach { it.reservation.release() }
                val replayResults = dispatchReady(released.readyToReplay, configuration, sink)
                dispatchCurrentAfterReplay(pending, configuration, sink, replayResults)
            } catch (failure: Throwable) {
                reservation.release()
                throw failure
            }
        }

        var reservationStored = false
        return try {
            val limited = rateLimiter.tryAcquire(decision.rateLimitKey, rule, pending)
            limited.discarded.forEach { it.reservation.release() }
            reservationStored = limited.disposition == QqRateLimitDisposition.STASHED
            val replayResults = dispatchReady(limited.readyToReplay, configuration, sink)
            when (limited.disposition) {
                QqRateLimitDisposition.ALLOW ->
                    dispatchCurrentAfterReplay(pending, configuration, sink, replayResults)
                QqRateLimitDisposition.STASHED -> QqOneBotIngressResult.RateLimited(
                    disposition = limited.disposition,
                    replayResults = replayResults,
                )
                QqRateLimitDisposition.DROPPED -> {
                    discardAndNotify(
                        reservation = reservation,
                        event = sourceEvent,
                        notice = RATE_LIMIT_NOTICE.takeIf { commandSettings().replyWhenPermissionDenied },
                    )
                    QqOneBotIngressResult.RateLimited(limited.disposition, replayResults)
                }
            }
        } catch (failure: Throwable) {
            if (!reservationStored) reservation.release()
            throw failure
        }
    }

    private suspend fun dispatchReady(
        pendingEvents: List<PendingQqInbound>,
        fallbackConfiguration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): List<PlatformInboundResult> {
        val results = mutableListOf<PlatformInboundResult>()
        pendingEvents.forEachIndexed { index, pending ->
            try {
                val currentConfiguration = currentSettingsProvider?.invoke() ?: fallbackConfiguration
                when (val currentDecision = policy.evaluate(pending.event, currentConfiguration)) {
                    is QqInboundDecision.Dropped -> {
                        sendPolicyNotice(pending.reservation, pending.event, currentDecision.reason)
                    }
                    is QqInboundDecision.ContextOnly -> {
                        val result = dispatch(pending, currentDecision.envelope, currentDecision.event, sink)
                        if (result is QqOneBotDispatchResult.Host) results += result.receipt
                    }
                    is QqInboundDecision.Trigger -> {
                        val result = dispatch(pending, currentDecision.envelope, currentDecision.event, sink)
                        if (result is QqOneBotDispatchResult.Host) results += result.receipt
                    }
                }
            } catch (failure: Throwable) {
                pending.reservation.release()
                pendingEvents.drop(index + 1).forEach { it.reservation.release() }
                throw failure
            }
        }
        return results
    }

    private suspend fun dispatchCurrentAfterReplay(
        pending: PendingQqInbound,
        fallbackConfiguration: QqPlatformSettings,
        sink: PlatformInboundSink,
        replayResults: List<PlatformInboundResult>,
    ): QqOneBotIngressResult {
        val currentConfiguration = currentSettingsProvider?.invoke() ?: fallbackConfiguration
        return when (val currentDecision = policy.evaluate(pending.event, currentConfiguration)) {
            is QqInboundDecision.Dropped -> {
                sendPolicyNotice(pending.reservation, pending.event, currentDecision.reason)
                QqOneBotIngressResult.Dropped(currentDecision.reason)
            }
            is QqInboundDecision.ContextOnly -> dispatch(
                pending,
                currentDecision.envelope,
                currentDecision.event,
                sink,
            ).toIngressResult(replayResults)
            is QqInboundDecision.Trigger -> dispatch(
                pending,
                currentDecision.envelope,
                currentDecision.event,
                sink,
            ).toIngressResult(replayResults)
        }
    }

    private suspend fun sendPolicyNotice(
        reservation: QqEventDeduplicator.Reservation,
        event: OneBotInboundEvent,
        reason: QqInboundDropReason,
    ) {
        val notice = try {
            when (reason) {
                QqInboundDropReason.NOT_ALLOWED -> WHITELIST_DENIED_NOTICE
                    .takeIf { commandSettings().replyWhenPermissionDenied }
                QqInboundDropReason.WAKE_WORD_REQUIRED -> WAKE_WORD_REQUIRED_NOTICE
                    .takeIf { commandSettings().replyWhenPermissionDenied }
                QqInboundDropReason.KEYWORD_BLOCKED -> KEYWORD_BLOCK_NOTICE
                QqInboundDropReason.SELF_MESSAGE,
                QqInboundDropReason.AT_ALL_IGNORED,
                QqInboundDropReason.NO_TRIGGER,
                -> null
            }
        } catch (failure: Throwable) {
            reservation.release()
            throw failure
        }
        discardAndNotify(reservation, event, notice)
    }

    private suspend fun discardAndNotify(
        reservation: QqEventDeduplicator.Reservation,
        event: OneBotInboundEvent,
        notice: String?,
    ) {
        if (notice == null) {
            reservation.release()
            return
        }
        reservation.complete()
        commandReplySender(event, notice)
    }

    private fun commandSettings(): QqPlatformCommandSettings =
        currentCommandSettingsProvider?.invoke() ?: QqPlatformCommandSettings()

    private suspend fun dispatch(
        pending: PendingQqInbound,
        envelope: PlatformInboundEnvelope,
        event: OneBotInboundEvent,
        sink: PlatformInboundSink,
    ): QqOneBotDispatchResult {
        val command = commandHandler?.handle(event)
        if (command != null) {
            if (command.completeEvent) pending.reservation.complete() else pending.reservation.release()
            val replyResult = command.replyText?.let { commandReplySender(event, it) }
            return QqOneBotDispatchResult.Command(
                settingsChanged = command.settingsChanged,
                retryable = !command.completeEvent,
                replyResult = replyResult,
            )
        }

        return try {
            val result = mediaSources.withEventSources(pending.event.mediaSources) {
                sink.accept(envelope)
            }
            when (result) {
                PlatformInboundResult.Accepted,
                PlatformInboundResult.Duplicate,
                -> pending.reservation.complete()
                is PlatformInboundResult.Rejected -> pending.reservation.release()
            }
            QqOneBotDispatchResult.Host(result)
        } catch (failure: Throwable) {
            pending.reservation.release()
            throw failure
        }
    }

    private fun QqOneBotDispatchResult.toIngressResult(
        replayResults: List<PlatformInboundResult> = emptyList(),
    ): QqOneBotIngressResult = when (this) {
        is QqOneBotDispatchResult.Host -> QqOneBotIngressResult.Forwarded(receipt, replayResults)
        is QqOneBotDispatchResult.Command -> QqOneBotIngressResult.CommandHandled(
            settingsChanged = settingsChanged,
            retryable = retryable,
            replyResult = replyResult,
        )
    }

    private companion object {
        const val WHITELIST_DENIED_NOTICE = "Permission denied by whitelist."
        const val WAKE_WORD_REQUIRED_NOTICE = "Wake word required."
        const val RATE_LIMIT_NOTICE = "Rate limit exceeded."
        const val KEYWORD_BLOCK_NOTICE = "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。"
    }
}

private fun QqPlatformRateLimitSettings.toQqRateLimitRule() = QqRateLimitRule(
    windowMillis = windowMillis,
    maxMessages = maxMessages,
    strategy = when (strategy) {
        QqPlatformRateLimitStrategy.DROP -> QqRateLimitStrategy.DROP
        QqPlatformRateLimitStrategy.STASH -> QqRateLimitStrategy.STASH
    },
    maxStashedMessages = maxStashedMessages,
    maxStashAgeMillis = maxStashAgeMillis,
)

private fun QqPlatformSettings.rateLimitFor(sourceKey: String): QqRateLimitRule? = when (sourceKey.split(':').getOrNull(1)) {
    "private" -> privateRateLimit?.toQqRateLimitRule()
    "group" -> groupRateLimit?.toQqRateLimitRule()
    else -> null
}

internal data class PendingQqInbound(
    val event: OneBotInboundEvent,
    val reservation: QqEventDeduplicator.Reservation,
)

/** Media source locators exist only for the duration of synchronous App ingestion. */
internal class QqMediaSourceRegistry {
    private val lock = Any()
    private val current = mutableMapOf<String, OneBotMediaSource>()

    fun find(resourceId: String): OneBotMediaSource? = synchronized(lock) { current[resourceId] }

    suspend fun <T> withEventSources(
        sources: List<OneBotMediaSource>,
        action: suspend () -> T,
    ): T {
        val previous = synchronized(lock) {
            sources.associate { source -> source.resourceId to current.put(source.resourceId, source) }
        }
        try {
            return action()
        } finally {
            synchronized(lock) {
                previous.forEach { (resourceId, priorSource) ->
                    if (priorSource == null) current.remove(resourceId) else current[resourceId] = priorSource
                }
            }
        }
    }
}
