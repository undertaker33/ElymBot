package com.elymbot.android.plugins.qq

import java.util.ArrayDeque
import java.util.LinkedHashMap

internal enum class QqRateLimitStrategy {
    DROP,
    STASH,
}

internal data class QqRateLimitRule(
    val windowMillis: Long,
    val maxMessages: Int,
    val strategy: QqRateLimitStrategy,
    val maxStashedMessages: Int,
    val maxStashAgeMillis: Long,
) {
    init {
        require(windowMillis > 0)
        require(maxMessages > 0)
        require(maxStashedMessages >= 0)
        require(maxStashAgeMillis > 0)
        require(strategy != QqRateLimitStrategy.STASH || maxStashedMessages > 0)
    }
}

internal enum class QqRateLimitDisposition {
    ALLOW,
    STASHED,
    DROPPED,
}

/** Earlier stashed events must be handled before the current message is delivered. */
internal data class QqRateLimitDecision<T>(
    val disposition: QqRateLimitDisposition,
    val readyToReplay: List<T> = emptyList(),
    val discarded: List<T> = emptyList(),
)

internal data class QqRateLimitRelease<T>(
    val readyToReplay: List<T> = emptyList(),
    val discarded: List<T> = emptyList(),
)

/** Per-source sliding window with bounded FIFO replay and age-based discard. */
internal class QqRateLimiter<T>(
    private val nowMillis: () -> Long,
    private val maxSources: Int = DEFAULT_MAX_SOURCES,
    private val maxTotalStashedMessages: Int = DEFAULT_MAX_TOTAL_STASHED_MESSAGES,
) {
    private val lock = Any()
    private val buckets = LinkedHashMap<String, Bucket<T>>(16, 0.75f, true)

    init {
        require(maxSources > 0)
        require(maxTotalStashedMessages > 0)
    }

    fun tryAcquire(
        sourceKey: String,
        rule: QqRateLimitRule,
        payload: T,
    ): QqRateLimitDecision<T> = synchronized(lock) {
        if (sourceKey.isBlank()) return@synchronized QqRateLimitDecision(QqRateLimitDisposition.DROPPED)
        val now = nowMillis()
        val discarded = removeExpiredBuckets(now).toMutableList()
        val bucket = buckets[sourceKey] ?: run {
            if (buckets.size >= maxSources) {
                return@synchronized QqRateLimitDecision(
                    disposition = QqRateLimitDisposition.DROPPED,
                    discarded = discarded,
                )
            }
            Bucket<T>(rule.windowMillis).also { buckets[sourceKey] = it }
        }
        bucket.windowMillis = rule.windowMillis
        removeExpiredTimestamps(bucket, now, bucket.windowMillis)
        discarded += removeExpiredStash(bucket, now)

        val replay = releaseReady(bucket, now, rule)
        if (bucket.timestamps.size < rule.maxMessages) {
            bucket.timestamps.addLast(now)
            return@synchronized QqRateLimitDecision(
                disposition = QqRateLimitDisposition.ALLOW,
                readyToReplay = replay,
                discarded = discarded,
            )
        }

        if (rule.strategy == QqRateLimitStrategy.STASH &&
            bucket.stash.size < rule.maxStashedMessages &&
            totalStashedMessages() < maxTotalStashedMessages
        ) {
            bucket.stash.addLast(Stashed(now, rule.maxStashAgeMillis, payload))
            return@synchronized QqRateLimitDecision(
                disposition = QqRateLimitDisposition.STASHED,
                readyToReplay = replay,
                discarded = discarded,
            )
        }
        QqRateLimitDecision(
            disposition = QqRateLimitDisposition.DROPPED,
            readyToReplay = replay,
            discarded = discarded,
        )
    }

    /** Uses the current policy rule; a null rule drains pending work without rate limiting. */
    fun releaseReady(sourceKey: String, rule: QqRateLimitRule?): QqRateLimitRelease<T> = synchronized(lock) {
        val bucket = buckets[sourceKey] ?: return@synchronized QqRateLimitRelease()
        val now = nowMillis()
        val discarded = removeExpiredStash(bucket, now)
        val ready = if (rule == null) {
            buildList(bucket.stash.size) {
                while (bucket.stash.isNotEmpty()) add(bucket.stash.removeFirst().payload)
            }
                .also { bucket.timestamps.clear() }
        } else {
            bucket.windowMillis = rule.windowMillis
            removeExpiredTimestamps(bucket, now, bucket.windowMillis)
            releaseReady(bucket, now, rule)
        }
        removeBucketIfEmpty(sourceKey, bucket)
        QqRateLimitRelease(readyToReplay = ready, discarded = discarded)
    }

    fun stashedSourceKeys(): List<String> = synchronized(lock) {
        buckets.entries.asSequence()
            .filter { it.value.stash.isNotEmpty() }
            .map { it.key }
            .toList()
    }

    private fun removeExpiredBuckets(now: Long): List<T> {
        val discarded = mutableListOf<T>()
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val bucket = entry.value
            removeExpiredTimestamps(bucket, now, bucket.windowMillis)
            discarded += removeExpiredStash(bucket, now)
            if (bucket.timestamps.isEmpty() && bucket.stash.isEmpty()) iterator.remove()
        }
        return discarded
    }

    private fun removeBucketIfEmpty(sourceKey: String, bucket: Bucket<T>) {
        if (bucket.timestamps.isEmpty() && bucket.stash.isEmpty() && buckets[sourceKey] === bucket) {
            buckets.remove(sourceKey)
        }
    }

    private fun totalStashedMessages(): Int = buckets.values.sumOf { it.stash.size }

    private fun releaseReady(bucket: Bucket<T>, now: Long, rule: QqRateLimitRule): List<T> {
        val available = (rule.maxMessages - bucket.timestamps.size).coerceAtLeast(0)
        if (available == 0 || bucket.stash.isEmpty()) return emptyList()
        val count = minOf(available, bucket.stash.size)
        val ready = buildList(count) {
            repeat(count) { add(bucket.stash.removeFirst().payload) }
        }
        repeat(ready.size) { bucket.timestamps.addLast(now) }
        return ready
    }

    private fun removeExpiredTimestamps(bucket: Bucket<T>, now: Long, windowMillis: Long) {
        while (bucket.timestamps.isNotEmpty() && now - bucket.timestamps.first() >= windowMillis) {
            bucket.timestamps.removeFirst()
        }
    }

    private fun removeExpiredStash(bucket: Bucket<T>, now: Long): List<T> = buildList {
        while (bucket.stash.isNotEmpty() &&
            now - bucket.stash.first().createdAtMillis >= bucket.stash.first().maxAgeMillis
        ) {
            add(bucket.stash.removeFirst().payload)
        }
    }

    private class Bucket<T>(var windowMillis: Long) {
        val timestamps = ArrayDeque<Long>()
        val stash = ArrayDeque<Stashed<T>>()
    }

    private data class Stashed<T>(
        val createdAtMillis: Long,
        val maxAgeMillis: Long,
        val payload: T,
    )

    private companion object {
        const val DEFAULT_MAX_SOURCES = 256
        const val DEFAULT_MAX_TOTAL_STASHED_MESSAGES = 1_024
    }
}
