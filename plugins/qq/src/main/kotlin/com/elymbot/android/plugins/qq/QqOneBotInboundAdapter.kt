package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundResult
import com.elymbot.core.contract.PlatformInboundSink
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
                reservation.release()
                QqOneBotIngressResult.Dropped(decision.reason)
            }
            is QqInboundDecision.ContextOnly -> {
                val pending = PendingQqInbound(event, reservation)
                QqOneBotIngressResult.Forwarded(dispatch(pending, decision.envelope, sink))
            }
            is QqInboundDecision.Trigger -> acceptTriggered(decision, event, configuration, reservation, sink)
        }
    }

    suspend fun releaseReady(
        sourceKey: String,
        configuration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): List<PlatformInboundResult> {
        val release = rateLimiter.releaseReady(sourceKey, configuration.rateLimitFor(sourceKey))
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
                QqOneBotIngressResult.Forwarded(
                    hostResult = dispatch(pending, decision.envelope, sink),
                    replayResults = replayResults,
                )
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
                QqRateLimitDisposition.ALLOW -> QqOneBotIngressResult.Forwarded(
                    hostResult = dispatch(pending, decision.envelope, sink),
                    replayResults = replayResults,
                )
                QqRateLimitDisposition.STASHED -> QqOneBotIngressResult.RateLimited(
                    disposition = limited.disposition,
                    replayResults = replayResults,
                )
                QqRateLimitDisposition.DROPPED -> {
                    reservation.release()
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
        configuration: QqPlatformSettings,
        sink: PlatformInboundSink,
    ): List<PlatformInboundResult> {
        val results = mutableListOf<PlatformInboundResult>()
        pendingEvents.forEachIndexed { index, pending ->
            try {
                when (val currentDecision = policy.evaluate(pending.event, configuration)) {
                    is QqInboundDecision.Dropped -> pending.reservation.release()
                    is QqInboundDecision.ContextOnly -> {
                        results += dispatch(pending, currentDecision.envelope, sink)
                    }
                    is QqInboundDecision.Trigger -> {
                        results += dispatch(pending, currentDecision.envelope, sink)
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

    private suspend fun dispatch(
        pending: PendingQqInbound,
        envelope: PlatformInboundEnvelope,
        sink: PlatformInboundSink,
    ): PlatformInboundResult = try {
        val result = mediaSources.withEventSources(pending.event.mediaSources) {
            sink.accept(envelope)
        }
        when (result) {
            PlatformInboundResult.Accepted,
            PlatformInboundResult.Duplicate,
            -> pending.reservation.complete()
            is PlatformInboundResult.Rejected -> pending.reservation.release()
        }
        result
    } catch (failure: Throwable) {
        pending.reservation.release()
        throw failure
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
