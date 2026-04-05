package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.PluginFailureCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginTriggerSource

data class PluginFailurePolicy(
    val maxConsecutiveFailures: Int = 3,
    val suspensionWindowMillis: Long = 5 * 60 * 1000L,
) {
    init {
        require(maxConsecutiveFailures > 0) { "maxConsecutiveFailures must be greater than zero." }
        require(suspensionWindowMillis >= 0L) { "suspensionWindowMillis must not be negative." }
    }
}

data class PluginFailureSnapshot(
    val pluginId: String,
    val trigger: PluginTriggerSource? = null,
    val consecutiveFailureCount: Int = 0,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
    val failureCategory: PluginFailureCategory = PluginFailureCategory.Unknown,
    val isSuspended: Boolean = false,
    val suspendedUntilEpochMillis: Long? = null,
)

interface PluginFailureStateStore {
    fun get(pluginId: String): PluginFailureSnapshot?

    fun put(snapshot: PluginFailureSnapshot)

    fun remove(pluginId: String)
}

class InMemoryPluginFailureStateStore : PluginFailureStateStore {
    private val states = linkedMapOf<String, PluginFailureSnapshot>()

    override fun get(pluginId: String): PluginFailureSnapshot? = states[pluginId]

    override fun put(snapshot: PluginFailureSnapshot) {
        states[snapshot.pluginId] = snapshot
    }

    override fun remove(pluginId: String) {
        states.remove(pluginId)
    }
}

class PersistentPluginFailureStateStore(
    private val findState: (String) -> PluginFailureState? = { pluginId ->
        repositoryFailureStateOrNull(pluginId)
    },
    private val updateState: (String, PluginFailureState) -> Unit = { pluginId, failureState ->
        if (repositoryFailureStateOrNull(pluginId) != null) {
            PluginRepository.updateFailureState(pluginId, failureState)
        }
    },
    private val clearState: (String) -> Unit = { pluginId ->
        if (repositoryFailureStateOrNull(pluginId) != null) {
            PluginRepository.clearFailureState(pluginId)
        }
    },
) : PluginFailureStateStore {
    override fun get(pluginId: String): PluginFailureSnapshot? {
        return findState(pluginId)
            ?.takeIf { it.hasFailures }
            ?.toSnapshot(pluginId)
    }

    override fun put(snapshot: PluginFailureSnapshot) {
        val failureState = snapshot.toFailureState()
        if (failureState.hasFailures) {
            updateState(snapshot.pluginId, failureState)
        } else {
            clearState(snapshot.pluginId)
        }
    }

    override fun remove(pluginId: String) {
        clearState(pluginId)
    }
}

object PluginRuntimeFailureStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginFailureStateStore? = null

    private val persistentStore: PluginFailureStateStore by lazy {
        PersistentPluginFailureStateStore()
    }

    fun store(): PluginFailureStateStore = storeOverrideForTests ?: persistentStore

    internal fun setStoreOverrideForTests(store: PluginFailureStateStore?) {
        storeOverrideForTests = store
    }
}

interface PluginScopedFailureStateStore {
    fun get(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot?

    fun put(snapshot: PluginFailureSnapshot)

    fun remove(
        pluginId: String,
        trigger: PluginTriggerSource,
    )
}

class InMemoryPluginScopedFailureStateStore : PluginScopedFailureStateStore {
    private val states = linkedMapOf<String, PluginFailureSnapshot>()

    override fun get(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot? = states[key(pluginId, trigger)]

    override fun put(snapshot: PluginFailureSnapshot) {
        val trigger = snapshot.trigger ?: return
        states[key(snapshot.pluginId, trigger)] = snapshot
    }

    override fun remove(
        pluginId: String,
        trigger: PluginTriggerSource,
    ) {
        states.remove(key(pluginId, trigger))
    }

    private fun key(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): String = "$pluginId#${trigger.wireValue}"
}

object PluginRuntimeScopedFailureStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginScopedFailureStateStore? = null

    private val sharedStore: PluginScopedFailureStateStore by lazy {
        InMemoryPluginScopedFailureStateStore()
    }

    fun store(): PluginScopedFailureStateStore = storeOverrideForTests ?: sharedStore

