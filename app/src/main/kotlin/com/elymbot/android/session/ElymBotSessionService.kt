package com.elymbot.android.session

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.elymbot.android.R
import com.elymbot.android.data.AttachmentEntity
import com.elymbot.android.data.AppGuidePreferences
import com.elymbot.android.data.CheckpointEntity
import com.elymbot.android.data.CommandNameEntity
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.ConversationSummaryRow
import com.elymbot.android.data.CredentialEntity
import com.elymbot.android.data.CredentialVault
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.HistorySummaryEntity
import com.elymbot.android.data.MessageEntity
import com.elymbot.android.data.MessageWithAttachments
import com.elymbot.android.data.PendingAppTranscriptDelivery
import com.elymbot.android.data.ReplyDeliveryEntity
import com.elymbot.android.data.ReplyDeliveryStatus
import com.elymbot.android.data.ReplyDeliveryTargetKind
import com.elymbot.android.data.ReplyDeliveryWithSubmission
import com.elymbot.android.data.ReplyExecutionIdentity
import com.elymbot.android.data.RetiredReplyExecutionEntity
import com.elymbot.android.data.RetiredReplyReceiptEntity
import com.elymbot.android.data.RetirableReplyDelivery
import com.elymbot.android.data.ScheduledTaskRunInboxEntity
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ModelServiceEntity
import com.elymbot.android.data.PersonaEntity
import com.elymbot.android.data.PersonaTagEntity
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.data.TurnEntity
import com.elymbot.android.ui.ChatCheckpoint
import com.elymbot.android.ui.ChatCommand
import com.elymbot.android.ui.ChatDeliveryStatus
import com.elymbot.android.ui.ChatDeliveryTarget
import com.elymbot.android.ui.ChatDetachedDeliveryReceipt
import com.elymbot.android.ui.ChatPendingDelivery
import com.elymbot.android.ui.ChatMessage
import com.elymbot.android.ui.Conversation
import com.elymbot.android.ui.ConversationSummary
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.android.ui.AppGuideState
import com.elymbot.android.ui.AppGuideStatus
import com.elymbot.android.ui.ImagePosition
import com.elymbot.android.ui.ModelChoice
import com.elymbot.android.ui.ModelConfiguration
import com.elymbot.android.ui.ModelProbe
import com.elymbot.android.ui.ModelRole as UiModelRole
import com.elymbot.android.ui.ModelServiceDraft
import com.elymbot.android.ui.MessageStatus
import com.elymbot.android.ui.PersonaCard
import com.elymbot.android.ui.ProbeStatus
import com.elymbot.android.ui.UiIssue
import com.elymbot.android.ui.UiServiceException
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentFailure
import com.elymbot.core.contract.AgentFailureKind
import com.elymbot.core.contract.AgentMessage
import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.AgentRequest
import com.elymbot.core.contract.AgentRuntime
import com.elymbot.core.contract.AgentTerminalStatus
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.CommandAction
import com.elymbot.core.contract.CommandNames
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.HistorySummaryCandidate
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelProbeFailure
import com.elymbot.core.contract.ModelProbeFailureKind
import com.elymbot.core.contract.ModelProbeTarget
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelSelection
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.MediaResourceReference
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ScheduledTaskRunRecord
import com.elymbot.core.contract.ToolSource
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ReplySubmitter
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskRejection
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ScheduledTaskRunFailureKind
import com.elymbot.core.contract.ScheduledTaskRunOrigin
import com.elymbot.core.contract.ScheduledTaskRunRequest
import com.elymbot.core.contract.ScheduledTaskRunStatus
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolRegistration
import com.elymbot.core.contract.ToolAccessPolicy
import com.elymbot.core.contract.BuiltInOnlyToolAccessPolicy
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ElymBotSessionService internal constructor(
    context: Context,
    private val repository: ElymBotDataRepository,
    private val connectionProvider: RoomModelServiceConnectionProvider,
    private val modelGateway: ModelGateway,
    private val agentRuntime: AgentRuntime,
    private val commandParser: SlashCommandParser,
    private val toolSources: () -> List<ToolSource> = { emptyList() },
    private val commandText: CommandTextProvider = AndroidCommandTextProvider(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val replyTargetSnapshotProvider: ReplyTargetSnapshotProvider = ReplyTargetSnapshotProvider {
        ReplyTargetSnapshot(
            targets = listOf(
                ReplyDeliveryTarget(
                    targetKey = APP_TRANSCRIPT_TARGET_KEY,
                    kind = ReplyDeliveryTargetKind.APP_TRANSCRIPT,
                ),
            ),
            historyVisibilityTargetKey = APP_TRANSCRIPT_TARGET_KEY,
        )
    },
    private val scheduledTaskHost: ScheduledTaskHost? = null,
    private val toolRegistrations: () -> List<ToolRegistration> = { emptyList() },
    private val toolAccessPolicy: ToolAccessPolicy = BuiltInOnlyToolAccessPolicy,
) : ElymBotUiService {
    private val appContext = context.applicationContext
    private val appGuidePreferences = AppGuidePreferences(appContext)
    private val modelConfigurationService = ElymBotModelConfigurationService(repository, connectionProvider, modelGateway)
    private val viewedSessionId = MutableStateFlow<String?>(null)
    private val liveExecutions = MutableStateFlow<Map<String, LiveExecutionState>>(emptyMap())
    private val commandMessages = MutableStateFlow<Map<String, List<CommandMessageGroup>>>(emptyMap())
    private val activeExecutions = ConcurrentHashMap<String, ActiveExecution>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
    private val scheduledServiceReady = CompletableDeferred<Unit>()
    internal val replySubmitter: ReplySubmitter = RoomReplySubmitter(
        database = repository.database,
        targetSnapshotProvider = replyTargetSnapshotProvider,
    )

    init {
        scope.launch {
            try {
                repository.initialize()
                recoverScheduledTaskRuns()
                scheduledServiceReady.complete(Unit)
                val initialRuns = when (val collection = scheduledTaskHost?.onProcessReady()) {
                    is ScheduledRunCollection.Ready -> collection.requests
                    else -> emptyList()
                }
                enqueueScheduledTaskRuns(initialRuns)
                val deliveries = repository.database.replyDeliveryDao()
                deliveries.observePendingAppTranscript().collectLatest {
                    while (true) {
                        val pending = deliveries.pendingAppTranscriptSnapshot()
                        val sessionHeads = pending.distinctBy(PendingAppTranscriptDelivery::sessionId)
                        if (sessionHeads.isEmpty()) return@collectLatest

                        val now = System.currentTimeMillis()
                        val due = sessionHeads.filter { it.delivery.availableAtEpochMillis <= now }
                        if (due.isNotEmpty()) {
                            due.forEach { item -> releaseDueAppTranscript(item) }
                        } else {
                            val nextDueAt = sessionHeads.minOf { it.delivery.availableAtEpochMillis }
                            delay((nextDueAt - now).coerceAtLeast(1L))
                        }
                    }
                }
            } catch (failure: Throwable) {
                scheduledServiceReady.completeExceptionally(failure)
                throw failure
            }
        }
    }

    override val personas: Flow<List<PersonaCard>> = readyFlow { repository.database.personaDao().observeAll() }
        .map { rows -> rows.map { it.toCard() } }

    override val personaTags: Flow<List<String>> = readyFlow { repository.database.personaTagDao().observeAll() }

    override val activePersonaId: Flow<String?> = readyFlow { repository.database.conversationDao().observeMain() }
        .map { it?.personaId }

    override val mainSession: Flow<ConversationSummary?> = combine(
        readyFlow { repository.database.conversationDao().observeMainSummary() },
        readyFlow { repository.database.personaDao().observeAll() },
    ) { row, personaRows ->
        row?.toMainSummary(personaRows.firstOrNull { it.id == row.personaId }?.name.orEmpty())
    }

    override val models: Flow<ModelConfiguration> = modelConfigurationService.models

    override val appGuide: Flow<AppGuideState> = flow {
        repository.initialize()
        emit(AppGuideState(AppGuideStatus.Unknown, ProbeStatus.Unknown))
        emitAll(
            combine(models, mainSession, appGuidePreferences.skipped) { configuration, main, skipped ->
                Triple(configuration, main, skipped)
            }.flatMapLatest { (configuration, main, skipped) ->
                val choice = configuration.text
                val service = choice?.let { selected ->
                    configuration.services.firstOrNull { it.id == selected.serviceId }
                }
                val noProbe = flowOf(AppGuideState(
                    status = if (skipped) AppGuideStatus.Skipped else AppGuideStatus.NeedsSetup,
                    structuredToolCalls = ProbeStatus.Unknown,
                ))
                if (choice == null || service == null || choice.modelId.isBlank()) {
                    noProbe
                } else {
                    repository.database.modelConfigurationDao()
                        .observeProbes(service.id, choice.modelId, service.baseUrl)
                        .map { probes ->
                            val capability = probes.firstOrNull {
                                it.target == ModelProbeTarget.STRUCTURED_TOOL_CALLS.name
                            }?.status?.let { status ->
                                when (status) {
                                    CapabilityStatus.SUPPORTED.name -> ProbeStatus.Supported
                                    CapabilityStatus.UNSUPPORTED.name -> ProbeStatus.Unsupported
                                    else -> ProbeStatus.Unknown
                                }
                            } ?: ProbeStatus.Unknown
                            val ready = main != null && capability == ProbeStatus.Supported
                            AppGuideState(
                                status = when {
                                    ready -> AppGuideStatus.Ready
                                    skipped -> AppGuideStatus.Skipped
                                    else -> AppGuideStatus.NeedsSetup
                                },
                                structuredToolCalls = capability,
                            )
                        }
                }
            },
        )
    }.flowOn(Dispatchers.IO)

    override val commandNames: Flow<Map<ChatCommand, String>> = readyFlow {
        repository.database.commandNameDao().observeAll()
    }.map { rows ->
        val names = rows.associate { it.behaviorId to it.name }
        ChatCommand.entries.associateWith { command -> names[command.name] ?: command.defaultName }
    }

    override fun sessions(personaId: String): Flow<List<ConversationSummary>> = readyFlow {
        repository.database.conversationDao().observeDedicatedSummaries(personaId)
    }.map { rows ->
        rows.map { it.toSummary(appContext.getString(R.string.elym_new_chat_title)) }
    }

    override fun conversation(sessionId: String): Flow<Conversation> = flow {
        repository.initialize()
        val deliveryState = combine(
            repository.database.replyDeliveryDao().observeForSession(sessionId),
            repository.database.retiredReplyDao().observeReceiptsForSession(sessionId),
        ) { deliveries, receipts -> SessionDeliveryState(deliveries, receipts) }
        emitAll(
            combine(
                repository.database.messageDao().observeForSession(sessionId),
                repository.database.turnDao().observeForSession(sessionId),
                deliveryState,
                liveExecutions,
                commandMessages,
            ) { messages, turns, delivery, live, commands ->
                val latestTurn = turns.lastOrNull()
                val active = live[sessionId]
                val deliveriesByMessage = delivery.deliveries.groupBy { it.delivery.messageId }
                val displayed = mergeCommandMessages(messages, commands[sessionId].orEmpty()).map { message ->
                    message.copy(
                        deliveryTargets = deliveriesByMessage[message.id].orEmpty().map { it.delivery.toChatDeliveryTarget() },
                    )
                }.toMutableList()
                val displayedIds = displayed.map(ChatMessage::id).toSet()
                Conversation(
                    messages = displayed,
                    generating = active?.running == true || latestTurn?.status == TURN_STATUS_RUNNING,
                    issue = active?.issue ?: latestTurn?.let(::turnIssue),
                    canRetry = latestTurn?.let { turn ->
                        turn.retryable && turn.status != TURN_STATUS_RUNNING &&
                            delivery.deliveries.none { it.turnSequence == turn.sequence && it.blocksWholeTurnRetry() }
                    } == true,
                    toolProgressActive = active?.toolProgressActive == true,
                    detachedDeliveryReceipts = delivery.receipts.map { it.toChatDetachedDeliveryReceipt() },
                    pendingDeliveries = delivery.deliveries.asSequence()
                        .filter { it.delivery.messageId !in displayedIds && it.delivery.status != ReplyDeliveryStatus.DELIVERED.name }
                        .map { it.delivery.toChatPendingDelivery() }
                        .toList(),
                )
            },
        )
    }.flowOn(Dispatchers.IO)

    override fun checkpoints(sessionId: String): Flow<List<ChatCheckpoint>> = flow {
        repository.initialize()
        emitAll(
            combine(
                repository.database.checkpointDao().observeForSession(sessionId),
                repository.database.conversationDao().observeCurrentCheckpointId(sessionId),
                repository.database.messageDao().observeHistoryMessageCountsByTurn(sessionId),
            ) { checkpoints, currentCheckpointId, historyMessageCounts ->
                checkpoints.map { checkpoint ->
                    ChatCheckpoint(
                        id = checkpoint.id,
                        displayNumber = checkpoint.displayNumber,
                        current = checkpoint.id == currentCheckpointId,
                        retainedMessageCount = historyMessageCounts.asSequence()
                            .takeWhile { it.turnSequence <= checkpoint.boundaryTurnSequence }
                            .sumOf { it.messageCount },
                    )
                }
            },
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun saveCheckpoint(sessionId: String) {
        repository.initialize()
        sessionLock(sessionId).withLock {
            if (createCheckpoint(sessionId) == null) fail(UiIssue.NoCheckpointBoundary)
        }
    }

    override suspend fun restoreCheckpoint(sessionId: String, displayNumber: Int, expectedCheckpointId: String) {
        repository.initialize()
        sessionLock(sessionId).withLock {
            val checkpoint = restoreCheckpointState(sessionId, displayNumber, expectedCheckpointId)
            recordCommand(
                sessionId = sessionId,
                commandInput = null,
                receipt = commandText.checkpointRestored(checkpoint.displayNumber),
            )
        }
    }

    override suspend fun setViewedConversation(sessionId: String?) {
        repository.initialize()
        viewedSessionId.value = sessionId
        if (sessionId != null && repository.database.conversationDao().get(sessionId) != null) {
            repository.database.conversationDao().setUnread(sessionId, false)
        }
    }

    override suspend fun newSession(personaId: String): String {
        repository.initialize()
        val id = UUID.randomUUID().toString()
        val checkpointId = emptyCheckpointId(id)
        val timestamp = System.currentTimeMillis()
        repository.database.withTransaction {
            if (repository.database.personaDao().get(personaId) == null) fail(UiIssue.InvalidInput)
            repository.database.conversationDao().insert(
                ConversationEntity(
                    id = id,
                    kind = SESSION_KIND_DEDICATED,
                    personaId = personaId,
                    title = null,
                    mainKey = null,
                    currentCheckpointId = checkpointId,
                    nextTurnSequence = 1,
                    unread = false,
                    createdAt = timestamp,
                    lastActivityAt = timestamp,
                ),
            )
            repository.database.checkpointDao().insert(
                CheckpointEntity(checkpointId, id, 1, 0, 1, timestamp),
            )
        }
        return id
    }

    override suspend fun activatePersona(personaId: String) {
        repository.initialize()
        sessionLock(MAIN_SESSION_ID).withLock {
            repository.database.withTransaction {
                val persona = repository.database.personaDao().get(personaId) ?: fail(UiIssue.InvalidInput)
                val main = repository.database.conversationDao().getMain() ?: fail(UiIssue.Failed)
                if (repository.database.turnDao().isRunning(main.id)) fail(UiIssue.Busy)
                if (persona.id != main.personaId) repository.database.conversationDao().setActivePersona(persona.id)
            }
        }
    }

    override suspend fun savePersona(persona: PersonaCard, makeGlobal: Boolean, createMissingTags: Boolean): String {
        repository.initialize()
        val name = persona.name.trim()
        if (name.isEmpty()) fail(UiIssue.InvalidInput)
        val tagNames = if (createMissingTags) persona.tags.map(String::trim) else persona.tags
        val tagsValue = tagNames.filter(String::isNotEmpty).distinct().joinToString(TAG_SEPARATOR)
        val catalogTagNames = tagsValue.split(TAG_SEPARATOR).filter(String::isNotEmpty).distinct()
        val id = persona.id.ifBlank { UUID.randomUUID().toString() }
        var importedPath: String? = null
        val importedCoverPath = when (val uri = persona.cover) {
            null -> null
            else -> {
                val existingPath = repository.assets.relativePath(uri)
                if (existingPath != null) existingPath
                else if (uri.scheme == "content") {
                    repository.assets.import(uri).also { importedPath = it.relativePath }.relativePath
                } else {
                    fail(UiIssue.InvalidInput)
                }
            }
        }
        var previousCoverPath: String? = null
        var savedCoverPath: String? = null
        try {
            sessionLock(MAIN_SESSION_ID).withLock {
                repository.database.withTransaction {
                    val personas = repository.database.personaDao()
                    val conversations = repository.database.conversationDao()
                    val main = conversations.getMain()
                    if (makeGlobal && main != null && repository.database.turnDao().isRunning(main.id)) {
                        fail(UiIssue.Busy)
                    }

                    val existing = personas.get(id)
                    previousCoverPath = existing?.coverPath
                    val coverPath = if (persona.cover == null) existing?.coverPath else importedCoverPath
                    val order = existing?.sortOrder ?: personas.nextSortOrder()
                    val savedTags = if (createMissingTags || catalogTagNames.isEmpty()) {
                        tagsValue
                    } else {
                        val availableTags = repository.database.personaTagDao().getAll().toSet()
                        catalogTagNames.filter { it in availableTags }.joinToString(TAG_SEPARATOR)
                    }
                    val item = PersonaEntity(
                        id = id,
                        name = name,
                        description = persona.description.trim(),
                        tagsValue = savedTags,
                        prompt = persona.prompt,
                        favorite = persona.favorite,
                        coverPath = coverPath,
                        atlasTile = 0,
                        coverX = persona.coverPosition.x.coerceIn(0f, 1f),
                        coverY = persona.coverPosition.y.coerceIn(0f, 1f),
                        avatarX = persona.avatarPosition.x.coerceIn(0f, 1f),
                        avatarY = persona.avatarPosition.y.coerceIn(0f, 1f),
                        sortOrder = order,
                    )
                    if (existing == null) personas.insert(item)
                    else personas.update(item)
                    if (createMissingTags) catalogTagNames.forEach { ensurePersonaTagExists(it) }
                    savedCoverPath = coverPath

                    val activePersonaId = if (makeGlobal) {
                        id
                    } else {
                        main?.let { personas.get(it.personaId) }?.id ?: personas.getAll().firstOrNull()?.id
                    }
                    if (main == null && activePersonaId != null) {
                        val timestamp = System.currentTimeMillis()
                        val checkpointId = emptyCheckpointId(MAIN_SESSION_ID)
                        conversations.insert(
                            ConversationEntity(
                                id = MAIN_SESSION_ID,
                                kind = SESSION_KIND_MAIN,
                                personaId = activePersonaId,
                                title = null,
                                mainKey = MAIN_SESSION_ID,
                                currentCheckpointId = checkpointId,
                                nextTurnSequence = 1,
                                unread = false,
                                createdAt = timestamp,
                                lastActivityAt = timestamp,
                            ),
                        )
                        repository.database.checkpointDao().insert(
                            CheckpointEntity(checkpointId, MAIN_SESSION_ID, 1, 0, 1, timestamp),
                        )
                    } else if (main != null && activePersonaId != null && main.personaId != activePersonaId) {
                        conversations.setActivePersona(activePersonaId)
                    }
                }
            }
        } catch (failure: Throwable) {
            importedPath?.let { candidate ->
                try {
                    withContext(NonCancellable) {
                        repository.cleanupUnreferencedAssets(listOf(candidate))
                    }
                } catch (cleanupFailure: Throwable) {
                    if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
        val replacedCoverPath = previousCoverPath?.takeIf { it != savedCoverPath }
        repository.cleanupUnreferencedAssets(listOfNotNull(replacedCoverPath))
        return id
    }

    override suspend fun createPersonaTag(tag: String) {
        val normalizedTag = tag.trim()
        if (normalizedTag.isEmpty() || '\u001f' in normalizedTag) fail(UiIssue.InvalidInput)

        repository.initialize()
        repository.database.withTransaction {
            ensurePersonaTagExists(normalizedTag)
        }
    }

    override suspend fun deletePersonaTag(tag: String) {
        if (tag.isEmpty() || TAG_SEPARATOR in tag) fail(UiIssue.InvalidInput)

        repository.initialize()
        val database = repository.database
        database.withTransaction {
            val personas = database.personaDao()
            personas.getWithTag(tag, TAG_SEPARATOR).forEach { persona ->
                val remaining = persona.tagsValue.split(TAG_SEPARATOR).filterNot { it == tag }
                personas.update(persona.copy(tagsValue = remaining.joinToString(TAG_SEPARATOR)))
            }
            database.personaTagDao().delete(tag)
        }
    }

    private suspend fun ensurePersonaTagExists(tag: String) {
        val tags = repository.database.personaTagDao()
        if (tags.get(tag) == null) {
            tags.insert(
                PersonaTagEntity(
                    name = tag,
                    sortOrder = tags.nextSortOrder(),
                ),
            )
        }
    }

    override suspend fun deletePersona(personaId: String) {
        repository.initialize()
        val oldCover = repository.database.personaDao().get(personaId)?.coverPath
        repository.database.withTransaction {
            val persona = repository.database.personaDao().get(personaId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.personaDao().count() <= 1) fail(UiIssue.InvalidInput)
            val drafts = repository.database.conversationDao().emptyDraftIds(personaId)
            drafts.forEach { repository.database.checkpointDao().deleteForSession(it) }
            if (drafts.isNotEmpty()) repository.database.conversationDao().delete(drafts)
            if (repository.database.conversationDao().dedicatedCount(personaId) > 0) fail(UiIssue.OwnedSessions)
            val main = repository.database.conversationDao().getMain() ?: fail(UiIssue.Failed)
            if (main.personaId == personaId) {
                if (repository.database.turnDao().isRunning(main.id)) fail(UiIssue.Busy)
                val replacement = repository.database.personaDao().getAll().first { it.id != personaId }
                repository.database.conversationDao().setActivePersona(replacement.id)
            }
            repository.database.personaDao().delete(persona.id)
        }
        repository.cleanupUnreferencedAssets(listOfNotNull(oldCover))
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        repository.initialize()
        val normalized = title.trim().take(60)
        if (normalized.isEmpty()) fail(UiIssue.InvalidInput)
        val updated = repository.database.conversationDao().renameDedicated(
            id = sessionId,
            title = normalized,
            lastActivityAt = System.currentTimeMillis(),
        )
        if (updated != 1) fail(UiIssue.InvalidInput)
    }

    override suspend fun deleteSessions(personaId: String, sessionIds: Set<String>) {
        repository.initialize()
        if (sessionIds.isEmpty()) return
        val ids = sessionIds.toList()
        val sessions = ids.map { id -> repository.database.conversationDao().get(id) ?: fail(UiIssue.InvalidInput) }
        if (sessions.any { it.kind != SESSION_KIND_DEDICATED || it.personaId != personaId }) fail(UiIssue.InvalidInput)
        if (sessions.any { repository.database.turnDao().isRunning(it.id) }) fail(UiIssue.Busy)
        var removedAssetPaths: List<String> = emptyList()
        repository.database.withTransaction {
            val current = ids.map { repository.database.conversationDao().get(it) ?: fail(UiIssue.InvalidInput) }
            if (current.any { it.kind != SESSION_KIND_DEDICATED || it.personaId != personaId }) fail(UiIssue.InvalidInput)
            if (current.any { repository.database.turnDao().isRunning(it.id) }) fail(UiIssue.Busy)
            removedAssetPaths = repository.database.attachmentDao().pathsForSessions(ids)
            ids.forEach { repository.database.checkpointDao().deleteForSession(it) }
            repository.database.conversationDao().delete(ids)
            repository.database.retiredReplyDao().deleteExecutionsForSessions(ids)
            repository.database.retiredReplyDao().deleteReceiptsForSessions(ids)
        }
        if (viewedSessionId.value in sessionIds) viewedSessionId.value = null
        commandMessages.update { current -> current - sessionIds }
        repository.cleanupUnreferencedAssets(removedAssetPaths)
    }

    override suspend fun send(sessionId: String, text: String, image: Uri?) {
        repository.initialize()
        if (text.isBlank() && image == null) fail(UiIssue.InvalidInput)
        if (image == null && text.trimStart().startsWith('/')) {
            val names = commandNamesForSession()
            when (val result = commandParser.parse(text, names)) {
                CommandParseResult.NotCommand -> Unit
                is CommandParseResult.Invalid -> {
                    sessionLock(sessionId).withLock {
                        recordCommand(sessionId, text, commandText.invalidCommand(names.help))
                    }
                    return
                }
                is CommandParseResult.Action -> {
                    sessionLock(sessionId).withLock { executeCommand(sessionId, text, result.action) }
                    return
                }
            }
        }
        startTurn(sessionId, text, image)
    }

    override suspend fun stop(sessionId: String) {
        repository.initialize()
        val execution = activeExecutions[sessionId]
            ?: sessionLock(sessionId).withLock {
                val current = activeExecutions[sessionId]
                if (current == null) {
                    withContext(NonCancellable) {
                        repository.database.withTransaction {
                            val now = System.currentTimeMillis()
                            repository.database.replyDeliveryDao().cancelPendingForSession(sessionId, now)
                            repository.database.replyDeliveryDao().markSendingUnknownForSession(sessionId, now)
                        }
                    }
                }
                current
            }
            ?: return
        val notEntered = synchronized(execution.startupLock) {
            execution.stopRequested = true
            !execution.runBodyEntered
        }
        val job = execution.job
        job?.cancel()
        if (notEntered) {
            withContext(NonCancellable) {
                finalizeExecution(
                    execution = execution,
                    status = TURN_STATUS_STOPPED,
                    failureKind = null,
                    retryable = false,
                    issue = null,
                )
            }
        }
        job?.join()
        withContext(NonCancellable) {
            repository.database.withTransaction {
                repository.database.replyDeliveryDao().cancelPendingForAttempt(
                    sessionId = execution.sessionId,
                    executionId = execution.executionId,
                    attemptId = execution.attemptId,
                    now = System.currentTimeMillis(),
                )
                repository.database.replyDeliveryDao().markSendingUnknownForAttempt(
                    sessionId = execution.sessionId,
                    executionId = execution.executionId,
                    attemptId = execution.attemptId,
                    now = System.currentTimeMillis(),
                )
            }
        }
    }

    override suspend fun retry(sessionId: String) {
        repository.initialize()
        sessionLock(sessionId).withLock {
            val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            val previous = repository.database.turnDao().latest(sessionId) ?: fail(UiIssue.InvalidInput)
            if (!previous.retryable || previous.status == TURN_STATUS_RUNNING) fail(UiIssue.InvalidInput)
            if (repository.database.replyDeliveryDao().hasVisibleOrPossibleEffectInTurn(sessionId, previous.sequence)) {
                fail(UiIssue.InvalidInput)
            }
            val snapshot = runSnapshot(session, previous)
            val executionId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val updated = try {
                repository.database.withTransaction {
                    val turn = repository.database.turnDao().get(sessionId, previous.sequence) ?: fail(UiIssue.InvalidInput)
                    val latest = repository.database.turnDao().latest(sessionId)
                    if (latest?.sequence != turn.sequence || !turn.retryable || turn.status == TURN_STATUS_RUNNING) {
                        fail(UiIssue.InvalidInput)
                    }
                    val deliveries = repository.database.replyDeliveryDao()
                    if (deliveries.hasVisibleOrPossibleEffectInTurn(sessionId, turn.sequence)) {
                        fail(UiIssue.InvalidInput)
                    }
                    val userMessage = repository.database.messageDao().userMessage(sessionId, turn.sequence)
                        ?: fail(UiIssue.InvalidInput)
                    val now = System.currentTimeMillis()
                    deliveries.cancelPendingForTurn(sessionId, turn.sequence, now)
                    deliveries.markSendingUnknownForTurn(sessionId, turn.sequence, now)
                    repository.database.messageDao().deleteNonReplyAssistantMessages(sessionId, turn.sequence)
                    val retryTurn = turn.copy(
                        executionId = executionId,
                        attemptId = attemptId,
                        status = TURN_STATUS_RUNNING,
                        startedAt = System.currentTimeMillis(),
                        finishedAt = null,
                        failureKind = null,
                        retryable = false,
                        successfulSilence = false,
                    )
                    repository.database.turnDao().update(retryTurn)
                    Pair(retryTurn, userMessage.id)
                }
            } catch (failure: Throwable) {
                snapshot.close()
                throw failure
            }
            launchExecution(session, updated.first, updated.second, snapshot)
        }
    }

    override suspend fun retryDelivery(messageId: String, deliveryId: String) {
        repository.initialize()
        val changed = repository.database.replyDeliveryDao().retryConfirmedFailure(
            messageId = messageId,
            deliveryId = deliveryId,
            now = System.currentTimeMillis(),
        )
        if (changed != 1) fail(UiIssue.InvalidInput)
    }

    override suspend fun skipAppGuide() {
        appGuidePreferences.skip()
    }

    override suspend fun scheduledTasksSnapshot(
        sessionId: String,
        taskId: String?,
    ): ScheduledTaskSnapshotResult {
        awaitScheduledServiceReady()
        return scheduledTaskHost?.snapshotMain(sessionId, taskId)
            ?: ScheduledTaskSnapshotResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION)
    }

    override suspend fun requestScheduledTaskAction(
        sessionId: String,
        requestId: String,
        action: ScheduledTaskAction,
    ): ScheduledTaskActionResult {
        awaitScheduledServiceReady()
        val result = scheduledTaskHost?.requestMainAction(sessionId, requestId, action)
            ?: return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION)
        if (result is ScheduledTaskActionResult.RunQueued) enqueueScheduledTaskRuns(listOf(result.run))
        return result
    }

    override suspend fun confirmScheduledTaskAction(
        sessionId: String,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
    ): ScheduledTaskActionResult {
        awaitScheduledServiceReady()
        val result = scheduledTaskHost?.confirmMainAction(
            sessionId = sessionId,
            confirmationId = confirmationId,
            initiatingExecutionId = initiatingExecutionId,
            approved = approved,
        ) ?: return ScheduledTaskActionResult.Rejected(ScheduledTaskRejection.INACTIVE_INSTALLATION)
        if (result is ScheduledTaskActionResult.RunQueued) enqueueScheduledTaskRuns(listOf(result.run))
        return result
    }

    internal suspend fun processScheduledTaskWake(): Boolean {
        awaitScheduledServiceReady()
        val host = scheduledTaskHost ?: return false
        return when (val collection = host.collectPendingRuns(fromWorker = true)) {
            ScheduledRunCollection.Deferred -> false
            is ScheduledRunCollection.Ready -> {
                flushPendingScheduledTaskReceipts()
                enqueueScheduledTaskRuns(collection.requests)
                true
            }
        }
    }

    private suspend fun enqueueScheduledTaskRuns(requests: List<ScheduledTaskRunRequest>) {
        if (requests.isEmpty()) {
            drainScheduledTaskRuns()
            return
        }
        repository.database.withTransaction {
            val dao = repository.database.scheduledTaskRunInboxDao()
            for (request in requests) {
                require(request.taskId.isNotBlank() && request.runId.isNotBlank())
                require(request.installationIdentity.pluginId == SCHEDULED_TASKS_PLUGIN_ID)
                val item = request.toInboxEntity(System.currentTimeMillis())
                val inserted = dao.insertIfAbsent(item)
                if (inserted == -1L) {
                    val existing = dao.get(request.runId) ?: error("Scheduled run disappeared")
                    val contentMatches = existing.title.isEmpty() && existing.prompt.isEmpty() ||
                        existing.title == request.title && existing.prompt == request.prompt
                    check(
                        existing.taskId == request.taskId && existing.occurrenceId == request.occurrenceId &&
                            existing.scheduleRevision == request.scheduleRevision &&
                            existing.installationPluginId == request.installationIdentity.pluginId &&
                            existing.installationInstanceId == request.installationIdentity.instanceId &&
                            existing.origin == request.origin.name && contentMatches,
                    ) { "Scheduled run identity conflicts with a persisted inbox row" }
                }
            }
        }
        drainScheduledTaskRuns()
    }

    private suspend fun recoverScheduledTaskRuns() {
        if (scheduledTaskHost == null) return
        repository.database.withTransaction {
            val dao = repository.database.scheduledTaskRunInboxDao()
            val now = System.currentTimeMillis()
            for (item in dao.running()) {
                dao.finish(
                    runId = item.runId,
                    status = SCHEDULED_RUN_STATUS_FAILED,
                    failureKind = ScheduledTaskRunFailureKind.UNKNOWN.name,
                    receiptPending = true,
                    now = now,
                )
            }
            for (item in dao.claimPending().filter { it.status == SCHEDULED_RUN_STATUS_CLAIMING }) {
                dao.finishBeforeStart(
                    runId = item.runId,
                    status = SCHEDULED_RUN_STATUS_FAILED,
                    failureKind = ScheduledTaskRunFailureKind.UNKNOWN.name,
                    receiptPending = true,
                    now = now,
                )
            }
        }
        flushPendingScheduledTaskReceipts()
    }

    private suspend fun flushPendingScheduledTaskReceipts() {
        val host = scheduledTaskHost ?: return
        val dao = repository.database.scheduledTaskRunInboxDao()
        for (item in dao.pendingReceipts()) {
            val status = runCatching { ScheduledTaskRunStatus.valueOf(item.status) }.getOrNull()
                ?: ScheduledTaskRunStatus.FAILED
            val failure = item.failureKind?.let { runCatching { ScheduledTaskRunFailureKind.valueOf(it) }.getOrNull() }
            if (host.recordRunReceipt(item.toRunRequest(), status, failure)) {
                dao.acknowledgeReceipt(item.runId, System.currentTimeMillis())
            }
        }
    }

    private suspend fun drainScheduledTaskRuns() {
        awaitScheduledServiceReady()
        val host = scheduledTaskHost ?: return
        sessionLock(MAIN_SESSION_ID).withLock {
            val session = repository.database.conversationDao().getMain() ?: return
            if (repository.database.turnDao().isRunning(session.id)) return
            val dao = repository.database.scheduledTaskRunInboxDao()
            val pending = dao.queued() + dao.claimPending().filter { it.status == SCHEDULED_RUN_STATUS_CLAIMED }
            for (item in pending.distinctBy(ScheduledTaskRunInboxEntity::runId)) {
                if (repository.database.turnDao().isRunning(session.id)) return
                val request = item.toRunRequest()
                val snapshot = try {
                    runSnapshot(session)
                } catch (_: Exception) {
                    return
                }
                val newlyClaiming = item.status == SCHEDULED_RUN_STATUS_QUEUED
                if (newlyClaiming && dao.markClaiming(item.runId, System.currentTimeMillis()) != 1) {
                    snapshot.close()
                    continue
                }
                val claim = if (newlyClaiming) host.claimRun(request) else host.resumeClaimedRun(request)
                when (claim) {
                    ScheduledRunClaimResult.Deferred -> {
                        if (newlyClaiming) dao.requeueClaiming(item.runId, System.currentTimeMillis())
                        snapshot.close()
                        return
                    }
                    ScheduledRunClaimResult.Stale -> {
                        dao.finishBeforeStart(
                            item.runId,
                            SCHEDULED_RUN_STATUS_STALE,
                            ScheduledTaskRunFailureKind.TASK_CHANGED.name,
                            receiptPending = false,
                            now = System.currentTimeMillis(),
                        )
                        snapshot.close()
                        continue
                    }
                    ScheduledRunClaimResult.Lost -> {
                        dao.finishBeforeStart(
                            item.runId,
                            SCHEDULED_RUN_STATUS_FAILED,
                            ScheduledTaskRunFailureKind.UNKNOWN.name,
                            receiptPending = true,
                            now = System.currentTimeMillis(),
                        )
                        snapshot.close()
                        if (host.recordRunReceipt(request, ScheduledTaskRunStatus.FAILED, ScheduledTaskRunFailureKind.UNKNOWN)) {
                            dao.acknowledgeReceipt(item.runId, System.currentTimeMillis())
                        }
                        continue
                    }
                    ScheduledRunClaimResult.Claimed -> Unit
                }

                val prepared = try {
                    repository.database.withTransaction {
                        val current = repository.database.conversationDao().getMain() ?: return@withTransaction null
                        if (repository.database.turnDao().isRunning(current.id)) return@withTransaction null
                        if (newlyClaiming && dao.finishClaim(item.runId, System.currentTimeMillis()) != 1) {
                            return@withTransaction null
                        }
                        val persona = repository.database.personaDao().get(current.personaId)
                            ?: return@withTransaction null
                        val sequence = current.nextTurnSequence
                        val executionId = UUID.randomUUID().toString()
                        val attemptId = UUID.randomUUID().toString()
                        val userMessageId = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val turn = TurnEntity(
                            sessionId = current.id,
                            sequence = sequence,
                            executionId = executionId,
                            attemptId = attemptId,
                            status = TURN_STATUS_RUNNING,
                            startedAt = now,
                            finishedAt = null,
                            personaId = persona.id,
                            personaName = persona.name,
                            personaPrompt = persona.prompt,
                            primaryServiceId = snapshot.primary.persistedServiceId,
                            primaryModelId = snapshot.primary.modelId,
                            visionServiceId = snapshot.vision?.persistedServiceId,
                            visionModelId = snapshot.vision?.modelId,
                            failureKind = null,
                            retryable = false,
                        )
                        repository.database.turnDao().insert(turn)
                        repository.database.messageDao().insert(
                            MessageEntity(
                                id = userMessageId,
                                sessionId = current.id,
                                turnSequence = sequence,
                                messageIndex = 0,
                                role = MESSAGE_ROLE_USER,
                                text = request.prompt,
                                category = MESSAGE_CATEGORY_HISTORY,
                                status = null,
                                author = null,
                            ),
                        )
                        repository.database.conversationDao().update(
                            current.copy(nextTurnSequence = sequence + 1, lastActivityAt = now),
                        )
                        check(dao.markRunning(item.runId, current.id, sequence, now) == 1)
                        StartTurn(turn, userMessageId, PersonaPrompt(persona.id, persona.name, persona.prompt), current.kind)
                    }
                } catch (failure: Throwable) {
                    snapshot.close()
                    dao.finishBeforeStart(
                        item.runId,
                        SCHEDULED_RUN_STATUS_FAILED,
                        ScheduledTaskRunFailureKind.HOST_REJECTED.name,
                        receiptPending = true,
                        now = System.currentTimeMillis(),
                    )
                    if (host.recordRunReceipt(request, ScheduledTaskRunStatus.FAILED, ScheduledTaskRunFailureKind.HOST_REJECTED)) {
                        dao.acknowledgeReceipt(item.runId, System.currentTimeMillis())
                    }
                    throw failure
                }
                if (prepared == null) {
                    snapshot.close()
                    return
                }
                launchExecution(
                    session = session,
                    turn = prepared.turn,
                    userMessageId = prepared.userMessageId,
                    snapshot = snapshot,
                    persona = prepared.persona,
                    sessionKind = prepared.kind,
                    scheduledRun = request,
                )
                return
            }
        }
    }

    private suspend fun awaitScheduledServiceReady() {
        scheduledServiceReady.await()
    }

    override suspend fun saveModelService(service: ModelServiceDraft) =
        modelConfigurationService.saveModelService(service)

    override suspend fun deleteModelService(serviceId: String) =
        modelConfigurationService.deleteModelService(serviceId)

    override suspend fun fetchModels(service: ModelServiceDraft): List<String> =
        modelConfigurationService.fetchModels(service)

    override fun probeModel(service: ModelServiceDraft): Flow<ModelProbe> =
        modelConfigurationService.probeModel(service)

    override suspend fun setModel(role: UiModelRole, model: ModelChoice?) {
        modelConfigurationService.setModel(role, model)
        if (role == UiModelRole.Text) drainScheduledTaskRuns()
    }

    override suspend fun saveCommandNames(names: Map<ChatCommand, String>) {
        repository.initialize()
        val updated = ChatCommand.entries.associateWith { command ->
            names[command]?.trim()?.removePrefix("/") ?: command.defaultName
        }
        val values = updated.values.toList()
        if (values.any { it.isEmpty() || it.any(Char::isWhitespace) || it.contains('/') } ||
            values.map(String::lowercase).distinct().size != values.size
        ) {
            fail(UiIssue.InvalidInput)
        }
        repository.database.commandNameDao().upsertAll(
            updated.map { (command, name) -> CommandNameEntity(command.name, name) },
        )
    }

    private suspend fun startTurn(sessionId: String, text: String, image: Uri?) {
        sessionLock(sessionId).withLock {
            val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            val snapshot = runSnapshot(session)
            var importedPath: String? = null
            try {
                val asset = image?.let { repository.assets.import(it).also { copied -> importedPath = copied.relativePath } }
                val executionId = UUID.randomUUID().toString()
                val attemptId = UUID.randomUUID().toString()
                val prepared = repository.database.withTransaction {
                    val current = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
                    if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
                    val persona = repository.database.personaDao().get(current.personaId) ?: fail(UiIssue.InvalidInput)
                    val now = System.currentTimeMillis()
                    repository.database.replyDeliveryDao().cancelPendingForSession(sessionId, now)
                    repository.database.replyDeliveryDao().markSendingUnknownForSession(sessionId, now)
                    val sequence = current.nextTurnSequence
                    val userMessageId = UUID.randomUUID().toString()
                    val turn = TurnEntity(
                        sessionId = current.id,
                        sequence = sequence,
                        executionId = executionId,
                        attemptId = attemptId,
                        status = TURN_STATUS_RUNNING,
                        startedAt = System.currentTimeMillis(),
                        finishedAt = null,
                        personaId = persona.id,
                        personaName = persona.name,
                        personaPrompt = persona.prompt,
                        primaryServiceId = snapshot.primary.persistedServiceId,
                        primaryModelId = snapshot.primary.modelId,
                        visionServiceId = snapshot.vision?.persistedServiceId,
                        visionModelId = snapshot.vision?.modelId,
                        failureKind = null,
                        retryable = false,
                    )
                    repository.database.turnDao().insert(turn)
                    repository.database.messageDao().insert(
                        MessageEntity(
                            id = userMessageId,
                            sessionId = current.id,
                            turnSequence = sequence,
                            messageIndex = 0,
                            role = MESSAGE_ROLE_USER,
                            text = text,
                            category = MESSAGE_CATEGORY_HISTORY,
                            status = null,
                            author = null,
                        ),
                    )
                    if (asset != null) {
                        repository.database.attachmentDao().insert(
                            AttachmentEntity(
                                id = UUID.randomUUID().toString(),
                                messageId = userMessageId,
                                privatePath = asset.relativePath,
                                mediaType = asset.mediaType,
                                byteSize = asset.byteSize,
                            ),
                        )
                    }
                    val title = if (current.kind == SESSION_KIND_DEDICATED && current.title == null) {
                        firstTurnTitle(text, image != null)
                    } else {
                        current.title
                    }
                    repository.database.conversationDao().update(
                        current.copy(
                            title = title,
                            nextTurnSequence = sequence + 1,
                            lastActivityAt = System.currentTimeMillis(),
                        ),
                    )
                    StartTurn(turn, userMessageId, PersonaPrompt(persona.id, persona.name, persona.prompt), current.kind)
                }
                importedPath = null
                launchExecution(session, prepared.turn, prepared.userMessageId, snapshot, prepared.persona, prepared.kind)
            } catch (failure: Throwable) {
                repository.assets.delete(importedPath)
                snapshot.close()
                throw failure
            }
        }
    }

    private suspend fun launchExecution(
        session: ConversationEntity,
        turn: TurnEntity,
        userMessageId: String,
        snapshot: RunSnapshot,
        persona: PersonaPrompt = PersonaPrompt(turn.personaId, turn.personaName, turn.personaPrompt),
        sessionKind: String = session.kind,
        scheduledRun: ScheduledTaskRunRequest? = null,
    ) {
        val execution = ActiveExecution(
            sessionId = session.id,
            turnSequence = turn.sequence,
            executionId = turn.executionId,
            attemptId = turn.attemptId,
            userMessageId = userMessageId,
            persona = persona,
            sessionKind = sessionKind,
            snapshot = snapshot,
            scheduledRun = scheduledRun,
        )
        execution.job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val shouldRun = synchronized(execution.startupLock) {
                    if (execution.stopRequested) false
                    else {
                        execution.runBodyEntered = true
                        true
                    }
                }
                if (shouldRun) runTurn(execution)
            } finally {
                if (!execution.finalizationStarted.get()) {
                    val stopped = execution.stopRequested
                    withContext(NonCancellable) {
                        finalizeExecution(
                            execution = execution,
                            status = if (stopped) TURN_STATUS_STOPPED else TURN_STATUS_INTERRUPTED,
                            failureKind = if (stopped) null else AgentFailureKind.STREAM_INTERRUPTED.name,
                            retryable = !stopped,
                            issue = if (stopped) null else UiIssue.Connection,
                        )
                    }
                }
            }
        }
        val started = synchronized(execution.startupLock) {
            activeExecutions[session.id] = execution
            publishLive(
                session.id,
                LiveExecutionState(
                    executionId = turn.executionId,
                    attemptId = turn.attemptId,
                    issue = null,
                    running = true,
                ),
            )
            execution.job?.start() == true
        }
        if (!started) {
            withContext(NonCancellable) {
                finalizeExecution(
                    execution = execution,
                    status = TURN_STATUS_INTERRUPTED,
                    failureKind = AgentFailureKind.STREAM_INTERRUPTED.name,
                    retryable = true,
                    issue = UiIssue.Connection,
                    sessionLockHeld = true,
                )
            }
        }
    }

    private suspend fun runTurn(execution: ActiveExecution) {
        var terminalStatus: String? = null
        var failureKind: String? = null
        var retryable = false
        var successfulSilence = false
        var uiIssue: UiIssue? = null
        try {
            val user = repository.database.messageDao().forTurn(execution.sessionId, execution.turnSequence)
                .firstOrNull { it.message.id == execution.userMessageId }
                ?: throw IllegalStateException("Turn input is unavailable")
            if (user.attachments.any { !it.mediaType.startsWith("image/") }) {
                throw IllegalArgumentException("Unsupported attachment type")
            }
            val (primary, vision) = if (user.attachments.isEmpty()) {
                execution.snapshot.primary to execution.snapshot.vision
            } else {
                resolveVisionModels(execution)
            }
            val content = buildList {
                if (user.message.text.isNotEmpty()) add(ModelContentPart.Text(user.message.text))
                user.attachments.forEach { attachment ->
                    val bytes = repository.assets.file(attachment.privatePath).readBytes()
                    add(ModelContentPart.Image(bytes, attachment.mediaType))
                }
                if (isEmpty()) add(ModelContentPart.Text(user.message.text))
            }
            val request = AgentRequest(
                sessionId = execution.sessionId,
                executionId = execution.executionId,
                attemptId = execution.attemptId,
                turnSequence = execution.turnSequence,
                historyUpperBoundExclusive = execution.turnSequence,
                persona = execution.persona,
                primaryModel = primary.selection,
                visionModel = vision?.selection,
                currentInput = ModelMessage(ModelRole.USER, content),
                toolScope = if (execution.sessionKind == SESSION_KIND_MAIN) ToolScope.MAIN_SESSION else ToolScope.PERSONA_SESSION,
                toolSources = toolSources(),
                toolRegistrations = toolRegistrations(),
                toolAccessPolicy = toolAccessPolicy,
                inputMessage = AgentMessage(
                    messageId = user.message.id,
                    origin = MessageRoute(
                        channelId = if (execution.scheduledRun == null) APP_CHANNEL_ID else SCHEDULED_TASK_CHANNEL_ID,
                        conversationId = if (execution.scheduledRun == null) execution.sessionId else execution.scheduledRun.runId,
                        actorId = if (execution.scheduledRun == null) APP_USER_ID else SCHEDULED_TASK_ACTOR_ID,
                    ),
                    segments = buildList {
                        if (user.message.text.isNotEmpty()) add(AgentMessageSegment.Text(user.message.text))
                        user.attachments.forEach { attachment ->
                            add(
                                AgentMessageSegment.Image(
                                    MediaResourceReference(
                                        resourceId = attachment.id,
                                        mediaType = attachment.mediaType,
                                        sizeBytes = attachment.byteSize,
                                    ),
                                ),
                            )
                        }
                    },
                ),
                replySubmitter = replySubmitter,
            )
            var finished: AgentEvent.Finished? = null
            agentRuntime.run(request).takeWhile { event ->
                if (!event.belongsTo(execution)) {
                    return@takeWhile true
                }
                if (event is AgentEvent.Finished) {
                    finished = event
                    false
                } else {
                    true
                }
            }.collect { event ->
                if (!event.belongsTo(execution)) return@collect
                if (!isCurrentAttempt(execution)) return@collect
                when (event) {
                    is AgentEvent.TextDelta -> Unit
                    is AgentEvent.AssistantFeedback -> persistAssistantFeedback(execution, event.text)
                    is AgentEvent.ToolProgress -> publishToolProgress(execution, event.active)
                    is AgentEvent.Finished -> Unit
                }
            }
            val ended = finished
            if (ended == null) {
                terminalStatus = TURN_STATUS_INTERRUPTED
                failureKind = AgentFailureKind.STREAM_INTERRUPTED.name
                retryable = true
                uiIssue = UiIssue.Connection
            } else {
                terminalStatus = ended.status.toTurnStatus()
                failureKind = ended.failure?.kind?.name
                retryable = ended.failure?.retryable ?: (ended.status != AgentTerminalStatus.COMPLETED)
                successfulSilence = ended.successfulSilence
                uiIssue = ended.failure?.kind?.toUiIssue()
            }
        } catch (cancelled: CancellationException) {
            if (execution.stopRequested) {
                terminalStatus = TURN_STATUS_STOPPED
                retryable = false
            } else {
                terminalStatus = TURN_STATUS_INTERRUPTED
                failureKind = AgentFailureKind.STREAM_INTERRUPTED.name
                retryable = true
                uiIssue = UiIssue.Connection
                throw cancelled
            }
        } catch (failure: CapabilityProbeException) {
            val requestFailure = failure.requestFailure
            terminalStatus = requestFailure.toTurnStatus()
            failureKind = requestFailure.toStoredFailureKind()
            retryable = failure.retryable
            uiIssue = requestFailure.toUiIssue()
        } catch (failure: Throwable) {
            terminalStatus = failure.toTurnStatus()
            failureKind = failure.toStoredFailureKind()
            retryable = true
            uiIssue = failure.toUiIssue()
        } finally {
            withContext(NonCancellable) {
                finalizeExecution(
                    execution = execution,
                    status = terminalStatus,
                    failureKind = failureKind,
                    retryable = retryable,
                    issue = uiIssue,
                    successfulSilence = successfulSilence,
                )
            }
        }
    }

    private suspend fun resolveVisionModels(execution: ActiveExecution): Pair<RunModel, RunModel?> {
        var primary = execution.snapshot.primary
        var vision = execution.snapshot.vision
        if (primary.selection.capabilities.vision == CapabilityStatus.UNKNOWN) {
            primary = probeVision(primary)
        }
        if (primary.selection.capabilities.vision == CapabilityStatus.UNSUPPORTED) {
            val configuredVision = vision ?: return primary to null
            if (configuredVision.selection.reference == primary.selection.reference) {
                vision = primary
            } else if (configuredVision.selection.capabilities.vision == CapabilityStatus.UNKNOWN) {
                vision = probeVision(configuredVision)
            }
        }
        return primary to vision
    }

    private suspend fun probeVision(model: RunModel): RunModel {
        val report = modelGateway.probeVision(model.selection.reference)
        val failure = report.failuresByTarget[ModelProbeTarget.VISION]
        if (report.vision == CapabilityStatus.UNKNOWN && failure != null) {
            throw CapabilityProbeException(failure.toModelRequestException(), failure.retryable)
        }
        saveVisionProbeIfCurrent(model, report, failure)
        val currentCapabilities = model.selection.capabilities
        val failures = currentCapabilities.failuresByTarget - ModelProbeTarget.VISION
        val updatedFailures = failure?.let { failures + (ModelProbeTarget.VISION to it) } ?: failures
        return model.copy(
            selection = model.selection.copy(
                capabilities = currentCapabilities.copy(
                    vision = report.vision,
                    failuresByTarget = updatedFailures,
                ),
            ),
        )
    }

    private suspend fun saveVisionProbeIfCurrent(
        model: RunModel,
        report: ModelCapabilityReport,
        failure: ModelProbeFailure?,
    ) {
        if (report.vision == CapabilityStatus.UNKNOWN) return
        try {
            connectionProvider.withSavedIdentity(model.connectionServiceId, model.persistedServiceId) { service ->
                val dao = repository.database.modelConfigurationDao()
                dao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = model.persistedServiceId,
                        modelId = model.modelId,
                        baseUrl = service.baseUrl,
                        target = ModelProbeTarget.VISION.name,
                        status = report.vision.name,
                        failureKind = failure?.kind?.name,
                        httpStatusCode = failure?.httpStatusCode,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A cache write must not fail the run using its captured connection.
        }
    }

    private suspend fun persistAssistantFeedback(execution: ActiveExecution, text: String) {
        if (!isCurrentAttempt(execution)) return
        repository.database.withTransaction {
            if (!isCurrentAttempt(execution)) return@withTransaction
            val index = repository.database.messageDao().nextIndex(execution.sessionId, execution.turnSequence)
            repository.database.messageDao().insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = execution.sessionId,
                    turnSequence = execution.turnSequence,
                    messageIndex = index,
                    role = MESSAGE_ROLE_ASSISTANT,
                    text = text,
                    category = MESSAGE_CATEGORY_FEEDBACK,
                    status = null,
                    author = execution.persona.name,
                ),
            )
        }
    }

    private suspend fun finishTurn(
        execution: ActiveExecution,
        status: String,
        failureKind: String?,
        retryable: Boolean,
        issue: UiIssue?,
        successfulSilence: Boolean = false,
    ) {
        sessionLock(execution.sessionId).withLock {
            finishTurnLocked(execution, status, failureKind, retryable, issue, successfulSilence)
        }
    }

    private suspend fun finishTurnLocked(
        execution: ActiveExecution,
        status: String,
        failureKind: String?,
        retryable: Boolean,
        issue: UiIssue?,
        successfulSilence: Boolean = false,
    ) {
        val finished = repository.database.withTransaction {
            val turn = repository.database.turnDao().get(execution.sessionId, execution.turnSequence)
                ?: return@withTransaction false
            if (turn.status != TURN_STATUS_RUNNING || turn.executionId != execution.executionId || turn.attemptId != execution.attemptId) {
                return@withTransaction false
            }
            repository.database.turnDao().update(
                turn.copy(
                    status = status,
                    finishedAt = System.currentTimeMillis(),
                    failureKind = failureKind,
                    retryable = retryable,
                    successfulSilence = status == TURN_STATUS_COMPLETED && successfulSilence,
                ),
            )
            repository.database.conversationDao().setCompletionState(
                id = execution.sessionId,
                unread = viewedSessionId.value != execution.sessionId,
                lastActivityAt = System.currentTimeMillis(),
            )
            true
        }
        if (finished && issue != null && activeExecutions[execution.sessionId] === execution) {
            publishLive(
                execution.sessionId,
                LiveExecutionState(
                    executionId = execution.executionId,
                    attemptId = execution.attemptId,
                    issue = issue,
                    running = false,
                ),
            )
        }
    }

    private suspend fun finalizeExecution(
        execution: ActiveExecution,
        status: String?,
        failureKind: String?,
        retryable: Boolean,
        issue: UiIssue?,
        sessionLockHeld: Boolean = false,
        successfulSilence: Boolean = false,
    ) {
        if (!execution.finalizationStarted.compareAndSet(false, true)) return
        var finishFailure: Throwable? = null
        try {
            if (status != null) {
                try {
                    if (sessionLockHeld) finishTurnLocked(execution, status, failureKind, retryable, issue, successfulSilence)
                    else finishTurn(execution, status, failureKind, retryable, issue, successfulSilence)
                } catch (failure: Throwable) {
                    try {
                        if (sessionLockHeld) recoverInterruptedTurnLocked(execution)
                        else recoverInterruptedTurn(execution)
                    } catch (recoveryFailure: Throwable) {
                        failure.addSuppressed(recoveryFailure)
                        finishFailure = failure
                    }
                }
            }
            if (execution.scheduledRun != null) {
                try {
                    finishScheduledRun(execution)
                } catch (failure: Throwable) {
                    finishFailure = finishFailure?.also { it.addSuppressed(failure) } ?: failure
                }
            }
        } finally {
            try {
                execution.snapshot.close()
            } finally {
                activeExecutions.remove(execution.sessionId, execution)
                liveExecutions.update { current ->
                    val state = current[execution.sessionId]
                    if (state?.belongsTo(execution) == true) current - execution.sessionId else current
                }
                if (execution.sessionId == MAIN_SESSION_ID) {
                    scope.launch { drainScheduledTaskRuns() }
                }
            }
        }
        finishFailure?.let { throw it }
    }

    private suspend fun recoverInterruptedTurn(execution: ActiveExecution) {
        sessionLock(execution.sessionId).withLock {
            recoverInterruptedTurnLocked(execution)
        }
    }

    private suspend fun recoverInterruptedTurnLocked(execution: ActiveExecution) {
        repository.database.withTransaction {
            val turn = repository.database.turnDao().get(execution.sessionId, execution.turnSequence)
                ?: return@withTransaction
            if (turn.executionId != execution.executionId || turn.attemptId != execution.attemptId ||
                turn.status != TURN_STATUS_RUNNING
            ) {
                return@withTransaction
            }
            val messageDao = repository.database.messageDao()
            val assistant = messageDao.assistantHistoryMessage(execution.sessionId, execution.turnSequence)
            if (assistant != null && !repository.database.replyDeliveryDao().isCanonicalReply(assistant.id)) {
                messageDao.update(assistant.copy(status = TURN_STATUS_INTERRUPTED))
            }
            repository.database.turnDao().update(
                turn.copy(
                    status = TURN_STATUS_INTERRUPTED,
                    finishedAt = System.currentTimeMillis(),
                    failureKind = AgentFailureKind.INTERNAL.name,
                    retryable = true,
                ),
            )
            repository.database.conversationDao().setCompletionState(
                id = execution.sessionId,
                unread = viewedSessionId.value != execution.sessionId,
                lastActivityAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun isCurrentAttempt(execution: ActiveExecution): Boolean {
        if (activeExecutions[execution.sessionId] !== execution) return false
        val turn = repository.database.turnDao().get(execution.sessionId, execution.turnSequence) ?: return false
        return turn.status == TURN_STATUS_RUNNING && turn.executionId == execution.executionId && turn.attemptId == execution.attemptId
    }

    private suspend fun releaseDueAppTranscript(item: PendingAppTranscriptDelivery) {
        repository.database.withTransaction {
            val deliveries = repository.database.replyDeliveryDao()
            val currentHead = deliveries.pendingAppTranscriptSnapshot()
                .firstOrNull { it.sessionId == item.sessionId }
                ?: return@withTransaction
            if (currentHead.delivery.id != item.delivery.id) return@withTransaction

            val releasedAt = System.currentTimeMillis()
            if (item.delivery.availableAtEpochMillis > releasedAt) return@withTransaction
            if (deliveries.releaseDueAppTranscript(item.delivery.id, releasedAt) != 1) return@withTransaction

            repository.database.conversationDao().setCompletionState(
                id = item.sessionId,
                unread = viewedSessionId.value != item.sessionId,
                lastActivityAt = releasedAt,
            )
        }
    }

    private suspend fun finishScheduledRun(execution: ActiveExecution) {
        val request = execution.scheduledRun ?: return
        val turn = repository.database.turnDao().get(execution.sessionId, execution.turnSequence) ?: return
        if (turn.status == TURN_STATUS_RUNNING) return
        val status = when (turn.status) {
            TURN_STATUS_COMPLETED -> ScheduledTaskRunStatus.SUCCEEDED
            TURN_STATUS_STOPPED -> ScheduledTaskRunStatus.CANCELLED
            else -> ScheduledTaskRunStatus.FAILED
        }
        val failureKind = when {
            status == ScheduledTaskRunStatus.CANCELLED -> ScheduledTaskRunFailureKind.USER_CANCELLED
            status == ScheduledTaskRunStatus.FAILED -> ScheduledTaskRunFailureKind.AGENT_FAILED
            else -> null
        }
        val dao = repository.database.scheduledTaskRunInboxDao()
        dao.finish(
            runId = request.runId,
            status = status.name,
            failureKind = failureKind?.name,
            receiptPending = true,
            now = System.currentTimeMillis(),
        )
        if (scheduledTaskHost?.recordRunReceipt(request, status, failureKind) == true) {
            dao.acknowledgeReceipt(request.runId, System.currentTimeMillis())
        }
    }

    private suspend fun publishToolProgress(execution: ActiveExecution, active: Boolean) {
        if (!isCurrentAttempt(execution)) return
        liveExecutions.update { current ->
            val state = current[execution.sessionId]
            if (state?.belongsTo(execution) != true) current
            else current + (execution.sessionId to state.copy(toolProgressActive = active))
        }
    }

    private suspend fun runSnapshot(session: ConversationEntity, retry: TurnEntity? = null): RunSnapshot {
        val persona = retry?.let { PersonaPrompt(it.personaId, it.personaName, it.personaPrompt) }
            ?: repository.database.personaDao().get(session.personaId)?.let { PersonaPrompt(it.id, it.name, it.prompt) }
            ?: fail(UiIssue.InvalidInput)
        val configuredModels = repository.database.withTransaction {
            val dao = repository.database.modelConfigurationDao()
            val primaryAssignment = if (retry == null) dao.getAssignment(UiModelRole.Text.name) else null
            val visionAssignment = if (retry == null) dao.getAssignment(UiModelRole.Vision.name) else null

            suspend fun configuredModel(serviceId: String?, modelId: String?): ConfiguredModel? {
                if (serviceId == null || modelId.isNullOrBlank()) return null
                val service = dao.getService(serviceId) ?: return null
                val rows = dao.getProbes(serviceId, modelId, service.baseUrl)
                val statuses = rows.associate { it.target to it.status }
                val failures = rows.mapNotNull { row ->
                    row.failureKind?.let { kind ->
                        val target = runCatching { ModelProbeTarget.valueOf(row.target) }.getOrNull() ?: return@let null
                        target to ModelProbeFailure(
                            kind = runCatching { ModelProbeFailureKind.valueOf(kind) }.getOrNull() ?: ModelProbeFailureKind.PROTOCOL,
                            httpStatusCode = row.httpStatusCode,
                            retryable = kind in setOf("NETWORK", "HTTP", "TIMED_OUT"),
                        )
                    }
                }.toMap()
                val reference = ModelReference(serviceId, modelId)
                val capabilities = ModelCapabilityReport(
                    model = reference,
                    availability = statuses.status(ModelProbeTarget.AVAILABILITY),
                    vision = statuses.status(ModelProbeTarget.VISION),
                    nativeStreaming = statuses.status(ModelProbeTarget.NATIVE_STREAMING),
                    structuredToolCalls = statuses.status(ModelProbeTarget.STRUCTURED_TOOL_CALLS),
                    failuresByTarget = failures,
                )
                val credential = repository.database.credentialDao().get(serviceId)
                return ConfiguredModel(service, modelId, capabilities, credential)
            }

            val primary = if (retry == null) {
                primaryAssignment?.let { configuredModel(it.serviceId, it.modelId) }
            } else {
                configuredModel(retry.primaryServiceId, retry.primaryModelId)
            }
            val vision = if (retry == null) {
                visionAssignment?.let { configuredModel(it.serviceId, it.modelId) }
            } else {
                configuredModel(retry.visionServiceId, retry.visionModelId)
            }
            primary to vision
        }
        val primary = configuredModels.first ?: fail(UiIssue.MainModelMissing)
        val vision = configuredModels.second
        val leases = linkedMapOf<String, com.elymbot.android.data.ModelConnectionLease>()
        return try {
            suspend fun selection(configured: ConfiguredModel): RunModel {
                val lease = leases[configured.service.id] ?: try {
                    connectionProvider.snapshot(
                        configured.service.id,
                        configured.service.baseUrl,
                        configured.credential,
                    )
                } catch (_: CredentialVault.CredentialUnavailableException) {
                    fail(UiIssue.Authentication)
                }?.also { leases[configured.service.id] = it } ?: run {
                    val current = repository.database.modelConfigurationDao().getService(configured.service.id)
                    fail(if (current == null) UiIssue.MainModelMissing else UiIssue.Connection)
                }
                val reference = ModelReference(lease.serviceId, configured.modelId)
                return RunModel(
                    persistedServiceId = configured.service.id,
                    modelId = configured.modelId,
                    connectionServiceId = lease.serviceId,
                    selection = ModelSelection(reference, configured.capabilities.copy(model = reference)),
                )
            }
            RunSnapshot(
                persona = persona,
                primary = selection(primary),
                vision = vision?.let { selection(it) },
                leases = leases.values.toMutableList(),
            )
        } catch (failure: Throwable) {
            leases.values.forEach { it.close() }
            throw failure
        }
    }

    private suspend fun executeCommand(sessionId: String, commandInput: String, action: CommandAction) {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        val names = commandNamesForSession()
        val receipt = when (action) {
            CommandAction.Help -> commandText.help(session.kind == SESSION_KIND_MAIN, names)
            is CommandAction.Persona -> {
                if (session.kind != SESSION_KIND_MAIN) {
                    commandText.invalidCommand(names.help)
                } else if (action.index == null) {
                    val personas = repository.database.personaDao().getAll()
                    personas.mapIndexed { index, persona ->
                        commandText.personaEntry(index + 1, persona.name, persona.id == session.personaId)
                    }.joinToString("\n")
                } else {
                    val index = action.index ?: -1
                    val persona = repository.database.personaDao().getAll().getOrNull(index - 1)
                        ?: run {
                            recordCommand(sessionId, commandInput, commandText.invalidCommand(names.help))
                            return
                        }
                    repository.database.withTransaction {
                        val current = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
                        if (current.kind != SESSION_KIND_MAIN || repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
                        repository.database.conversationDao().setActivePersona(persona.id)
                    }
                    commandText.personaSwitched(persona.name)
                }
            }
            is CommandAction.Checkpoint -> {
                val number = action.number
                if (number == null) checkpointList(sessionId)
                else commandText.checkpointRestored(restoreCheckpointState(sessionId, number).displayNumber)
            }
            CommandAction.Reset -> {
                val current = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
                val checkpoint = repository.database.checkpointDao().getForSession(sessionId)
                    .firstOrNull { it.id == current.currentCheckpointId } ?: fail(UiIssue.InvalidInput)
                commandText.checkpointRestored(restoreCheckpointState(sessionId, checkpoint.displayNumber).displayNumber)
            }
            CommandAction.Mark -> createCheckpoint(sessionId)?.let { commandText.checkpointMarked(it.displayNumber) }
                ?: commandText.noMarkBoundary()
        }
        recordCommand(sessionId, commandInput, receipt)
    }

    private suspend fun checkpointList(sessionId: String): String {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        val checkpoints = repository.database.checkpointDao().getForSession(sessionId)
        if (checkpoints.isEmpty()) return commandText.noCheckpoints()
        return checkpoints.joinToString("\n") { checkpoint ->
            commandText.checkpointEntry(checkpoint.displayNumber, checkpoint.id == session.currentCheckpointId)
        }
    }

    private suspend fun createCheckpoint(sessionId: String): CheckpointEntity? = repository.database.withTransaction {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
        val latest = repository.database.turnDao().latest(sessionId)
        if (latest?.status != TURN_STATUS_COMPLETED) return@withTransaction null
        val checkpoints = repository.database.checkpointDao().getForSession(sessionId)
        val point = CheckpointEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            displayNumber = (checkpoints.maxOfOrNull { it.displayNumber } ?: 0) + 1,
            boundaryTurnSequence = latest.sequence,
            creationOrder = repository.database.checkpointDao().nextCreationOrder(sessionId),
            createdAt = System.currentTimeMillis(),
        )
        repository.database.checkpointDao().insert(point)
        repository.database.conversationDao().update(session.copy(currentCheckpointId = point.id))
        point
    }

    private suspend fun restoreCheckpointState(
        sessionId: String,
        displayNumber: Int,
        expectedCheckpointId: String? = null,
    ): CheckpointEntity {
        var removedAssetPaths: List<String> = emptyList()
        val restored = repository.database.withTransaction {
            val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            val selected = repository.database.checkpointDao().getByNumber(sessionId, displayNumber)
                ?: fail(UiIssue.InvalidInput)
            if (expectedCheckpointId != null && selected.id != expectedCheckpointId) fail(UiIssue.InvalidInput)
            val retiredAt = System.currentTimeMillis()
            val removedTurns = repository.database.turnDao().after(sessionId, selected.boundaryTurnSequence)
            repository.database.retiredReplyDao().retireExecutions(
                removedTurns.map { turn ->
                    RetiredReplyExecutionEntity(
                        sessionId = turn.sessionId,
                        executionId = turn.executionId,
                        attemptId = turn.attemptId,
                        turnSequence = turn.sequence,
                        retiredAtEpochMillis = retiredAt,
                    )
                },
            )
            val detachedReceipts = repository.database.replySubmissionDao()
                .externalEffectsAfter(sessionId, selected.boundaryTurnSequence)
                .mapNotNull { delivery ->
                    val channelId = delivery.channelId ?: return@mapNotNull null
                    val conversationId = delivery.conversationId ?: return@mapNotNull null
                    val pluginId = delivery.pluginId ?: return@mapNotNull null
                    val installationInstanceId = delivery.installationInstanceId ?: return@mapNotNull null
                    RetiredReplyReceiptEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = delivery.sessionId,
                        turnSequence = delivery.turnSequence,
                        messageId = delivery.messageId,
                        targetKey = delivery.targetKey,
                        channelId = channelId,
                        conversationId = conversationId,
                        accountId = delivery.accountId,
                        actorId = delivery.actorId,
                        pluginId = pluginId,
                        installationInstanceId = installationInstanceId,
                        status = delivery.status,
                        remoteMessageId = delivery.remoteMessageId,
                        retiredAtEpochMillis = retiredAt,
                    )
                }
            repository.database.retiredReplyDao().insertReceipts(detachedReceipts)
            removedAssetPaths = repository.database.attachmentDao().pathsAfterTurn(sessionId, selected.boundaryTurnSequence)
            repository.database.turnDao().deleteAfter(sessionId, selected.boundaryTurnSequence)
            repository.database.historySummaryDao().deleteAfter(sessionId, selected.boundaryTurnSequence)
            repository.database.checkpointDao().deleteAfter(sessionId, selected.creationOrder)
            repository.database.conversationDao().update(
                session.copy(currentCheckpointId = selected.id, unread = false, lastActivityAt = System.currentTimeMillis()),
            )
            selected
        }
        repository.cleanupUnreferencedAssets(removedAssetPaths)
        commandMessages.update { it - sessionId }
        return restored
    }

    private suspend fun recordCommand(sessionId: String, commandInput: String?, receipt: String) {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        val persona = repository.database.personaDao().get(session.personaId) ?: fail(UiIssue.InvalidInput)
        val messages = buildList {
            commandInput?.let { add(ChatMessage(UUID.randomUUID().toString(), text = it)) }
            add(
                ChatMessage(
                    UUID.randomUUID().toString(),
                    text = receipt,
                    assistant = true,
                    author = persona.name,
                    localReceipt = true,
                ),
            )
        }
        val group = CommandMessageGroup(session.nextTurnSequence, messages)
        commandMessages.update { current -> current + (sessionId to (current[sessionId].orEmpty() + group)) }
    }

    private fun mergeCommandMessages(
        stored: List<MessageWithAttachments>,
        commandGroups: List<CommandMessageGroup>,
    ): MutableList<ChatMessage> {
        val displayed = mutableListOf<ChatMessage>()
        var commandIndex = 0
        stored.forEach { item ->
            while (commandIndex < commandGroups.size &&
                commandGroups[commandIndex].beforeTurnSequence <= item.message.turnSequence
            ) {
                displayed += commandGroups[commandIndex].messages
                commandIndex += 1
            }
            displayed += item.toChatMessage()
        }
        while (commandIndex < commandGroups.size) {
            displayed += commandGroups[commandIndex].messages
            commandIndex += 1
        }
        return displayed
    }

    private fun ReplyDeliveryEntity.toChatDeliveryTarget() = ChatDeliveryTarget(
        id = id,
        kind = targetKind,
        channelId = channelId,
        conversationId = conversationId,
        accountId = accountId,
        status = status.toChatDeliveryStatus(),
    )

    private fun RetiredReplyReceiptEntity.toChatDetachedDeliveryReceipt() = ChatDetachedDeliveryReceipt(
        id = id,
        turnSequence = turnSequence,
        messageId = messageId,
        targetKey = targetKey,
        channelId = channelId,
        conversationId = conversationId,
        accountId = accountId,
        pluginId = pluginId,
        status = status.toChatDeliveryStatus(),
        remoteMessageId = remoteMessageId,
    )

    private fun ReplyDeliveryEntity.toChatPendingDelivery() = ChatPendingDelivery(
        id = id,
        messageId = messageId,
        targetKey = targetKey,
        kind = targetKind,
        channelId = channelId,
        conversationId = conversationId,
        accountId = accountId,
        status = status.toChatDeliveryStatus(),
        availableAtEpochMillis = availableAtEpochMillis,
    )

    private fun ReplyDeliveryWithSubmission.blocksWholeTurnRetry(): Boolean = when (delivery.targetKind) {
        ReplyDeliveryTargetKind.APP_TRANSCRIPT.name -> delivery.status == ReplyDeliveryStatus.DELIVERED.name
        ReplyDeliveryTargetKind.PLATFORM.name -> delivery.status in setOf(
            ReplyDeliveryStatus.DELIVERED.name,
            ReplyDeliveryStatus.UNKNOWN.name,
            ReplyDeliveryStatus.SENDING.name,
        )
        else -> false
    }

    private fun String.toChatDeliveryStatus() = when (this) {
        ReplyDeliveryStatus.PENDING.name -> ChatDeliveryStatus.Pending
        ReplyDeliveryStatus.SENDING.name -> ChatDeliveryStatus.Sending
        ReplyDeliveryStatus.DELIVERED.name -> ChatDeliveryStatus.Delivered
        ReplyDeliveryStatus.FAILED.name -> ChatDeliveryStatus.Failed
        ReplyDeliveryStatus.UNKNOWN.name -> ChatDeliveryStatus.Unknown
        ReplyDeliveryStatus.CANCELLED.name -> ChatDeliveryStatus.Cancelled
        else -> ChatDeliveryStatus.Stale
    }

    private fun firstTurnTitle(text: String, hasImage: Boolean): String {
        val normalized = StringBuilder()
        var pendingSpace = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.isNotEmpty()
            } else {
                if (pendingSpace) normalized.append(' ')
                normalized.appendCodePoint(codePoint)
                pendingSpace = false
            }
            index += Character.charCount(codePoint)
        }
        if (normalized.isEmpty()) {
            return if (hasImage) {
                appContext.getString(R.string.elym_session_image_title)
            } else {
                appContext.getString(R.string.elym_new_chat_title)
            }
        }
        val end = normalized.offsetByCodePoints(0, minOf(26, normalized.codePointCount(0, normalized.length)))
        return normalized.substring(0, end)
    }

    private suspend fun commandNamesForSession(): CommandNames {
        val saved = repository.database.commandNameDao().getAll().associate { it.behaviorId to it.name }
        return CommandNames(
            help = saved[ChatCommand.Help.name] ?: ChatCommand.Help.defaultName,
            persona = saved[ChatCommand.Persona.name] ?: ChatCommand.Persona.defaultName,
            checkpoint = saved[ChatCommand.Checkpoint.name] ?: ChatCommand.Checkpoint.defaultName,
            reset = saved[ChatCommand.Reset.name] ?: ChatCommand.Reset.defaultName,
            mark = saved[ChatCommand.Mark.name] ?: ChatCommand.Mark.defaultName,
        )
    }

    private fun sessionLock(sessionId: String): Mutex = sessionLocks.computeIfAbsent(sessionId) { Mutex() }

    private fun publishLive(sessionId: String, state: LiveExecutionState) {
        liveExecutions.update { it + (sessionId to state) }
    }

    private fun AgentEvent.belongsTo(execution: ActiveExecution): Boolean =
        executionId == execution.executionId && attemptId == execution.attemptId

    private fun LiveExecutionState.belongsTo(execution: ActiveExecution): Boolean =
        executionId == execution.executionId && attemptId == execution.attemptId

    private fun <T> readyFlow(source: () -> Flow<T>): Flow<T> = flow {
        repository.initialize()
        emitAll(source())
    }

    private fun Map<String, String>.status(target: ModelProbeTarget): CapabilityStatus =
        this[target.name]?.let { runCatching { CapabilityStatus.valueOf(it) }.getOrNull() } ?: CapabilityStatus.UNKNOWN

    private fun ModelProbeFailure.toModelRequestException(): ModelRequestException = when (kind) {
        ModelProbeFailureKind.NETWORK -> ModelRequestException.Network("The model service could not be reached.")
        ModelProbeFailureKind.AUTHENTICATION -> ModelRequestException.HttpStatus(
            httpStatusCode?.takeIf { it == HttpURLConnection.HTTP_UNAUTHORIZED || it == HttpURLConnection.HTTP_FORBIDDEN }
                ?: HttpURLConnection.HTTP_UNAUTHORIZED,
            "The model service rejected authentication.",
        )
        ModelProbeFailureKind.HTTP -> ModelRequestException.HttpStatus(
            httpStatusCode ?: HttpURLConnection.HTTP_INTERNAL_ERROR,
            "The model service returned an error.",
        )
        ModelProbeFailureKind.API_REJECTED -> ModelRequestException.ApiRejected(
            code = null,
            message = "The model service rejected the capability probe.",
            httpStatusCode = httpStatusCode,
        )
        ModelProbeFailureKind.PROTOCOL -> ModelRequestException.Protocol("The model service returned an invalid capability response.")
        ModelProbeFailureKind.TIMED_OUT -> ModelRequestException.TimedOut("The model capability probe timed out.")
    }

    private fun AgentFailureKind.toUiIssue(): UiIssue = when (this) {
        AgentFailureKind.NETWORK, AgentFailureKind.STREAM_INTERRUPTED -> UiIssue.Connection
        AgentFailureKind.AUTHENTICATION -> UiIssue.Authentication
        AgentFailureKind.MODEL_REJECTED -> UiIssue.ServiceRejected
        AgentFailureKind.PROTOCOL -> UiIssue.ServiceProtocol
        AgentFailureKind.VISION_MODEL_MISSING -> UiIssue.VisionModelMissing
        AgentFailureKind.VISION_CAPABILITY_UNKNOWN -> UiIssue.VisionCapabilityUnknown
        AgentFailureKind.CONTEXT_LIMIT, AgentFailureKind.OUTPUT_LIMIT -> UiIssue.InvalidInput
        AgentFailureKind.TOOL_LIMIT, AgentFailureKind.EXECUTION_LIMIT, AgentFailureKind.INTERNAL -> UiIssue.Failed
    }

    private fun Throwable.toAgentFailureKind(): AgentFailureKind = when (this) {
        is ModelRequestException.Network -> AgentFailureKind.NETWORK
        is ModelRequestException.HttpStatus -> if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || statusCode == HttpURLConnection.HTTP_FORBIDDEN) AgentFailureKind.AUTHENTICATION else AgentFailureKind.MODEL_REJECTED
        is ModelRequestException.ApiRejected -> if (
            httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED || httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN
        ) AgentFailureKind.AUTHENTICATION else AgentFailureKind.MODEL_REJECTED
        is ModelRequestException.Protocol -> AgentFailureKind.PROTOCOL
        is ModelRequestException.StreamInterrupted -> AgentFailureKind.STREAM_INTERRUPTED
        is ModelRequestException.ContextLimit -> AgentFailureKind.CONTEXT_LIMIT
        is ModelRequestException.TimedOut -> AgentFailureKind.NETWORK
        is CredentialVault.CredentialUnavailableException -> AgentFailureKind.AUTHENTICATION
        else -> AgentFailureKind.INTERNAL
    }

    private fun Throwable.toStoredFailureKind(): String = when (this) {
        is ModelRequestException.TimedOut -> TURN_STATUS_TIMED_OUT
        is ModelRequestException.ContextLimit -> TURN_STATUS_CONTEXT_LIMIT
        is ModelRequestException.StreamInterrupted -> TURN_STATUS_INTERRUPTED
        else -> toAgentFailureKind().name
    }

    private fun Throwable.toTurnStatus(): String = when (this) {
        is ModelRequestException.ContextLimit -> TURN_STATUS_CONTEXT_LIMIT
        is ModelRequestException.TimedOut -> TURN_STATUS_TIMED_OUT
        is ModelRequestException.StreamInterrupted -> TURN_STATUS_INTERRUPTED
        else -> TURN_STATUS_FAILED
    }

    private fun AgentTerminalStatus.toTurnStatus(): String = when (this) {
        AgentTerminalStatus.COMPLETED -> TURN_STATUS_COMPLETED
        AgentTerminalStatus.INTERRUPTED -> TURN_STATUS_INTERRUPTED
        AgentTerminalStatus.FAILED -> TURN_STATUS_FAILED
        AgentTerminalStatus.TIMED_OUT -> TURN_STATUS_TIMED_OUT
        AgentTerminalStatus.CONTEXT_LIMIT -> TURN_STATUS_CONTEXT_LIMIT
        AgentTerminalStatus.OUTPUT_LIMIT -> TURN_STATUS_OUTPUT_LIMIT
    }

    private fun AgentFailureKind.toTurnFailureStatus(): String = when (this) {
        AgentFailureKind.CONTEXT_LIMIT -> TURN_STATUS_CONTEXT_LIMIT
        AgentFailureKind.OUTPUT_LIMIT -> TURN_STATUS_OUTPUT_LIMIT
        AgentFailureKind.STREAM_INTERRUPTED -> TURN_STATUS_INTERRUPTED
        else -> TURN_STATUS_FAILED
    }

    private fun failureKindToIssue(kind: String): UiIssue = runCatching { AgentFailureKind.valueOf(kind).toUiIssue() }
        .getOrElse {
            when (kind) {
                TURN_STATUS_TIMED_OUT -> UiIssue.TimedOut
                TURN_STATUS_CONTEXT_LIMIT, TURN_STATUS_OUTPUT_LIMIT -> UiIssue.InvalidInput
                TURN_STATUS_INTERRUPTED -> UiIssue.Connection
                else -> UiIssue.Failed
            }
        }

    private fun turnIssue(turn: TurnEntity): UiIssue? = turn.failureKind?.let(::failureKindToIssue)
        ?: when (turn.status) {
            TURN_STATUS_FAILED -> UiIssue.Failed
            TURN_STATUS_TIMED_OUT -> UiIssue.TimedOut
            TURN_STATUS_CONTEXT_LIMIT, TURN_STATUS_OUTPUT_LIMIT -> UiIssue.InvalidInput
            TURN_STATUS_INTERRUPTED -> UiIssue.Connection
            else -> null
        }

    private fun PersonaEntity.toCard() = PersonaCard(
        id = id,
        name = name,
        description = description,
        tags = tagsValue.split(TAG_SEPARATOR).filter(String::isNotEmpty),
        prompt = prompt,
        favorite = favorite,
        cover = repository.assets.uri(coverPath),
        coverPosition = ImagePosition(coverX, coverY),
        avatarPosition = ImagePosition(avatarX, avatarY),
    )

    private fun ConversationSummaryRow.toSummary(defaultTitle: String) = ConversationSummary(
        id = id,
        personaId = personaId,
        title = title.ifBlank { defaultTitle },
        preview = preview,
        running = running,
        unread = unread,
    )

    private fun ConversationSummaryRow.toMainSummary(mainPersonaName: String): ConversationSummary =
        copy(title = mainPersonaName).toSummary(mainPersonaName)

    private fun MessageWithAttachments.toChatMessage() = ChatMessage(
        id = message.id,
        text = message.text,
        assistant = message.role == MESSAGE_ROLE_ASSISTANT,
        author = message.author,
        image = attachments.firstOrNull()?.let { repository.assets.uri(it.privatePath) },
        status = when (message.status) {
            TURN_STATUS_STOPPED -> MessageStatus.Stopped
            TURN_STATUS_INTERRUPTED, TURN_STATUS_FAILED, TURN_STATUS_TIMED_OUT,
            TURN_STATUS_CONTEXT_LIMIT, TURN_STATUS_OUTPUT_LIMIT -> MessageStatus.Interrupted
            else -> null
        },
    )

    private fun RunSnapshot.close() = leases.forEach { it.close() }

    private fun fail(issue: UiIssue): Nothing = throw UiServiceException(issue)

    private data class ConfiguredModel(
        val service: ModelServiceEntity,
        val modelId: String,
        val capabilities: ModelCapabilityReport,
        val credential: CredentialEntity?,
    )

    private data class RunModel(
        val persistedServiceId: String,
        val modelId: String,
        val connectionServiceId: String,
        val selection: ModelSelection,
    )

    private class CapabilityProbeException(
        val requestFailure: ModelRequestException,
        val retryable: Boolean,
    ) : Exception(requestFailure.message, requestFailure)

    private data class RunSnapshot(
        val persona: PersonaPrompt,
        val primary: RunModel,
        val vision: RunModel?,
        val leases: MutableList<com.elymbot.android.data.ModelConnectionLease>,
    )

    private data class StartTurn(
        val turn: TurnEntity,
        val userMessageId: String,
        val persona: PersonaPrompt,
        val kind: String,
    )

    private data class LiveExecutionState(
        val executionId: String,
        val attemptId: String,
        val issue: UiIssue?,
        val running: Boolean,
        val toolProgressActive: Boolean = false,
    )

    private data class CommandMessageGroup(
        val beforeTurnSequence: Long,
        val messages: List<ChatMessage>,
    )

    private data class SessionDeliveryState(
        val deliveries: List<ReplyDeliveryWithSubmission>,
        val receipts: List<RetiredReplyReceiptEntity>,
    )

    private class ActiveExecution(
        val sessionId: String,
        val turnSequence: Long,
        val executionId: String,
        val attemptId: String,
        val userMessageId: String,
        val persona: PersonaPrompt,
        val sessionKind: String,
        val snapshot: RunSnapshot,
        val scheduledRun: ScheduledTaskRunRequest? = null,
    ) {
        @Volatile var stopRequested = false
        val startupLock = Any()
        @Volatile var runBodyEntered = false
        val finalizationStarted = AtomicBoolean(false)
        @Volatile var job: Job? = null
    }

    private companion object {
        const val MAIN_SESSION_ID = "main"
        const val SESSION_KIND_MAIN = "MAIN"
        const val SESSION_KIND_DEDICATED = "DEDICATED"
        const val TURN_STATUS_RUNNING = "RUNNING"
        const val TURN_STATUS_COMPLETED = "COMPLETED"
        const val TURN_STATUS_STOPPED = "STOPPED"
        const val TURN_STATUS_INTERRUPTED = "INTERRUPTED"
        const val TURN_STATUS_FAILED = "FAILED"
        const val TURN_STATUS_TIMED_OUT = "TIMED_OUT"
        const val TURN_STATUS_CONTEXT_LIMIT = "CONTEXT_LIMIT"
        const val TURN_STATUS_OUTPUT_LIMIT = "OUTPUT_LIMIT"
        const val MESSAGE_ROLE_USER = "USER"
        const val MESSAGE_ROLE_ASSISTANT = "ASSISTANT"
        const val MESSAGE_CATEGORY_HISTORY = "HISTORY"
        const val MESSAGE_CATEGORY_FEEDBACK = "FEEDBACK"
        const val MESSAGE_CATEGORY_COMMAND = "COMMAND"
        const val APP_CHANNEL_ID = "app"
        const val APP_USER_ID = "local-user"
        const val SCHEDULED_TASK_CHANNEL_ID = "scheduled-task"
        const val SCHEDULED_TASK_ACTOR_ID = "scheduled-task"
        const val TAG_SEPARATOR = "\u001f"

        fun emptyCheckpointId(sessionId: String) = "empty:$sessionId"
    }
}

