package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.PluginEntrypoint
import com.elymbot.core.contract.PluginHandle
import com.elymbot.core.contract.PluginHostContext
import com.elymbot.core.contract.ScheduledTask
import com.elymbot.core.contract.ScheduledTaskAccessContext
import com.elymbot.core.contract.ScheduledTaskAccessOrigin
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionKind
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskConfirmationSummary
import com.elymbot.core.contract.ScheduledTaskCreationSource
import com.elymbot.core.contract.ScheduledTaskDraft as ApiTaskDraft
import com.elymbot.core.contract.ScheduledTaskEndCondition as ApiEndCondition
import com.elymbot.core.contract.ScheduledTaskRejection
import com.elymbot.core.contract.ScheduledTaskRepeatRule as ApiRepeatRule
import com.elymbot.core.contract.ScheduledTaskRunFailureKind as ApiRunFailureKind
import com.elymbot.core.contract.ScheduledTaskRunOrigin as ApiRunOrigin
import com.elymbot.core.contract.ScheduledTaskRunRecord as ApiRunRecord
import com.elymbot.core.contract.ScheduledTaskRunRequest as ApiRunRequest
import com.elymbot.core.contract.ScheduledTaskRunReceipt as ApiRunReceipt
import com.elymbot.core.contract.ScheduledTaskRunStatus as ApiRunStatus
import com.elymbot.core.contract.ScheduledTaskSnapshot
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ScheduledTaskUiState
import com.elymbot.core.contract.ScheduledTaskUiStateResult
import com.elymbot.core.contract.ScheduledTasksPluginHandle
import com.elymbot.core.contract.ScheduledTasksUiHandle
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Public no-argument entrypoint declared by the signed plugin package manifest. */
class ScheduledTasksPluginEntrypoint : PluginEntrypoint {
    override fun prepare(context: PluginHostContext): PluginHandle = ScheduledTasksPluginHandleImpl(
        repository = FileScheduledTaskRepository(context.dataDirectory),
        installationIdentity = context.installationIdentity,
    )
}