    internal fun setStoreOverrideForTests(store: PluginScopedFailureStateStore?) {
        storeOverrideForTests = store
    }
}

class PluginFailureGuard(
    private val store: PluginFailureStateStore = InMemoryPluginFailureStateStore(),
    private val scopedStore: PluginScopedFailureStateStore = PluginRuntimeScopedFailureStateStoreProvider.store(),
    private val policy: PluginFailurePolicy = PluginFailurePolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
) {
    fun snapshot(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): PluginFailureSnapshot {
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        }
        return current ?: PluginFailureSnapshot(
            pluginId = pluginId,
            trigger = trigger,
        )
    }

    fun isSuspended(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): Boolean = snapshot(pluginId, trigger).isSuspended

    fun recordFailure(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
        errorSummary: String = "",
    ): PluginFailureSnapshot {
        val now = clock()
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        } ?: PluginFailureSnapshot(pluginId = pluginId, trigger = trigger)
        val category = classifyFailure(errorSummary.ifBlank { current.lastErrorSummary })
        val snapshot = evolveFailureSnapshot(
            current = current,
            pluginId = pluginId,
            trigger = trigger,
            errorSummary = errorSummary,
            category = category,
            now = now,
        )
        saveSnapshot(snapshot)
        if (trigger != null) {
            saveAggregateFailure(
                pluginId = pluginId,
                errorSummary = errorSummary,
                category = category,
                now = now,
            )
        }
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = now,
                pluginId = pluginId,
                trigger = trigger,
                category = PluginRuntimeLogCategory.FailureGuard,
                level = if (snapshot.isSuspended) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Warning,
                code = if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
                message = errorSummary.ifBlank { "Plugin failure recorded." },
                succeeded = false,
                metadata = mapOf(
                    "consecutiveFailureCount" to snapshot.consecutiveFailureCount.toString(),
                    "failureCategory" to category.wireValue,
                    "failureScope" to (trigger?.wireValue ?: "plugin"),
                    "suspendedUntilEpochMillis" to (snapshot.suspendedUntilEpochMillis?.toString() ?: ""),
                ),
            ),
        )
        return snapshot
    }

    fun recordSuccess(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): PluginFailureSnapshot {
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        } ?: PluginFailureSnapshot(pluginId = pluginId, trigger = trigger)
        val snapshot = current.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        saveSnapshot(snapshot)
        if (trigger != null) {
            saveAggregateRecovery(pluginId)
        }
        if (current.consecutiveFailureCount > 0 || current.isSuspended) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = pluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.FailureGuard,
                    level = PluginRuntimeLogLevel.Info,
                    code = "failure_guard_recovered",
                    message = "Plugin failure state recovered.",
                    succeeded = true,
                    metadata = mapOf(
                        "failureCategory" to current.failureCategory.wireValue,
                        "failureScope" to (trigger?.wireValue ?: "plugin"),
                        "previousConsecutiveFailureCount" to current.consecutiveFailureCount.toString(),
                    ),
                ),
            )
        }
        return snapshot
    }

    fun reset(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ) {
        val hadState = if (trigger == null) {
            store.get(pluginId) != null
        } else {
            scopedStore.get(pluginId, trigger) != null
        }
        removeSnapshot(pluginId, trigger)
        if (hadState) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = pluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.FailureGuard,
                    level = PluginRuntimeLogLevel.Info,
                    code = "failure_guard_reset",
                    message = "Plugin failure state reset.",
                    succeeded = true,
                ),
            )
        }
    }

    private fun resolve(pluginId: String): PluginFailureSnapshot? {
        val snapshot = store.get(pluginId) ?: return null
        return normalizeRecoveredSnapshot(
            snapshot = snapshot,
            save = { recovered -> store.put(recovered) },
        )
    }

    private fun resolveScoped(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot? {
        val snapshot = scopedStore.get(pluginId, trigger) ?: return null
        return normalizeRecoveredSnapshot(
            snapshot = snapshot,
            save = { recovered -> scopedStore.put(recovered) },
        )
    }

    private fun normalizeRecoveredSnapshot(
        snapshot: PluginFailureSnapshot,
        save: (PluginFailureSnapshot) -> Unit,
    ): PluginFailureSnapshot {
        if (!snapshot.isSuspended) return snapshot
        val suspendedUntil = snapshot.suspendedUntilEpochMillis ?: return snapshot
        if (clock() < suspendedUntil) return snapshot
        val recovered = snapshot.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        save(recovered)
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = snapshot.pluginId,
                trigger = snapshot.trigger,
                category = PluginRuntimeLogCategory.FailureGuard,
                level = PluginRuntimeLogLevel.Info,
                code = "failure_guard_resumed",
                message = "Plugin suspension window expired and execution resumed.",
                succeeded = true,
            ),
        )
        return recovered
    }

    private fun evolveFailureSnapshot(
        current: PluginFailureSnapshot,
        pluginId: String,
        trigger: PluginTriggerSource?,
        errorSummary: String,
        category: PluginFailureCategory,
        now: Long,
    ): PluginFailureSnapshot {
        val failureCount = current.consecutiveFailureCount + 1
        val suspended = failureCount >= policy.maxConsecutiveFailures
        return PluginFailureSnapshot(
            pluginId = pluginId,
            trigger = trigger,
            consecutiveFailureCount = failureCount,
            lastFailureAtEpochMillis = now,
            lastErrorSummary = errorSummary.ifBlank { current.lastErrorSummary },
            failureCategory = category,
            isSuspended = suspended,
            suspendedUntilEpochMillis = if (suspended) now + policy.suspensionWindowMillis else null,
        )
    }

    private fun saveSnapshot(snapshot: PluginFailureSnapshot) {
        if (snapshot.lastFailureAtEpochMillis == null) {
            removeSnapshot(snapshot.pluginId, snapshot.trigger)
            return
        }
        if (snapshot.trigger == null) {
            store.put(snapshot)
        } else {
            scopedStore.put(snapshot)
        }
    }

    private fun removeSnapshot(
        pluginId: String,
        trigger: PluginTriggerSource?,
    ) {
        if (trigger == null) {
            store.remove(pluginId)
        } else {
            scopedStore.remove(pluginId, trigger)
        }
    }

    private fun saveAggregateFailure(
        pluginId: String,
        errorSummary: String,
        category: PluginFailureCategory,
        now: Long,
    ) {
        val aggregate = evolveFailureSnapshot(
            current = resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId),
            pluginId = pluginId,
            trigger = null,
            errorSummary = errorSummary,
            category = category,
            now = now,
        )
        store.put(aggregate)
    }

    private fun saveAggregateRecovery(pluginId: String) {
        val current = resolve(pluginId) ?: return
        val snapshot = current.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        if (snapshot.lastFailureAtEpochMillis == null) {
            store.remove(pluginId)
        } else {
            store.put(snapshot)
        }
    }
}