private fun ScheduledTaskRunRequest.toInboxEntity(now: Long) = ScheduledTaskRunInboxEntity(
    runId = runId,
    taskId = taskId,
    occurrenceId = occurrenceId,
    scheduleRevision = scheduleRevision,
    plannedAtEpochMillis = plannedAt?.toEpochMilli(),
    triggeredAtEpochMillis = triggeredAt.toEpochMilli(),
    title = title,
    prompt = prompt,
    timeZoneId = timeZoneId,
    origin = origin.name,
    installationPluginId = installationIdentity.pluginId,
    installationInstanceId = installationIdentity.instanceId,
    status = SCHEDULED_RUN_STATUS_QUEUED,
    updatedAtEpochMillis = now,
)

private fun ScheduledTaskRunInboxEntity.toRunRequest() = ScheduledTaskRunRequest(
    taskId = taskId,
    runId = runId,
    occurrenceId = occurrenceId,
    scheduleRevision = scheduleRevision,
    plannedAt = plannedAtEpochMillis?.let(Instant::ofEpochMilli),
    triggeredAt = Instant.ofEpochMilli(triggeredAtEpochMillis),
    title = title,
    prompt = prompt,
    timeZoneId = timeZoneId,
    origin = runCatching { ScheduledTaskRunOrigin.valueOf(origin) }.getOrDefault(ScheduledTaskRunOrigin.SCHEDULED),
    installationIdentity = ToolInstallationIdentity(installationPluginId, installationInstanceId),
)