internal class ScheduledTasksPluginHandleImpl(
    repository: ScheduledTaskRepository,
    private val installationIdentity: ToolInstallationIdentity,
    private val clock: Clock = Clock.systemUTC(),
    confirmationTtl: Duration = Duration.ofMinutes(5),
) : ScheduledTasksPluginHandle, ScheduledTasksUiHandle {
    private val service = ScheduledTasksService(repository, installationIdentity, clock, confirmationTtl)
    private val scheduler = ScheduledTaskScheduler(repository, installationIdentity)

    override val toolSources: List<ToolSource> = listOf(ScheduledTasksToolSource(this))

    override suspend fun snapshot(
        access: ScheduledTaskAccessContext,
        taskId: String?,
    ): ScheduledTaskSnapshotResult {
        accessRejection(access, requireUi = false)?.let { return ScheduledTaskSnapshotResult.Rejected(it) }
        return try {
            withContext(Dispatchers.IO) {
                service.snapshot(taskId, clock.instant())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScheduledTaskSnapshotResult.Rejected(ScheduledTaskRejection.STORE_UNAVAILABLE)
        }
    }

    override fun observeMainUiState(
        access: ScheduledTaskAccessContext,
        taskId: String?,
    ): Flow<ScheduledTaskUiStateResult> = service.observeMainUiState(access, taskId).flowOn(Dispatchers.IO)

    override suspend fun skipFirstTaskGuide(
        access: ScheduledTaskAccessContext,
    ): ScheduledTaskUiStateResult {
        accessRejection(access, requireUi = true)?.let { return ScheduledTaskUiStateResult.Rejected(it) }
        return try {
            withContext(Dispatchers.IO) {
                service.skipFirstTaskGuide(access, clock.instant())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScheduledTaskUiStateResult.Rejected(ScheduledTaskRejection.STORE_UNAVAILABLE)
        }
    }

    override suspend fun requestAction(
        access: ScheduledTaskAccessContext,
        requestId: String,
        action: ScheduledTaskAction,
    ): ScheduledTaskActionResult {
        accessRejection(access, requireUi = false)?.let { return ScheduledTaskActionResult.Rejected(it) }
        return try {
            if (access.origin == ScheduledTaskAccessOrigin.APP_AGENT_MAIN) {
                withContext(Dispatchers.IO) {
                    service.propose(access, requestId, action, clock.instant())
                }
            } else {
                withContext(Dispatchers.IO) {
                    service.apply(requestId, action, TaskCreationSource.APP, clock.instant())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.STORE_UNAVAILABLE)
        }
    }

    override suspend fun confirmAction(
        accessNow: ScheduledTaskAccessContext,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
    ): ScheduledTaskActionResult {
        accessRejection(accessNow, requireUi = true)?.let { return ScheduledTaskActionResult.Rejected(it) }
        return try {
            withContext(Dispatchers.IO) {
                service.confirm(accessNow, confirmationId, initiatingExecutionId, approved, clock.instant())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.STORE_UNAVAILABLE)
        }
    }

    override suspend fun nextWakeAt(
        now: Instant,
        activeIdentity: ToolInstallationIdentity,
    ): Instant? = withContext(Dispatchers.IO) {
        scheduler.nextWakeAt(now, activeIdentity)
    }

    override suspend fun collectPendingRuns(
        now: Instant,
        activeIdentity: ToolInstallationIdentity,
    ): List<ApiRunRequest> = withContext(Dispatchers.IO) {
        scheduler.collectPendingRuns(now, activeIdentity).map(TaskRunRequest::toApi)
    }

    override suspend fun recordRunReceipt(
        activeIdentity: ToolInstallationIdentity,
        receipt: ApiRunReceipt,
    ): ApiRunRecord? = withContext(Dispatchers.IO) {
        scheduler.recordRunReceipt(activeIdentity, receipt)
    }

    override fun close() {
        service.close()
    }

    private fun accessRejection(
        access: ScheduledTaskAccessContext,
        requireUi: Boolean,
    ): ScheduledTaskRejection? {
        if (service.isClosed()) return ScheduledTaskRejection.INACTIVE_INSTALLATION
        if (access.installationIdentity != installationIdentity) {
            return ScheduledTaskRejection.INACTIVE_INSTALLATION
        }
        if (access.sessionId.isBlank() || access.userId.isBlank() ||
            access.sessionScope != ToolScope.MAIN_SESSION
        ) {
            return ScheduledTaskRejection.UNAUTHORIZED
        }
        return when (access.origin) {
            ScheduledTaskAccessOrigin.APP_UI_MAIN -> null
            ScheduledTaskAccessOrigin.APP_AGENT_MAIN -> {
                if (requireUi || access.executionId.isNullOrBlank()) {
                    ScheduledTaskRejection.UNAUTHORIZED
                } else {
                    null
                }
            }
            ScheduledTaskAccessOrigin.OTHER -> ScheduledTaskRejection.UNAUTHORIZED
        }
    }
}

private class ScheduledTasksService(
    private val repository: ScheduledTaskRepository,
    private val installationIdentity: ToolInstallationIdentity,
    private val clock: Clock,
    private val confirmationTtl: Duration,
) {
    private val pendingMonitor = Any()
    private val pendingById = linkedMapOf<String, PendingTaskAction>()
    private val pendingByRequestId = mutableMapOf<String, String>()
    private val uiRevision = MutableStateFlow(0L)
    @Volatile
    private var closed = false

    fun isClosed(): Boolean = closed

    fun observeMainUiState(
        access: ScheduledTaskAccessContext,
        taskId: String?,
    ): Flow<ScheduledTaskUiStateResult> = flow {
        uiAccessRejection(access)?.let { reason ->
            emit(ScheduledTaskUiStateResult.Rejected(reason))
            return@flow
        }

        while (currentCoroutineContext().isActive) {
            if (closed) {
                emit(ScheduledTaskUiStateResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION))
                return@flow
            }
            val beforeDataRevision = repository.dataRevision.value
            val beforeUiRevision = uiRevision.value
            val now = clock.instant()
            val result = try {
                uiState(access, taskId, now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ScheduledTaskUiStateResult.Rejected(ScheduledTaskRejection.STORE_UNAVAILABLE)
            }
            val afterDataRevision = repository.dataRevision.value
            val afterUiRevision = uiRevision.value
            emit(
                if (result is ScheduledTaskUiStateResult.Visible) {
                    ScheduledTaskUiStateResult.Visible(result.state.copy(dataRevision = afterDataRevision))
                } else {
                    result
                },
            )
            if (result is ScheduledTaskUiStateResult.Rejected) return@flow
            if (closed || beforeDataRevision != afterDataRevision || beforeUiRevision != afterUiRevision) continue

            val nextExpiry = nextConfirmationExpiry()
            if (nextExpiry == null) {
                awaitUiChange(afterDataRevision, afterUiRevision)
            } else {
                val timeoutMillis = Duration.between(clock.instant(), nextExpiry).toMillis().coerceAtLeast(0L)
                withTimeoutOrNull(timeoutMillis) {
                    awaitUiChange(afterDataRevision, afterUiRevision)
                }
            }
        }
    }

    fun skipFirstTaskGuide(
        access: ScheduledTaskAccessContext,
        now: Instant,
    ): ScheduledTaskUiStateResult {
        repository.transact { state ->
            val nextState = if (state.guideSkipped) state else state.copy(guideSkipped = true)
            nextState to Unit
        }
        return uiState(access, taskId = null, now)
    }

    fun snapshot(
        taskId: String?,
        now: Instant,
    ): ScheduledTaskSnapshotResult {
        return snapshot(repository.snapshot(), taskId, now)
    }

    private fun snapshot(
        state: TaskStoreState,
        taskId: String?,
        now: Instant,
    ): ScheduledTaskSnapshotResult {
        if (taskId != null && taskId !in state.tasks) {
            return ScheduledTaskSnapshotResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
        }
        val tasks = state.tasks.values.asSequence()
            .sortedBy { it.createdAt }
            .map { task -> task.toApi(state, now) }
            .toList()
        val runs = if (taskId == null) {
            emptyList()
        } else {
            state.runs.values.asSequence()
                .filter { it.taskId == taskId }
                .sortedByDescending(TaskRunRecord::triggeredAt)
                .take(RUN_HISTORY_LIMIT)
                .map { run -> run.toApi() }
                .toList()
        }
        return ScheduledTaskSnapshotResult.Visible(
            ScheduledTaskSnapshot(capturedAt = now, tasks = tasks, runs = runs),
        )
    }

    private fun uiState(
        access: ScheduledTaskAccessContext,
        taskId: String?,
        now: Instant,
    ): ScheduledTaskUiStateResult {
        val state = repository.snapshot()
        val snapshot = when (val result = snapshot(state, taskId, now)) {
            is ScheduledTaskSnapshotResult.Visible -> result.snapshot
            is ScheduledTaskSnapshotResult.Rejected -> return ScheduledTaskUiStateResult.Rejected(result.reason)
        }
        val pending = synchronized(pendingMonitor) {
            discardExpired(now)
            pendingById.values.asSequence()
                .filter { it.sessionId == access.sessionId && it.userId == access.userId &&
                    it.installationIdentity == installationIdentity
                }
                .sortedBy(PendingTaskAction::createdAt)
                .map { it.asResult() }
                .toList()
        }
        return ScheduledTaskUiStateResult.Visible(
            ScheduledTaskUiState(
                snapshot = snapshot,
                pendingConfirmations = pending,
                firstTaskCreated = state.firstTaskCreated,
                guideSkipped = state.guideSkipped,
                dataRevision = repository.dataRevision.value,
            ),
        )
    }

    private fun uiAccessRejection(access: ScheduledTaskAccessContext): ScheduledTaskRejection? = when {
        closed -> ScheduledTaskRejection.INACTIVE_INSTALLATION
        access.installationIdentity != installationIdentity -> ScheduledTaskRejection.INACTIVE_INSTALLATION
        access.origin != ScheduledTaskAccessOrigin.APP_UI_MAIN -> ScheduledTaskRejection.UNAUTHORIZED
        access.sessionId.isBlank() || access.userId.isBlank() || access.sessionScope != ToolScope.MAIN_SESSION ->
            ScheduledTaskRejection.UNAUTHORIZED
        else -> null
    }

    private fun nextConfirmationExpiry(): Instant? = synchronized(pendingMonitor) {
        pendingById.values.minOfOrNull(PendingTaskAction::expiresAt)
    }

    private suspend fun awaitUiChange(dataRevision: Long, observedUiRevision: Long) {
        combine(repository.dataRevision, uiRevision) { data, ui -> data to ui }
            .first { (data, ui) -> data != dataRevision || ui != observedUiRevision }
    }

    fun propose(
        access: ScheduledTaskAccessContext,
        requestId: String,
        action: ScheduledTaskAction,
        now: Instant,
    ): ScheduledTaskActionResult {
        if (closed) return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION)
        if (requestId.isBlank() || requestId.length > MAX_REQUEST_ID_LENGTH) {
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INVALID_INPUT)
        }
        val executionId = access.executionId
            ?: return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.UNAUTHORIZED)
        val fingerprint = action.fingerprint()
        synchronized(pendingMonitor) {
            discardExpired(now)
            val state = repository.snapshot()
            state.mutationReceipts[requestId]?.let { receipt ->
                return resultFromReceipt(state, receipt, fingerprint)
            }
            pendingByRequestId[requestId]?.let { confirmationId ->
                val existing = pendingById[confirmationId]
                if (existing != null) {
                    return if (existing.fingerprint == fingerprint) {
                        existing.asResult()
                    } else {
                        ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.IDEMPOTENCY_CONFLICT)
                    }
                }
            }
            validateActionForProposal(action, now)?.let { reason ->
                return ScheduledTaskActionResult.Rejected(reason)
            }
            if (pendingById.size >= MAX_PENDING_CONFIRMATIONS) {
                val oldest = pendingById.values.minByOrNull(PendingTaskAction::createdAt)
                oldest?.let(::removePending)
            }
            val confirmationId = UUID.randomUUID().toString()
            val pending = PendingTaskAction(
                confirmationId = confirmationId,
                requestId = requestId,
                action = action,
                fingerprint = fingerprint,
                sessionId = access.sessionId,
                userId = access.userId,
                installationIdentity = access.installationIdentity,
                initiatingExecutionId = executionId,
                summary = action.toSummary(state),
                createdAt = now,
                expiresAt = now.plus(confirmationTtl),
            )
            pendingById[confirmationId] = pending
            pendingByRequestId[requestId] = confirmationId
            uiRevision.value += 1L
            return pending.asResult()
        }
    }

    fun confirm(
        accessNow: ScheduledTaskAccessContext,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
        now: Instant,
    ): ScheduledTaskActionResult {
        if (closed) return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION)
        val pending = synchronized(pendingMonitor) {
            discardExpired(now)
            pendingById[confirmationId]
        }
        if (pending == null) return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.CONFIRMATION_EXPIRED)
        if (pending.expiresAt <= now) {
            synchronized(pendingMonitor) { removePending(pending) }
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.CONFIRMATION_EXPIRED)
        }
        if (accessNow.origin != ScheduledTaskAccessOrigin.APP_UI_MAIN ||
            accessNow.sessionScope != ToolScope.MAIN_SESSION ||
            accessNow.sessionId != pending.sessionId ||
            accessNow.userId != pending.userId ||
            accessNow.installationIdentity != pending.installationIdentity ||
            accessNow.installationIdentity != installationIdentity ||
            initiatingExecutionId != pending.initiatingExecutionId
        ) {
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.UNAUTHORIZED)
        }
        if (!approved) {
            synchronized(pendingMonitor) { removePending(pending) }
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.CONFIRMATION_DECLINED)
        }

        val result = apply(
            requestId = pending.requestId,
            action = pending.action,
            creationSource = TaskCreationSource.AGENT,
            now = now,
            expectedFingerprint = pending.fingerprint,
        )
        if (result !is ScheduledTaskActionResult.Rejected ||
            result.reason != ScheduledTaskRejection.STORE_UNAVAILABLE
        ) {
            synchronized(pendingMonitor) { removePending(pending) }
        }
        return result
    }

    fun close() {
        synchronized(pendingMonitor) {
            if (closed) return
            closed = true
            pendingById.clear()
            pendingByRequestId.clear()
            uiRevision.value += 1L
        }
    }

    fun apply(
        requestId: String,
        action: ScheduledTaskAction,
        creationSource: TaskCreationSource,
        now: Instant,
        expectedFingerprint: String? = null,
    ): ScheduledTaskActionResult {
        if (requestId.isBlank() || requestId.length > MAX_REQUEST_ID_LENGTH) {
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INVALID_INPUT)
        }
        val fingerprint = action.fingerprint()
        if (expectedFingerprint != null && expectedFingerprint != fingerprint) {
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.IDEMPOTENCY_CONFLICT)
        }
        return repository.transact { state ->
            state.mutationReceipts[requestId]?.let { receipt ->
                val prior = resultFromReceipt(state, receipt, fingerprint)
                return@transact state to prior
            }

            when (action) {
                is ScheduledTaskAction.Create -> create(state, requestId, fingerprint, action.draft, creationSource, now)
                is ScheduledTaskAction.Update -> update(state, requestId, fingerprint, action, now)
                is ScheduledTaskAction.Pause -> setEnabled(state, requestId, fingerprint, action.taskId, action.expectedRevision, false, now)
                is ScheduledTaskAction.Resume -> setEnabled(state, requestId, fingerprint, action.taskId, action.expectedRevision, true, now)
                is ScheduledTaskAction.Delete -> delete(state, requestId, fingerprint, action.taskId, action.expectedRevision)
                is ScheduledTaskAction.RunNow -> runNow(state, requestId, fingerprint, action, now)
            }
        }
    }

    private fun create(
        state: TaskStoreState,
        requestId: String,
        fingerprint: String,
        draft: ApiTaskDraft,
        creationSource: TaskCreationSource,
        now: Instant,
    ): Pair<TaskStoreState, ScheduledTaskActionResult> {
        val domainDraft = draft.toDomain()
        if (TaskDraftValidator.validationError(domainDraft, now) != null) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INVALID_INPUT)
        }
        val taskId = UUID.randomUUID().toString()
        val task = domainDraft.toTask(
            id = taskId,
            revision = 1,
            enabled = true,
            eligibleFrom = now,
            creationSource = creationSource,
            createdAt = now,
            updatedAt = now,
        )
        val receipt = MutationReceipt(requestId, fingerprint, MutationResultKind.CREATED, taskId, revision = 1)
        val nextState = state.copy(
            tasks = state.tasks + (taskId to task),
            mutationReceipts = state.mutationReceipts + (requestId to receipt),
            firstTaskCreated = true,
        )
        return nextState to ScheduledTaskActionResult.Saved(task.toApi(nextState, now))
    }

    private fun update(
        state: TaskStoreState,
        requestId: String,
        fingerprint: String,
        action: ScheduledTaskAction.Update,
        now: Instant,
    ): Pair<TaskStoreState, ScheduledTaskActionResult> {
        val existing = state.tasks[action.taskId]
            ?: return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
        if (existing.revision != action.expectedRevision) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.REVISION_CONFLICT)
        }
        val domainDraft = action.draft.toDomain()
        if (TaskDraftValidator.validationError(domainDraft, now, existing) != null) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INVALID_INPUT)
        }
        val updated = domainDraft.toTask(
            id = existing.id,
            revision = existing.revision + 1,
            enabled = existing.enabled,
            eligibleFrom = now,
            creationSource = existing.creationSource,
            createdAt = existing.createdAt,
            updatedAt = now,
        )
        val nextRuns = invalidateQueuedRuns(state.runs, existing.id, updated.revision, now)
        val receipt = MutationReceipt(requestId, fingerprint, MutationResultKind.UPDATED, updated.id, updated.revision)
        val nextState = state.copy(
            tasks = state.tasks + (updated.id to updated),
            runs = nextRuns,
            mutationReceipts = state.mutationReceipts + (requestId to receipt),
        )
        return nextState to ScheduledTaskActionResult.Saved(updated.toApi(nextState, now))
    }

    private fun setEnabled(
        state: TaskStoreState,
        requestId: String,
        fingerprint: String,
        taskId: String,
        expectedRevision: Long,
        enabled: Boolean,
        now: Instant,
    ): Pair<TaskStoreState, ScheduledTaskActionResult> {
        val task = state.tasks[taskId]
            ?: return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
        if (task.revision != expectedRevision) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.REVISION_CONFLICT)
        }
        val kind = if (enabled) MutationResultKind.RESUMED else MutationResultKind.PAUSED
        if (task.enabled == enabled) {
            val receipt = MutationReceipt(requestId, fingerprint, kind, taskId, task.revision)
            val nextState = state.copy(mutationReceipts = state.mutationReceipts + (requestId to receipt))
            return nextState to ScheduledTaskActionResult.Saved(task.toApi(nextState, now))
        }
        val updated = task.copy(
            revision = task.revision + 1,
            enabled = enabled,
            eligibleFrom = now,
            updatedAt = now,
        )
        val nextRuns = invalidateQueuedRuns(state.runs, task.id, updated.revision, now)
        val receipt = MutationReceipt(requestId, fingerprint, kind, taskId, updated.revision)
        val nextState = state.copy(
            tasks = state.tasks + (taskId to updated),
            runs = nextRuns,
            mutationReceipts = state.mutationReceipts + (requestId to receipt),
        )
        return nextState to ScheduledTaskActionResult.Saved(updated.toApi(nextState, now))
    }

    private fun delete(
        state: TaskStoreState,
        requestId: String,
        fingerprint: String,
        taskId: String,
        expectedRevision: Long,
    ): Pair<TaskStoreState, ScheduledTaskActionResult> {
        val task = state.tasks[taskId]
            ?: return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
        if (task.revision != expectedRevision) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.REVISION_CONFLICT)
        }
        val receipt = MutationReceipt(requestId, fingerprint, MutationResultKind.DELETED, taskId)
        val nextState = state.copy(
            tasks = state.tasks - taskId,
            runs = state.runs.filterValues { it.taskId != taskId },
            mutationReceipts = state.mutationReceipts + (requestId to receipt),
        )
        return nextState to ScheduledTaskActionResult.Deleted(taskId)
    }

    private fun runNow(
        state: TaskStoreState,
        requestId: String,
        fingerprint: String,
        action: ScheduledTaskAction.RunNow,
        now: Instant,
    ): Pair<TaskStoreState, ScheduledTaskActionResult> {
        val task = state.tasks[action.taskId]
            ?: return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
        if (task.revision != action.expectedRevision) {
            return state to ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.REVISION_CONFLICT)
        }
        val runId = UUID.nameUUIDFromBytes(
            "${installationIdentity.pluginId}|${installationIdentity.instanceId}|manual|$requestId"
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val run = TaskRunRecord(
            taskId = task.id,
            runId = runId,
            occurrenceId = null,
            scheduleRevision = task.revision,
            plannedAt = null,
            triggeredAt = now,
            origin = TaskRunOrigin.MANUAL,
            installationIdentity = installationIdentity,
            requestId = requestId,
            status = TaskRunStatus.QUEUED,
            updatedAt = now,
        )
        val receipt = MutationReceipt(requestId, fingerprint, MutationResultKind.RUN_QUEUED, task.id, task.revision, runId)
        val nextState = state.copy(
            runs = state.runs + (runId to run),
            mutationReceipts = state.mutationReceipts + (requestId to receipt),
        )
        return nextState to ScheduledTaskActionResult.RunQueued(run.toApiRequest(task))
    }

    private fun invalidateQueuedRuns(
        runs: Map<String, TaskRunRecord>,
        taskId: String,
        currentRevision: Long,
        now: Instant,
    ): Map<String, TaskRunRecord> = runs.mapValues { (_, run) ->
        if (run.taskId == taskId && run.status == TaskRunStatus.QUEUED &&
            run.scheduleRevision != currentRevision
        ) {
            run.copy(status = TaskRunStatus.CANCELLED, updatedAt = now, failureKind = TaskRunFailureKind.TASK_CHANGED)
        } else {
            run
        }
    }

    private fun resultFromReceipt(
        state: TaskStoreState,
        receipt: MutationReceipt,
        actionFingerprint: String?,
    ): ScheduledTaskActionResult {
        if (actionFingerprint != null && actionFingerprint != receipt.fingerprint) {
            return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.IDEMPOTENCY_CONFLICT)
        }
        return when (receipt.resultKind) {
            MutationResultKind.CREATED,
            MutationResultKind.UPDATED,
            MutationResultKind.PAUSED,
            MutationResultKind.RESUMED -> state.tasks[receipt.taskId]?.let { task ->
                ScheduledTaskActionResult.Saved(task.toApi(state, clock.instant()))
            } ?: ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.TASK_NOT_FOUND)
            MutationResultKind.DELETED -> ScheduledTaskActionResult.Deleted(receipt.taskId)
            MutationResultKind.RUN_QUEUED -> {
                val run = receipt.runId?.let(state.runs::get)
                val task = state.tasks[receipt.taskId]
                if (run == null || task == null || run.scheduleRevision != task.revision) {
                    ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.REVISION_CONFLICT)
                } else {
                    ScheduledTaskActionResult.RunQueued(run.toApiRequest(task))
                }
            }
        }
    }

    private fun TaskRecord.toApi(state: TaskStoreState, now: Instant): ScheduledTask = ScheduledTask(
        id = id,
        revision = revision,
        title = title,
        prompt = prompt,
        startLocalDateTime = startLocalDateTime,
        timeZoneId = timeZoneId,
        repeatRule = repeatRule.toApi(),
        endCondition = endCondition.toApi(),
        enabled = enabled,
        nextRunAt = if (!enabled) {
            null
        } else {
            state.runs.values.firstOrNull { run ->
                run.taskId == id && run.origin == TaskRunOrigin.SCHEDULED && run.status == TaskRunStatus.QUEUED &&
                    run.scheduleRevision == revision && run.installationIdentity == installationIdentity
            }?.plannedAt ?: ScheduleCalculator.nextOccurrenceAfter(
                this,
                now,
                scheduledExecutionCount(state, id),
            )?.plannedAt
        },
        creationSource = when (creationSource) {
            TaskCreationSource.APP -> ScheduledTaskCreationSource.APP
            TaskCreationSource.AGENT -> ScheduledTaskCreationSource.AGENT
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun TaskRepeatRule.toApi(): ApiRepeatRule = when (kind) {
        TaskRepeatKind.ONCE -> ApiRepeatRule.Once
        TaskRepeatKind.DAILY -> ApiRepeatRule.Daily
        TaskRepeatKind.WEEKLY -> ApiRepeatRule.Weekly
        TaskRepeatKind.INTERVAL_HOURS -> ApiRepeatRule.IntervalHours(requireNotNull(intervalHours))
    }

    private fun ApiRepeatRule.toDomain(): TaskRepeatRule = when (this) {
        ApiRepeatRule.Once -> TaskRepeatRule(TaskRepeatKind.ONCE)
        ApiRepeatRule.Daily -> TaskRepeatRule(TaskRepeatKind.DAILY)
        ApiRepeatRule.Weekly -> TaskRepeatRule(TaskRepeatKind.WEEKLY)
        is ApiRepeatRule.IntervalHours -> TaskRepeatRule(TaskRepeatKind.INTERVAL_HOURS, hours)
    }

    private fun TaskEndCondition.toApi(): ApiEndCondition = when (kind) {
        TaskEndKind.NEVER -> ApiEndCondition.Never
        TaskEndKind.DATE -> ApiEndCondition.OnDate(requireNotNull(endDate))
        TaskEndKind.COUNT -> ApiEndCondition.AfterOccurrences(requireNotNull(occurrenceCount))
    }

    private fun ApiEndCondition.toDomain(): TaskEndCondition = when (this) {
        ApiEndCondition.Never -> TaskEndCondition(TaskEndKind.NEVER)
        is ApiEndCondition.OnDate -> TaskEndCondition(TaskEndKind.DATE, endDate = date)
        is ApiEndCondition.AfterOccurrences -> TaskEndCondition(TaskEndKind.COUNT, occurrenceCount = count)
    }

    private fun ApiTaskDraft.toDomain(): TaskDraft = TaskDraft(
        title = title.trim(),
        prompt = prompt.trim(),
        startLocalDateTime = startLocalDateTime,
        timeZoneId = timeZoneId,
        repeatRule = repeatRule.toDomain(),
        endCondition = endCondition.toDomain(),
    )

    private fun TaskDraft.toTask(
        id: String,
        revision: Long,
        enabled: Boolean,
        eligibleFrom: Instant,
        creationSource: TaskCreationSource,
        createdAt: Instant,
        updatedAt: Instant,
    ): TaskRecord = TaskRecord(
        id = id,
        revision = revision,
        title = title,
        prompt = prompt,
        startLocalDateTime = startLocalDateTime,
        timeZoneId = timeZoneId,
        repeatRule = repeatRule,
        endCondition = endCondition,
        enabled = enabled,
        eligibleFrom = eligibleFrom,
        creationSource = creationSource,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun TaskRunRecord.toApi(): ApiRunRecord = ApiRunRecord(
        taskId = taskId,
        runId = runId,
        occurrenceId = occurrenceId,
        scheduleRevision = scheduleRevision,
        plannedAt = plannedAt,
        triggeredAt = triggeredAt,
        origin = ApiRunOrigin.valueOf(origin.name),
        installationIdentity = installationIdentity,
        status = ApiRunStatus.valueOf(status.name),
        updatedAt = updatedAt,
        failureKind = failureKind?.let { ApiRunFailureKind.valueOf(it.name) },
    )

    private fun TaskRunRecord.toApiRequest(task: TaskRecord): ApiRunRequest = ApiRunRequest(
        taskId = taskId,
        runId = runId,
        occurrenceId = occurrenceId,
        scheduleRevision = scheduleRevision,
        plannedAt = plannedAt,
        triggeredAt = triggeredAt,
        title = task.title,
        prompt = task.prompt,
        timeZoneId = task.timeZoneId,
        origin = ApiRunOrigin.valueOf(origin.name),
        installationIdentity = installationIdentity,
    )

    private fun TaskRunRequest.toApi(): ApiRunRequest = ApiRunRequest(
        taskId = taskId,
        runId = runId,
        occurrenceId = occurrenceId,
        scheduleRevision = scheduleRevision,
        plannedAt = plannedAt,
        triggeredAt = triggeredAt,
        title = title,
        prompt = prompt,
        timeZoneId = timeZoneId,
        origin = ApiRunOrigin.valueOf(origin.name),
        installationIdentity = installationIdentity,
    )

    private fun ScheduledTaskAction.fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun validateActionForProposal(
        action: ScheduledTaskAction,
        now: Instant,
    ): ScheduledTaskRejection? {
        val state = repository.snapshot()
        return when (action) {
            is ScheduledTaskAction.Create -> {
                if (TaskDraftValidator.validationError(action.draft.toDomain(), now) == null) null
                else ScheduledTaskRejection.INVALID_INPUT
            }
            is ScheduledTaskAction.Update -> {
                val task = state.tasks[action.taskId]
                    ?: return ScheduledTaskRejection.TASK_NOT_FOUND
                if (task.revision != action.expectedRevision) return ScheduledTaskRejection.REVISION_CONFLICT
                if (TaskDraftValidator.validationError(action.draft.toDomain(), now, task) == null) null
                else ScheduledTaskRejection.INVALID_INPUT
            }
            is ScheduledTaskAction.Pause -> validateTaskRevision(state, action.taskId, action.expectedRevision)
            is ScheduledTaskAction.Resume -> validateTaskRevision(state, action.taskId, action.expectedRevision)
            is ScheduledTaskAction.Delete -> validateTaskRevision(state, action.taskId, action.expectedRevision)
            is ScheduledTaskAction.RunNow -> validateTaskRevision(state, action.taskId, action.expectedRevision)
        }
    }

    private fun validateTaskRevision(
        state: TaskStoreState,
        taskId: String,
        expectedRevision: Long,
    ): ScheduledTaskRejection? {
        val task = state.tasks[taskId] ?: return ScheduledTaskRejection.TASK_NOT_FOUND
        return if (task.revision == expectedRevision) null else ScheduledTaskRejection.REVISION_CONFLICT
    }

    private fun ScheduledTaskAction.toSummary(state: TaskStoreState): ScheduledTaskConfirmationSummary {
        val task = when (this) {
            is ScheduledTaskAction.Create -> null
            is ScheduledTaskAction.Update -> state.tasks[taskId]
            is ScheduledTaskAction.Pause -> state.tasks[taskId]
            is ScheduledTaskAction.Resume -> state.tasks[taskId]
            is ScheduledTaskAction.Delete -> state.tasks[taskId]
            is ScheduledTaskAction.RunNow -> state.tasks[taskId]
        }
        val title = when (this) {
            is ScheduledTaskAction.Create -> draft.title.trim()
            is ScheduledTaskAction.Update -> draft.title.trim()
            else -> task?.title.orEmpty()
        }
        return ScheduledTaskConfirmationSummary(kind(), title)
    }

    private fun ScheduledTaskAction.kind(): ScheduledTaskActionKind = when (this) {
        is ScheduledTaskAction.Create -> ScheduledTaskActionKind.CREATE
        is ScheduledTaskAction.Update -> ScheduledTaskActionKind.UPDATE
        is ScheduledTaskAction.Pause -> ScheduledTaskActionKind.PAUSE
        is ScheduledTaskAction.Resume -> ScheduledTaskActionKind.RESUME
        is ScheduledTaskAction.Delete -> ScheduledTaskActionKind.DELETE
        is ScheduledTaskAction.RunNow -> ScheduledTaskActionKind.RUN_NOW
    }

    private fun PendingTaskAction.asResult(): ScheduledTaskActionResult.ConfirmationRequired =
        ScheduledTaskActionResult.ConfirmationRequired(
            confirmationId = confirmationId,
            requestId = requestId,
            initiatingExecutionId = initiatingExecutionId,
            requestedAction = action,
            summary = summary,
            expiresAt = expiresAt,
        )

    private fun discardExpired(now: Instant) {
        val expired = pendingById.values.filter { it.expiresAt <= now }
        expired.forEach(::removePending)
    }

    private fun removePending(pending: PendingTaskAction) {
        val removed = pendingById.remove(pending.confirmationId) != null
        if (pendingByRequestId[pending.requestId] == pending.confirmationId) {
            pendingByRequestId.remove(pending.requestId)
        }
        if (removed) uiRevision.value += 1L
    }

    private fun scheduledExecutionCount(state: TaskStoreState, taskId: String): Int =
        state.runs.values.count { run ->
            run.taskId == taskId && run.origin == TaskRunOrigin.SCHEDULED &&
                run.status != TaskRunStatus.MISSED && run.status != TaskRunStatus.STALE &&
                (run.status != TaskRunStatus.QUEUED || run.installationIdentity == installationIdentity)
        }

    private data class PendingTaskAction(
        val confirmationId: String,
        val requestId: String,
        val action: ScheduledTaskAction,
        val fingerprint: String,
        val sessionId: String,
        val userId: String,
        val installationIdentity: ToolInstallationIdentity,
        val initiatingExecutionId: String,
        val summary: ScheduledTaskConfirmationSummary,
        val createdAt: Instant,
        val expiresAt: Instant,
    )

    private companion object {
        const val RUN_HISTORY_LIMIT = 6
        const val MAX_REQUEST_ID_LENGTH = 256
        const val MAX_PENDING_CONFIRMATIONS = 100
    }
}

private class ScheduledTasksToolSource(
    private val handle: ScheduledTasksPluginHandle,
) : ToolSource {
    override val definition = ToolDefinition(
        id = "elymbot.scheduled-tasks",
        name = "ScheduledTasks",
        description = "View and manage scheduled tasks for the App main session. Changes and immediate runs require user confirmation.",
        parametersJsonSchema = PARAMETERS_SCHEMA,
        scopes = setOf(ToolScope.MAIN_SESSION),
    )

    override suspend fun execute(
        call: ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (ToolProgress) -> Unit,
    ): ToolExecutionResult {
        val access = context.toTaskAccessContext()
            ?: return rejected(call.callId, ScheduledTaskRejection.UNAUTHORIZED)
        val args = runCatching { Json.parseToJsonElement(call.argumentsJson).jsonObject }.getOrNull()
            ?: return rejected(call.callId, ScheduledTaskRejection.INVALID_INPUT)
        val actionName = args.string("action")
            ?: return rejected(call.callId, ScheduledTaskRejection.INVALID_INPUT)
        onProgress(ToolProgress("Checking scheduled tasks"))
        if (actionName == "list") {
            return when (val result = handle.snapshot(access, args.string("taskId"))) {
                is ScheduledTaskSnapshotResult.Visible -> ToolExecutionResult(
                    callId = call.callId,
                    status = ToolResultStatus.SUCCESS,
                    content = result.snapshot.toJson().toString(),
                )
                is ScheduledTaskSnapshotResult.Rejected -> rejected(call.callId, result.reason)
            }
        }

        val action = runCatching { args.toAction() }.getOrNull()
            ?: return rejected(call.callId, ScheduledTaskRejection.INVALID_INPUT)
        val requestId = listOf(
            context.executionId,
            context.attemptId,
            context.toolStepOrdinal.toString(),
            context.toolCallOrdinal.toString(),
            call.callId,
        ).joinToString(":")
        return when (val result = handle.requestAction(access, requestId, action)) {
            is ScheduledTaskActionResult.Rejected -> rejected(call.callId, result.reason)
            else -> ToolExecutionResult(
                callId = call.callId,
                status = ToolResultStatus.SUCCESS,
                content = result.toJson().toString(),
            )
        }
    }

    private fun ToolExecutionContext.toTaskAccessContext(): ScheduledTaskAccessContext? {
        val message = inputMessage ?: return null
        val identity = installationIdentity ?: return null
        val isAppMain = scope == ToolScope.MAIN_SESSION &&
            message.origin.channelId == APP_CHANNEL_ID && message.origin.actorId == APP_USER_ID
        return ScheduledTaskAccessContext(
            origin = if (isAppMain) ScheduledTaskAccessOrigin.APP_AGENT_MAIN else ScheduledTaskAccessOrigin.OTHER,
            sessionId = sessionId,
            userId = message.origin.actorId.orEmpty(),
            sessionScope = scope,
            installationIdentity = identity,
            executionId = executionId,
        )
    }

    private fun JsonObject.toAction(): ScheduledTaskAction? = when (string("action")) {
        "create" -> string("title")?.let { title ->
            string("prompt")?.let { prompt ->
                taskDraft(title, prompt)?.let { draft -> ScheduledTaskAction.Create(draft) }
            }
        }
        "update" -> string("taskId")?.let { taskId ->
            long("expectedRevision")?.let { revision ->
                string("title")?.let { title ->
                    string("prompt")?.let { prompt ->
                        taskDraft(title, prompt)?.let { draft -> ScheduledTaskAction.Update(taskId, revision, draft) }
                    }
                }
            }
        }
        "pause" -> string("taskId")?.let { taskId -> long("expectedRevision")?.let { ScheduledTaskAction.Pause(taskId, it) } }
        "resume" -> string("taskId")?.let { taskId -> long("expectedRevision")?.let { ScheduledTaskAction.Resume(taskId, it) } }
        "delete" -> string("taskId")?.let { taskId -> long("expectedRevision")?.let { ScheduledTaskAction.Delete(taskId, it) } }
        "run_now" -> string("taskId")?.let { taskId -> long("expectedRevision")?.let { ScheduledTaskAction.RunNow(taskId, it) } }
        else -> null
    }

    private fun JsonObject.taskDraft(title: String, prompt: String): ApiTaskDraft? {
        val start = string("startLocalDateTime")?.let { value ->
            runCatching { LocalDateTime.parse(value) }.getOrNull()
        } ?: return null
        val zone = string("timeZoneId") ?: return null
        runCatching { ZoneId.of(zone) }.getOrNull() ?: return null
        val repeat = this["repeatRule"]?.jsonObject?.toRepeatRule() ?: return null
        val end = this["endCondition"]?.jsonObject?.toEndCondition() ?: return null
        return ApiTaskDraft(title, prompt, start, zone, repeat, end)
    }

    private fun JsonObject.toRepeatRule(): ApiRepeatRule? = when (string("kind")) {
        "ONCE" -> ApiRepeatRule.Once
        "DAILY" -> ApiRepeatRule.Daily
        "WEEKLY" -> ApiRepeatRule.Weekly
        "INTERVAL_HOURS" -> int("hours")?.let { hours -> ApiRepeatRule.IntervalHours(hours) }
        else -> null
    }

    private fun JsonObject.toEndCondition(): ApiEndCondition? = when (string("kind")) {
        "NEVER" -> ApiEndCondition.Never
        "DATE" -> string("date")?.let { value -> runCatching { ApiEndCondition.OnDate(LocalDate.parse(value)) }.getOrNull() }
        "COUNT" -> int("count")?.let { count -> ApiEndCondition.AfterOccurrences(count) }
        else -> null
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)
        ?.takeIf { !it.isString }
        ?.intOrNull

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)
        ?.takeIf { !it.isString }
        ?.longOrNull

    private fun rejected(callId: String, reason: ScheduledTaskRejection) = ToolExecutionResult(
        callId = callId,
        status = ToolResultStatus.REJECTED,
        content = "Scheduled task request was rejected: ${reason.name}.",
    )

    private fun ScheduledTaskSnapshot.toJson() = buildJsonObject {
        put("capturedAt", capturedAt.toString())
        put("tasks", buildJsonArray { tasks.forEach { add(it.toJson()) } })
        put("runs", buildJsonArray { runs.forEach { add(it.toJson()) } })
    }

    private fun ScheduledTask.toJson() = buildJsonObject {
        put("id", id)
        put("revision", revision)
        put("title", title)
        put("prompt", prompt)
        put("startLocalDateTime", startLocalDateTime.toString())
        put("timeZoneId", timeZoneId)
        put("repeatRule", repeatRule.toString())
        put("endCondition", endCondition.toString())
        put("enabled", enabled)
        put("nextRunAt", nextRunAt?.toString())
        put("creationSource", creationSource.name)
        put("createdAt", createdAt.toString())
        put("updatedAt", updatedAt.toString())
    }

    private fun ApiRunRecord.toJson() = buildJsonObject {
        put("taskId", taskId)
        put("runId", runId)
        put("occurrenceId", occurrenceId)
        put("scheduleRevision", scheduleRevision)
        put("plannedAt", plannedAt?.toString())
        put("triggeredAt", triggeredAt.toString())
        put("origin", origin.name)
        put("status", status.name)
        put("updatedAt", updatedAt.toString())
        put("failureKind", failureKind?.name)
    }

    private fun ScheduledTaskActionResult.toJson() = when (this) {
        is ScheduledTaskActionResult.Saved -> buildJsonObject {
            put("status", "SAVED")
            put("task", task.toJson())
        }
        is ScheduledTaskActionResult.Deleted -> buildJsonObject {
            put("status", "DELETED")
            put("taskId", taskId)
        }
        is ScheduledTaskActionResult.RunQueued -> buildJsonObject {
            put("status", "RUN_QUEUED")
            put("run", run.toJson())
        }
        is ScheduledTaskActionResult.ConfirmationRequired -> buildJsonObject {
            put("status", "PENDING_CONFIRMATION")
            put("confirmationId", confirmationId)
            put("requestId", requestId)
            put("initiatingExecutionId", initiatingExecutionId)
            put("requestedAction", requestedAction.toJson())
            put("action", summary.action.name)
            put("title", summary.title)
            put("expiresAt", expiresAt.toString())
        }
        is ScheduledTaskActionResult.Rejected -> buildJsonObject {
            put("status", "REJECTED")
            put("reason", reason.name)
        }
    }

    private fun ApiRunRequest.toJson() = buildJsonObject {
        put("taskId", taskId)
        put("runId", runId)
        put("occurrenceId", occurrenceId)
        put("scheduleRevision", scheduleRevision)
        put("plannedAt", plannedAt?.toString())
        put("triggeredAt", triggeredAt.toString())
        put("title", title)
        put("prompt", prompt)
        put("timeZoneId", timeZoneId)
        put("origin", origin.name)
    }

    private fun ScheduledTaskAction.toJson() = when (this) {
        is ScheduledTaskAction.Create -> buildJsonObject {
            put("kind", "CREATE")
            put("draft", draft.toJson())
        }
        is ScheduledTaskAction.Update -> buildJsonObject {
            put("kind", "UPDATE")
            put("taskId", taskId)
            put("expectedRevision", expectedRevision)
            put("draft", draft.toJson())
        }
        is ScheduledTaskAction.Pause -> taskActionJson("PAUSE", taskId, expectedRevision)
        is ScheduledTaskAction.Resume -> taskActionJson("RESUME", taskId, expectedRevision)
        is ScheduledTaskAction.Delete -> taskActionJson("DELETE", taskId, expectedRevision)
        is ScheduledTaskAction.RunNow -> taskActionJson("RUN_NOW", taskId, expectedRevision)
    }

    private fun ApiTaskDraft.toJson() = buildJsonObject {
        put("title", title)
        put("prompt", prompt)
        put("startLocalDateTime", startLocalDateTime.toString())
        put("timeZoneId", timeZoneId)
        put("repeatRule", repeatRule.toJson())
        put("endCondition", endCondition.toJson())
    }

    private fun ApiRepeatRule.toJson() = when (this) {
        ApiRepeatRule.Once -> buildJsonObject { put("kind", "ONCE") }
        ApiRepeatRule.Daily -> buildJsonObject { put("kind", "DAILY") }
        ApiRepeatRule.Weekly -> buildJsonObject { put("kind", "WEEKLY") }
        is ApiRepeatRule.IntervalHours -> buildJsonObject {
            put("kind", "INTERVAL_HOURS")
            put("hours", hours)
        }
    }

    private fun ApiEndCondition.toJson() = when (this) {
        ApiEndCondition.Never -> buildJsonObject { put("kind", "NEVER") }
        is ApiEndCondition.OnDate -> buildJsonObject {
            put("kind", "DATE")
            put("date", date.toString())
        }
        is ApiEndCondition.AfterOccurrences -> buildJsonObject {
            put("kind", "COUNT")
            put("count", count)
        }
    }

    private fun taskActionJson(kind: String, taskId: String, expectedRevision: Long) = buildJsonObject {
        put("kind", kind)
        put("taskId", taskId)
        put("expectedRevision", expectedRevision)
    }

    private companion object {
        const val APP_CHANNEL_ID = "app"
        const val APP_USER_ID = "local-user"
        private const val PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {"type":"string","enum":["list","create","update","pause","resume","delete","run_now"]},
                "taskId": {"type":"string"},
                "expectedRevision": {"type":"integer","minimum":1},
                "title": {"type":"string","minLength":1,"maxLength":40},
                "prompt": {"type":"string","minLength":1,"maxLength":1600},
                "startLocalDateTime": {"type":"string","format":"date-time"},
                "timeZoneId": {"type":"string"},
                "repeatRule": {
                  "type":"object",
                  "properties": {
                    "kind":{"type":"string","enum":["ONCE","DAILY","WEEKLY","INTERVAL_HOURS"]},
                    "hours":{"type":"integer","minimum":1,"maximum":168}
                  },
                  "required":["kind"],"additionalProperties":false
                },
                "endCondition": {
                  "type":"object",
                  "properties": {
                    "kind":{"type":"string","enum":["NEVER","DATE","COUNT"]},
                    "date":{"type":"string","format":"date"},
                    "count":{"type":"integer","minimum":1,"maximum":999}
                  },
                  "required":["kind"],"additionalProperties":false
                }
              },
              "required":["action"],"additionalProperties":false
            }
        """
    }
}

private fun TaskRunRequest.toApi(): ApiRunRequest = ApiRunRequest(
    taskId = taskId,
    runId = runId,
    occurrenceId = occurrenceId,
    scheduleRevision = scheduleRevision,
    plannedAt = plannedAt,
    triggeredAt = triggeredAt,
    title = title,
    prompt = prompt,
    timeZoneId = timeZoneId,
    origin = ApiRunOrigin.valueOf(origin.name),
    installationIdentity = installationIdentity,
)