private fun PluginFailureState.toSnapshot(pluginId: String): PluginFailureSnapshot {
    val suspendedUntil = suspendedUntilEpochMillis
    val category = classifyFailure(lastErrorSummary)
    return PluginFailureSnapshot(
        pluginId = pluginId,
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        failureCategory = category,
        isSuspended = suspendedUntil != null,
        suspendedUntilEpochMillis = suspendedUntil,
    )
}

private fun PluginFailureSnapshot.toFailureState(): PluginFailureState {
    return PluginFailureState(
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        suspendedUntilEpochMillis = suspendedUntilEpochMillis,
    )
}

private fun repositoryFailureStateOrNull(pluginId: String): PluginFailureState? {
    return runCatching {
        PluginRepository.findByPluginId(pluginId)?.failureState
    }.getOrNull()
}

internal fun classifyFailure(errorSummary: String): PluginFailureCategory {
    val summary = errorSummary.trim()
    if (summary.isBlank()) return PluginFailureCategory.Unknown
    val normalized = summary.lowercase()
    return when {
        "timeout" in normalized ||
            "timed out" in normalized ||
            "deadline exceeded" in normalized -> PluginFailureCategory.Timeout
        "requires granted permission" in normalized ||
            "permission denied" in normalized -> PluginFailureCategory.PermissionDenied
        "payload." in normalized ||
            "invalid payload" in normalized -> PluginFailureCategory.InvalidPayload
        "not open for v1" in normalized ||
            "not in the trigger whitelist" in normalized ||
            "not open" in normalized -> PluginFailureCategory.UnsupportedAction
        else -> PluginFailureCategory.RuntimeError
    }
}
