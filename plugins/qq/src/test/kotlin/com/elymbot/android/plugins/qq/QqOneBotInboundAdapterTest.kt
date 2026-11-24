package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundRejection
import com.elymbot.core.contract.PlatformInboundResult
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class QqOneBotInboundAdapterTest {
    @Test
    fun formalAdapterForwardsOnceAndUsesHostIdempotentReceipt() = runBlocking {
        val adapter = QqOneBotInboundAdapter(deduplicator = QqEventDeduplicator())
        val received = mutableListOf<PlatformInboundEnvelope>()
        val sink = PlatformInboundSink { envelope ->
            received += envelope
            PlatformInboundResult.Accepted
        }

        val first = adapter.accept(privatePayload("1", "hello"), configuration(), sink)
        val duplicate = adapter.accept(privatePayload("1", "hello"), configuration(), sink)

        assertTrue(first is QqOneBotIngressResult.Forwarded)
        assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
        assertEquals(1, received.size)
        assertEquals("private:2000", received.single().message.origin.conversationId)
    }

    @Test
    fun persistenceRejectionReleasesReservationForRetry() = runBlocking {
        val adapter = QqOneBotInboundAdapter(deduplicator = QqEventDeduplicator())
        var calls = 0
        val sink = PlatformInboundSink {
            calls += 1
            if (calls == 1) {
                PlatformInboundResult.Rejected(PlatformInboundRejection.PERSISTENCE)
            } else {
                PlatformInboundResult.Accepted
            }
        }

        val rejected = adapter.accept(privatePayload("1", "hello"), configuration(), sink)
        val retried = adapter.accept(privatePayload("1", "hello"), configuration(), sink)
        val duplicate = adapter.accept(privatePayload("1", "hello"), configuration(), sink)

        assertTrue(rejected is QqOneBotIngressResult.Forwarded)
        assertEquals(
            PlatformInboundResult.Rejected(PlatformInboundRejection.PERSISTENCE),
            (rejected as QqOneBotIngressResult.Forwarded).hostResult,
        )
        assertEquals(PlatformInboundResult.Accepted, (retried as QqOneBotIngressResult.Forwarded).hostResult)
        assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
        assertEquals(2, calls)
    }

    @Test
    fun thrownSinkFailureReleasesReservationForRetry() = runBlocking {
        val adapter = QqOneBotInboundAdapter(deduplicator = QqEventDeduplicator())
        var calls = 0
        val sink = PlatformInboundSink {
            calls += 1
            if (calls == 1) error("synthetic sink failure")
            PlatformInboundResult.Accepted
        }

        val failure = runCatching {
            adapter.accept(privatePayload("1", "hello"), configuration(), sink)
        }.exceptionOrNull()
        val retried = adapter.accept(privatePayload("1", "hello"), configuration(), sink)

        assertTrue(failure is IllegalStateException)
        assertEquals(PlatformInboundResult.Accepted, (retried as QqOneBotIngressResult.Forwarded).hostResult)
        assertEquals(2, calls)
    }

    @Test
    fun cancellationReleasesReservationForRetry() = runBlocking {
        val adapter = QqOneBotInboundAdapter(deduplicator = QqEventDeduplicator())
        var calls = 0
        val sink = PlatformInboundSink {
            calls += 1
            if (calls == 1) throw CancellationException("synthetic cancellation")
            PlatformInboundResult.Accepted
        }

        supervisorScope {
            val first = async { adapter.accept(privatePayload("1", "hello"), configuration(), sink) }
            val failure = runCatching {
                withTimeout(COORDINATED_AWAIT_TIMEOUT_MILLIS) { first.await() }
            }.exceptionOrNull()
            val retry = adapter.accept(privatePayload("1", "hello"), configuration(), sink)

            assertTrue(failure is CancellationException)
            assertEquals(PlatformInboundResult.Accepted, (retry as QqOneBotIngressResult.Forwarded).hostResult)
        }
        assertEquals(2, calls)
    }

    @Test
    fun inFlightDuplicateIsSuppressedUntilFirstSinkCallCompletes() = runBlocking {
        val adapter = QqOneBotInboundAdapter(deduplicator = QqEventDeduplicator())
        val sinkStarted = CompletableDeferred<Unit>()
        val finishSink = CompletableDeferred<Unit>()
        var calls = 0
        val sink = PlatformInboundSink {
            calls += 1
            sinkStarted.complete(Unit)
            withTimeout(COORDINATED_AWAIT_TIMEOUT_MILLIS) { finishSink.await() }
            PlatformInboundResult.Accepted
        }

        val first = async { adapter.accept(privatePayload("1", "hello"), configuration(), sink) }
        withTimeout(COORDINATED_AWAIT_TIMEOUT_MILLIS) { sinkStarted.await() }
        val duplicate = adapter.accept(privatePayload("1", "hello"), configuration(), sink)
        finishSink.complete(Unit)
        val accepted = withTimeout(COORDINATED_AWAIT_TIMEOUT_MILLIS) { first.await() }

        assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
        assertEquals(PlatformInboundResult.Accepted, (accepted as QqOneBotIngressResult.Forwarded).hostResult)
        assertEquals(1, calls)
    }

    @Test
    fun stashReplayIsOrderedAndOnlyAcceptedAfterRateWindowOpens() = runBlocking {
        var now = 0L
        val rule = rateLimitRule()
        val adapter = QqOneBotInboundAdapter(
            deduplicator = QqEventDeduplicator(),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        val received = mutableListOf<String>()
        val sink = PlatformInboundSink { envelope ->
            received += envelope.message.messageId.orEmpty()
            PlatformInboundResult.Accepted
        }

        adapter.accept(privatePayload("1", "first"), configuration(privateRateLimit = rule), sink)
        val stashed = adapter.accept(privatePayload("2", "second"), configuration(privateRateLimit = rule), sink)
        assertTrue(stashed is QqOneBotIngressResult.RateLimited)
        assertEquals(listOf("1"), received)

        now = 100
        val replay = adapter.accept(privatePayload("3", "third"), configuration(privateRateLimit = rule), sink)
        assertTrue(replay is QqOneBotIngressResult.RateLimited)
        assertEquals(1, (replay as QqOneBotIngressResult.RateLimited).replayResults.size)
        assertEquals(listOf("1", "2"), received)

        now = 200
        val replayed = adapter.releaseReady(
            sourceKey = "1000:private:2000",
            configuration = configuration(privateRateLimit = rule),
            sink = sink,
        )
        assertEquals(listOf(PlatformInboundResult.Accepted), replayed)
        assertEquals(listOf("1", "2", "3"), received)
    }

    @Test
    fun stashReplayReevaluatesCurrentWhitelistAndReleasesDroppedReservation() = runBlocking {
        var now = 0L
        val rule = rateLimitRule()
        val adapter = QqOneBotInboundAdapter(
            deduplicator = QqEventDeduplicator(),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        val received = mutableListOf<String>()
        val sink = PlatformInboundSink { envelope ->
            received += envelope.message.messageId.orEmpty()
            PlatformInboundResult.Accepted
        }
        val pendingPayload = privatePayload("2", "pending")

        adapter.accept(privatePayload("1", "first"), configuration(privateRateLimit = rule), sink)
        val stashed = adapter.accept(pendingPayload, configuration(privateRateLimit = rule), sink)
        assertTrue(stashed is QqOneBotIngressResult.RateLimited)

        now = 100
        val currentConfiguration = configuration(privateRateLimit = rule).copy(
            whitelistEnabled = true,
            allowedPrivateUserIds = setOf("different-user"),
        )
        val replay = adapter.releaseReady(
            sourceKey = "1000:private:2000",
            configuration = currentConfiguration,
            sink = sink,
        )
        assertTrue(replay.isEmpty())
        assertEquals(listOf("1"), received)

        val retriedAfterAllow = adapter.accept(pendingPayload, configuration(), sink)
        assertTrue(retriedAfterAllow is QqOneBotIngressResult.Forwarded)
        assertEquals(listOf("1", "2"), received)
    }

    @Test
    fun stashReplayUsesCurrentCommandAliasAndOriginalInput() = runBlocking {
        var now = 0L
        val rule = rateLimitRule()
        val adapter = QqOneBotInboundAdapter(
            deduplicator = QqEventDeduplicator(),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        val receivedTexts = mutableListOf<String>()
        val sink = PlatformInboundSink { envelope ->
            receivedTexts += envelope.message.segments.filterIsInstance<com.elymbot.core.contract.AgentMessageSegment.Text>()
                .joinToString("") { it.value }
            PlatformInboundResult.Accepted
        }

        adapter.accept(privatePayload("1", "first"), configuration(privateRateLimit = rule), sink)
        val pendingPayload = privatePayload("2", "/alias argument")
        val originalConfiguration = configuration(
            privateRateLimit = rule,
            commandAliases = mapOf("alias" to "help"),
        )
        adapter.accept(pendingPayload, originalConfiguration, sink)

        now = 100
        val replay = adapter.releaseReady(
            sourceKey = "1000:private:2000",
            configuration = configuration(
                privateRateLimit = rule,
                commandAliases = mapOf("alias" to "reset"),
            ),
            sink = sink,
        )

        assertEquals(listOf(PlatformInboundResult.Accepted), replay)
        assertEquals("/reset argument", receivedTexts.last())
    }

    @Test
    fun expiredStashReleasesReservationForRedelivery() = runBlocking {
        var now = 0L
        val rule = rateLimitRule(maxStashAgeMillis = 50)
        val adapter = QqOneBotInboundAdapter(
            deduplicator = QqEventDeduplicator(),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        val received = mutableListOf<String>()
        val sink = PlatformInboundSink { envelope ->
            received += envelope.message.messageId.orEmpty()
            PlatformInboundResult.Accepted
        }
        val pendingPayload = privatePayload("2", "pending")

        adapter.accept(privatePayload("1", "first"), configuration(privateRateLimit = rule), sink)
        adapter.accept(pendingPayload, configuration(privateRateLimit = rule), sink)
        now = 60
        adapter.releaseReady(
            sourceKey = "1000:private:2000",
            configuration = configuration(privateRateLimit = rule),
            sink = sink,
        )

        val retried = adapter.accept(pendingPayload, configuration(), sink)

        assertTrue(retried is QqOneBotIngressResult.Forwarded)
        assertEquals(listOf("1", "2"), received)
    }

    @Test
    fun clearingStashesOnLogoutCompletesDiscardedEventsAndResetsRateWindow() = runBlocking {
        var now = 0L
        val rule = rateLimitRule()
        val adapter = QqOneBotInboundAdapter(
            deduplicator = QqEventDeduplicator(),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        val received = mutableListOf<String>()
        val sink = PlatformInboundSink { envelope ->
            received += envelope.message.messageId.orEmpty()
            PlatformInboundResult.Accepted
        }
        val pending = privatePayload("pending", "pending")

        adapter.accept(privatePayload("first", "first"), configuration(privateRateLimit = rule), sink)
        assertTrue(adapter.accept(pending, configuration(privateRateLimit = rule), sink)
            is QqOneBotIngressResult.RateLimited)
        assertTrue(adapter.hasPendingStash())

        adapter.clearStashes()
        assertFalse(adapter.hasPendingStash())
        assertEquals(
            QqOneBotIngressResult.Duplicate,
            adapter.accept(pending, configuration(privateRateLimit = rule), sink),
        )
        now += 1
        assertEquals(listOf("first"), received)
    }

    private fun rateLimitRule(maxStashAgeMillis: Long = 1_000) = QqPlatformRateLimitSettings(
        windowMillis = 100,
        maxMessages = 1,
        strategy = QqPlatformRateLimitStrategy.STASH,
        maxStashedMessages = 3,
        maxStashAgeMillis = maxStashAgeMillis,
    )

    private fun configuration(
        privateRateLimit: QqPlatformRateLimitSettings? = null,
        whitelistEnabled: Boolean = false,
        allowedPrivateUserIds: Set<String> = emptySet(),
        commandAliases: Map<String, String> = emptyMap(),
    ) = QqPlatformSettings(
        whitelistEnabled = whitelistEnabled,
        privateChatRequiresWakeWord = false,
        allowedPrivateUserIds = allowedPrivateUserIds,
        commandAliases = commandAliases,
        privateRateLimit = privateRateLimit,
        groupRateLimit = null,
    )

    private fun privatePayload(messageId: String, text: String) =
        """{"post_type":"message","message_type":"private","self_id":"1000","user_id":"2000","message_id":"$messageId","message":[{"type":"text","data":{"text":"$text"}}]}"""

    private companion object {
        const val COORDINATED_AWAIT_TIMEOUT_MILLIS = 2_000L
    }
}
