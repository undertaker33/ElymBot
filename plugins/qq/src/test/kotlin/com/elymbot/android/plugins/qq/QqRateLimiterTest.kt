package com.elymbot.android.plugins.qq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqRateLimiterTest {
    @Test
    fun dropStrategyAllowsOnlyConfiguredMessagesPerWindowAndPerSource() {
        var now = 0L
        val limiter = QqRateLimiter<String>(nowMillis = { now })
        val rule = QqRateLimitRule(
            windowMillis = 100,
            maxMessages = 1,
            strategy = QqRateLimitStrategy.DROP,
            maxStashedMessages = 0,
            maxStashAgeMillis = 1_000,
        )

        assertEquals(QqRateLimitDisposition.ALLOW, limiter.tryAcquire("member-a", rule, "a1").disposition)
        assertEquals(QqRateLimitDisposition.DROPPED, limiter.tryAcquire("member-a", rule, "a2").disposition)
        assertEquals(QqRateLimitDisposition.ALLOW, limiter.tryAcquire("member-b", rule, "b1").disposition)
        now = 100
        assertEquals(QqRateLimitDisposition.ALLOW, limiter.tryAcquire("member-a", rule, "a3").disposition)
    }

    @Test
    fun stashStrategyReplaysInFifoOrderAndBoundsPendingMessages() {
        var now = 0L
        val limiter = QqRateLimiter<String>(nowMillis = { now })
        val rule = QqRateLimitRule(
            windowMillis = 100,
            maxMessages = 1,
            strategy = QqRateLimitStrategy.STASH,
            maxStashedMessages = 2,
            maxStashAgeMillis = 1_000,
        )

        assertEquals(QqRateLimitDisposition.ALLOW, limiter.tryAcquire("member-a", rule, "first").disposition)
        assertEquals(QqRateLimitDisposition.STASHED, limiter.tryAcquire("member-a", rule, "second").disposition)
        assertEquals(QqRateLimitDisposition.STASHED, limiter.tryAcquire("member-a", rule, "third").disposition)
        assertEquals(QqRateLimitDisposition.DROPPED, limiter.tryAcquire("member-a", rule, "fourth").disposition)

        now = 100
        val replay = limiter.tryAcquire("member-a", rule, "fourth").also {
            assertEquals(QqRateLimitDisposition.STASHED, it.disposition)
        }
        assertEquals(listOf("second"), replay.readyToReplay)
        assertTrue(replay.discarded.isEmpty())
        now = 200
        assertEquals(listOf("third"), limiter.releaseReady("member-a", rule).readyToReplay)
        now = 300
        assertEquals(listOf("fourth"), limiter.releaseReady("member-a", rule).readyToReplay)
    }

    @Test
    fun expiredStashedMessagesAreDiscardedWithoutBypassingWindow() {
        var now = 0L
        val limiter = QqRateLimiter<String>(nowMillis = { now })
        val rule = QqRateLimitRule(
            windowMillis = 100,
            maxMessages = 1,
            strategy = QqRateLimitStrategy.STASH,
            maxStashedMessages = 1,
            maxStashAgeMillis = 50,
        )

        limiter.tryAcquire("member-a", rule, "first")
        limiter.tryAcquire("member-a", rule, "expired")
        now = 60
        val release = limiter.releaseReady("member-a", rule)
        assertTrue(release.readyToReplay.isEmpty())
        assertEquals(listOf("expired"), release.discarded)
        now = 100
        assertTrue(limiter.releaseReady("member-a", rule).readyToReplay.isEmpty())
    }

    @Test
    fun nullCurrentRuleDrainsPendingMessagesWithoutRateLimit() {
        val limiter = QqRateLimiter<String>(nowMillis = { 0L })
        val rule = QqRateLimitRule(
            windowMillis = 100,
            maxMessages = 1,
            strategy = QqRateLimitStrategy.STASH,
            maxStashedMessages = 1,
            maxStashAgeMillis = 1_000,
        )
        limiter.tryAcquire("member-a", rule, "first")
        limiter.tryAcquire("member-a", rule, "pending")

        val release = limiter.releaseReady("member-a", null)

        assertEquals(listOf("pending"), release.readyToReplay)
        assertTrue(release.discarded.isEmpty())
    }
}
