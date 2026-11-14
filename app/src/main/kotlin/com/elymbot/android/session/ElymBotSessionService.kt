package com.elymbot.android.session

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.elymbot.android.R
import com.elymbot.android.data.AttachmentEntity
import com.elymbot.android.data.CheckpointEntity
import com.elymbot.android.data.CommandNameEntity
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.ConversationSummaryRow
import com.elymbot.android.data.CredentialVault
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.HistorySummaryEntity
import com.elymbot.android.data.MessageEntity
import com.elymbot.android.data.MessageWithAttachments
import com.elymbot.android.data.ModelAssignmentEntity
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ModelServiceEntity
import com.elymbot.android.data.PersonaEntity
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.data.TurnEntity
import com.elymbot.android.ui.ChatCommand
import com.elymbot.android.ui.ChatMessage
import com.elymbot.android.ui.Conversation
import com.elymbot.android.ui.ConversationSummary
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.android.ui.ImagePosition
import com.elymbot.android.ui.ModelChoice
import com.elymbot.android.ui.ModelConfiguration
import com.elymbot.android.ui.ModelProbe
import com.elymbot.android.ui.ModelRole as UiModelRole
import com.elymbot.android.ui.ModelServiceDraft
import com.elymbot.android.ui.ModelServiceItem
import com.elymbot.android.ui.MessageStatus
import com.elymbot.android.ui.PersonaCard
import com.elymbot.android.ui.ProbeFailure
import com.elymbot.android.ui.ProbeStatus
import com.elymbot.android.ui.ProbeTarget
import com.elymbot.android.ui.UiIssue
import com.elymbot.android.ui.UiServiceException
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.AgentFailure
import com.elymbot.core.contract.AgentFailureKind
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
import com.elymbot.core.contract.PersonaPrompt
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ElymBotSessionService(
    context: Context,
    private val repository: ElymBotDataRepository,
    private val connectionProvider: RoomModelServiceConnectionProvider,
    private val modelGateway: ModelGateway,
    private val agentRuntime: AgentRuntime,
    private val commandParser: SlashCommandParser,
    private val toolSources: () -> List<ToolSource> = { emptyList() },
    private val commandText: CommandTextProvider = AndroidCommandTextProvider(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ElymBotUiService {
    private val appContext = context.applicationContext
    private val viewedSessionId = MutableStateFlow<String?>(null)
    private val liveExecutions = MutableStateFlow<Map<String, LiveExecutionState>>(emptyMap())
    private val commandMessages = MutableStateFlow<Map<String, List<CommandMessageGroup>>>(emptyMap())
    private val activeExecutions = ConcurrentHashMap<String, ActiveExecution>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    override val personas: Flow<List<PersonaCard>> = readyFlow { repository.database.personaDao().observeAll() }
        .map { rows -> rows.map { it.toCard() } }

    override val activePersonaId: Flow<String?> = readyFlow { repository.database.conversationDao().observeMain() }
        .map { it?.personaId }

    override val mainSession: Flow<ConversationSummary?> = combine(
        readyFlow { repository.database.conversationDao().observeMainSummary() },
        readyFlow { repository.database.personaDao().observeAll() },
    ) { row, personaRows ->
        row?.toMainSummary(personaRows.firstOrNull { it.id == row.personaId }?.name.orEmpty())
    }

    override val models: Flow<ModelConfiguration> = combine(
        readyFlow { repository.database.modelConfigurationDao().observeServices() },
        readyFlow { repository.database.modelConfigurationDao().observeAssignments() },
        readyFlow { repository.database.credentialDao().observeServiceIds() },
    ) { services, assignments, credentialIds ->
        val items = services.map { service ->
            ModelServiceItem(
                id = service.id,
                provider = service.provider,
                name = service.name,
                baseUrl = service.baseUrl,
                modelId = service.modelId,
                hasKey = service.id in credentialIds,
                catalog = decodeCatalog(service.catalogValue),
            )
        }
        val choices = assignments.associate { it.role to ModelChoice(it.serviceId, it.modelId) }
        ModelConfiguration(
            services = items,
            text = choices[UiModelRole.Text.name],
            vision = choices[UiModelRole.Vision.name],
        )
    }

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
        emitAll(
            combine(
                repository.database.messageDao().observeForSession(sessionId),
                repository.database.turnDao().observeForSession(sessionId),
                liveExecutions,
                commandMessages,
            ) { messages, turns, live, commands ->
                val latestTurn = turns.lastOrNull()
                val active = live[sessionId]
                val displayed = mergeCommandMessages(messages, commands[sessionId].orEmpty())
                if (active?.text?.isNotEmpty() == true) {
                    displayed += ChatMessage(
                        id = "${active.executionId}:draft",
                        text = active.text,
                        assistant = true,
                    )
                }
                Conversation(
                    messages = displayed,
                    generating = active?.running == true || latestTurn?.status == TURN_STATUS_RUNNING,
                    issue = active?.issue ?: latestTurn?.let(::turnIssue),
                    canRetry = latestTurn?.let { it.retryable && it.status != TURN_STATUS_RUNNING } == true,
                    toolProgressActive = active?.toolProgressActive == true,
                )
            },
        )
    }.flowOn(Dispatchers.IO)

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

    override suspend fun savePersona(persona: PersonaCard): String {
        repository.initialize()
        val name = persona.name.trim()
        if (name.isEmpty()) fail(UiIssue.InvalidInput)
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
            repository.database.withTransaction {
                val personas = repository.database.personaDao()
                val existing = personas.get(id)
                previousCoverPath = existing?.coverPath
                val coverPath = if (persona.cover == null) existing?.coverPath else importedCoverPath
                val order = existing?.sortOrder ?: personas.nextSortOrder()
                val item = PersonaEntity(
                    id = id,
                    name = name,
                    description = persona.description.trim(),
                    tagsValue = persona.tags.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(TAG_SEPARATOR),
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
                savedCoverPath = coverPath

                val conversations = repository.database.conversationDao()
                val main = conversations.getMain()
                val mainPersona = main?.let { personas.get(it.personaId) }
                val activePersonaId = mainPersona?.id ?: personas.getAll().firstOrNull()?.id
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
        } catch (failure: Throwable) {
            repository.assets.delete(importedPath)
            throw failure
        }
        val replacedCoverPath = previousCoverPath?.takeIf { it != savedCoverPath }
        repository.cleanupUnreferencedAssets(listOfNotNull(replacedCoverPath))
        return id
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
            ?: sessionLock(sessionId).withLock { activeExecutions[sessionId] }
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
    }

    override suspend fun retry(sessionId: String) {
        repository.initialize()
        sessionLock(sessionId).withLock {
            val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            val previous = repository.database.turnDao().latest(sessionId) ?: fail(UiIssue.InvalidInput)
            if (!previous.retryable || previous.status == TURN_STATUS_RUNNING) fail(UiIssue.InvalidInput)
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
                    val userMessage = repository.database.messageDao().userMessage(sessionId, turn.sequence)
                        ?: fail(UiIssue.InvalidInput)
                    repository.database.messageDao().deleteAssistantMessages(sessionId, turn.sequence)
                    val retryTurn = turn.copy(
                        executionId = executionId,
                        attemptId = attemptId,
                        status = TURN_STATUS_RUNNING,
                        startedAt = System.currentTimeMillis(),
                        finishedAt = null,
                        failureKind = null,
                        retryable = false,
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

    override suspend fun saveModelService(service: ModelServiceDraft) {
        repository.initialize()
        val baseUrl = service.baseUrl.trim()
        val modelId = service.modelId.trim()
        val providerName = service.provider.trim()
        val name = service.name.trim().ifEmpty { providerName }
        if (baseUrl.isEmpty() || modelId.isEmpty() || providerName.isEmpty() || name.isEmpty()) fail(UiIssue.InvalidInput)
        val id = service.id ?: UUID.randomUUID().toString()
        val newRecord = ModelServiceEntity(
            id = id,
            provider = providerName,
            name = name,
            baseUrl = baseUrl,
            modelId = modelId,
            catalogValue = encodeCatalog(service.catalog),
        )
        repository.database.withTransaction {
            val serviceDao = repository.database.modelConfigurationDao()
            val existing = serviceDao.getService(id)
            serviceDao.upsertService(newRecord)
            if (service.apiKey.isNotBlank()) {
                repository.credentials.store(id, service.apiKey)
            } else if (existing != null && existing.baseUrl != baseUrl) {
                repository.credentials.delete(id)
            }
            if (existing != null && existing.baseUrl != baseUrl) {
                serviceDao.deleteProbes(id)
            }
        }
    }

    override suspend fun deleteModelService(serviceId: String) {
        repository.initialize()
        repository.database.withTransaction {
            repository.database.modelConfigurationDao().deleteAssignmentsForService(serviceId)
            repository.database.modelConfigurationDao().deleteService(serviceId)
        }
    }

    override suspend fun fetchModels(service: ModelServiceDraft): List<String> = withContext(Dispatchers.IO) {
        connectionProvider.withDraftConnection(service) { temporaryId ->
            modelGateway.listModels(temporaryId).distinct()
        }
    }

    override fun probeModel(service: ModelServiceDraft): Flow<ModelProbe> = flow {
        repository.initialize()
        emit(ModelProbe())
        val result = try {
            connectionProvider.withDraftConnection(service) { temporaryId ->
                val report = modelGateway.probe(ModelReference(temporaryId, service.modelId.trim()))
                saveProbeIfPersisted(service, report)
                report.toUiProbe()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            ModelProbe(issue = failure.toUiIssue())
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun setModel(role: UiModelRole, model: ModelChoice?) {
        repository.initialize()
        if (model == null) {
            repository.database.modelConfigurationDao().deleteAssignment(role.name)
            return
        }
        val service = repository.database.modelConfigurationDao().getService(model.serviceId)
        if (model.modelId.isBlank() || service == null ||
            (service.modelId != model.modelId && model.modelId !in decodeCatalog(service.catalogValue))
        ) {
            fail(UiIssue.InvalidInput)
        }
        repository.database.modelConfigurationDao().upsertAssignment(
            ModelAssignmentEntity(role.name, model.serviceId, model.modelId),
        )
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
                    text = "",
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
        var uiIssue: UiIssue? = null
        try {
            val user = repository.database.messageDao().forTurn(execution.sessionId, execution.turnSequence)
                .firstOrNull { it.message.id == execution.userMessageId }
                ?: throw IllegalStateException("Turn input is unavailable")
            val content = buildList {
                if (user.message.text.isNotEmpty()) add(ModelContentPart.Text(user.message.text))
                user.attachments.forEach { attachment ->
                    if (!attachment.mediaType.startsWith("image/")) throw IllegalArgumentException("Unsupported attachment type")
                    val bytes = repository.assets.file(attachment.privatePath).readBytes()
                    add(ModelContentPart.Image(bytes, attachment.mediaType))
                }
                if (isEmpty()) add(ModelContentPart.Text(user.message.text))
            }
            val mainModel = execution.snapshot.primary.selection
            val request = AgentRequest(
                sessionId = execution.sessionId,
                executionId = execution.executionId,
                attemptId = execution.attemptId,
                turnSequence = execution.turnSequence,
                historyUpperBoundExclusive = execution.turnSequence,
                persona = execution.persona,
                primaryModel = mainModel,
                visionModel = execution.snapshot.vision?.selection,
                currentInput = ModelMessage(ModelRole.USER, content),
                toolScope = if (execution.sessionKind == SESSION_KIND_MAIN) ToolScope.MAIN_SESSION else ToolScope.PERSONA_SESSION,
                toolSources = toolSources(),
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
                    is AgentEvent.TextDelta -> {
                        execution.output.append(event.text)
                        publishLive(
                            execution.sessionId,
                            LiveExecutionState(
                                executionId = execution.executionId,
                                attemptId = execution.attemptId,
                                text = execution.output.toString(),
                                issue = null,
                                running = true,
                            ),
                        )
                    }
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
        } catch (failure: Throwable) {
            terminalStatus = failure.toTurnStatus()
            failureKind = failure.toStoredFailureKind()
            retryable = true
            uiIssue = failure.toUiIssue()
        } finally {
            withContext(NonCancellable) {
                finalizeExecution(execution, terminalStatus, failureKind, retryable, uiIssue)
            }
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
    ) {
        sessionLock(execution.sessionId).withLock {
            finishTurnLocked(execution, status, failureKind, retryable, issue)
        }
    }

    private suspend fun finishTurnLocked(
        execution: ActiveExecution,
        status: String,
        failureKind: String?,
        retryable: Boolean,
        issue: UiIssue?,
    ) {
        val finished = repository.database.withTransaction {
            val turn = repository.database.turnDao().get(execution.sessionId, execution.turnSequence)
                ?: return@withTransaction false
            if (turn.status != TURN_STATUS_RUNNING || turn.executionId != execution.executionId || turn.attemptId != execution.attemptId) {
                return@withTransaction false
            }
            val body = execution.output.toString()
            if (body.isNotEmpty() || status != TURN_STATUS_COMPLETED) {
                val index = repository.database.messageDao().nextIndex(execution.sessionId, execution.turnSequence)
                repository.database.messageDao().insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = execution.sessionId,
                        turnSequence = execution.turnSequence,
                        messageIndex = index,
                        role = MESSAGE_ROLE_ASSISTANT,
                        text = body,
                        category = MESSAGE_CATEGORY_HISTORY,
                        status = status.takeIf { it != TURN_STATUS_COMPLETED },
                        author = execution.persona.name,
                    ),
                )
            }
            repository.database.turnDao().update(
                turn.copy(
                    status = status,
                    finishedAt = System.currentTimeMillis(),
                    failureKind = failureKind,
                    retryable = retryable,
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
                    text = execution.output.toString(),
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
    ) {
        if (!execution.finalizationStarted.compareAndSet(false, true)) return
        var finishFailure: Throwable? = null
        try {
            if (status != null) {
                try {
                    if (sessionLockHeld) finishTurnLocked(execution, status, failureKind, retryable, issue)
                    else finishTurn(execution, status, failureKind, retryable, issue)
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
        } finally {
            try {
                execution.snapshot.close()
            } finally {
                activeExecutions.remove(execution.sessionId, execution)
                liveExecutions.update { current ->
                    val state = current[execution.sessionId]
                    if (state?.belongsTo(execution) == true) current - execution.sessionId else current
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
            if (assistant == null) {
                messageDao.insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = execution.sessionId,
                        turnSequence = execution.turnSequence,
                        messageIndex = messageDao.nextIndex(execution.sessionId, execution.turnSequence),
                        role = MESSAGE_ROLE_ASSISTANT,
                        text = execution.output.toString(),
                        category = MESSAGE_CATEGORY_HISTORY,
                        status = TURN_STATUS_INTERRUPTED,
                        author = execution.persona.name,
                    ),
                )
            } else {
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
        val primary = if (retry == null) {
            configuredModel(UiModelRole.Text.name)
        } else {
            configuredModel(retry.primaryServiceId, retry.primaryModelId)
        } ?: fail(UiIssue.MainModelMissing)
        val vision = if (retry == null) configuredModel(UiModelRole.Vision.name) else {
            configuredModel(retry.visionServiceId, retry.visionModelId)
        }
        val leases = linkedMapOf<String, com.elymbot.android.data.ModelConnectionLease>()
        return try {
            suspend fun selection(configured: ConfiguredModel): ModelSelection {
                val lease = leases[configured.service.id] ?: try {
                    connectionProvider.snapshot(configured.service.id, configured.service.baseUrl)
                } catch (_: CredentialVault.CredentialUnavailableException) {
                    fail(UiIssue.Authentication)
                }?.also { leases[configured.service.id] = it } ?: fail(UiIssue.MainModelMissing)
                val reference = ModelReference(lease.serviceId, configured.modelId)
                return ModelSelection(reference, configured.capabilities.copy(model = reference))
            }
            RunSnapshot(
                persona = persona,
                primary = RunModel(primary.service.id, primary.modelId, selection(primary)),
                vision = vision?.let { RunModel(it.service.id, it.modelId, selection(it)) },
                leases = leases.values.toMutableList(),
            )
        } catch (failure: Throwable) {
            leases.values.forEach { it.close() }
            throw failure
        }
    }

    private suspend fun configuredModel(role: String): ConfiguredModel? {
        val assignment = repository.database.modelConfigurationDao().getAssignment(role) ?: return null
        return configuredModel(assignment.serviceId, assignment.modelId)
    }

    private suspend fun configuredModel(serviceId: String?, modelId: String?): ConfiguredModel? {
        if (serviceId == null || modelId.isNullOrBlank()) return null
        val service = repository.database.modelConfigurationDao().getService(serviceId) ?: return null
        val rows = repository.database.modelConfigurationDao().getProbes(serviceId, modelId, service.baseUrl)
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
        return ConfiguredModel(service, modelId, capabilities)
    }

    private suspend fun saveProbeIfPersisted(draft: ModelServiceDraft, report: ModelCapabilityReport) {
        val serviceId = draft.id ?: return
        val service = repository.database.modelConfigurationDao().getService(serviceId) ?: return
        if (service.baseUrl != draft.baseUrl.trim()) return
        repository.database.withTransaction {
            val dao = repository.database.modelConfigurationDao()
            dao.deleteProbesForModel(serviceId, draft.modelId.trim(), service.baseUrl)
            ModelProbeTarget.entries.forEach { target ->
                val status = when (target) {
                    ModelProbeTarget.AVAILABILITY -> report.availability
                    ModelProbeTarget.VISION -> report.vision
                    ModelProbeTarget.NATIVE_STREAMING -> report.nativeStreaming
                    ModelProbeTarget.STRUCTURED_TOOL_CALLS -> report.structuredToolCalls
                }
                val failure = report.failuresByTarget[target]
                dao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = serviceId,
                        modelId = draft.modelId.trim(),
                        baseUrl = service.baseUrl,
                        target = target.name,
                        status = status.name,
                        failureKind = failure?.kind?.name,
                        httpStatusCode = failure?.httpStatusCode,
                    ),
                )
            }
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
                else restoreCheckpoint(sessionId, number)
            }
            CommandAction.Reset -> {
                val current = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
                val checkpoint = repository.database.checkpointDao().getForSession(sessionId)
                    .firstOrNull { it.id == current.currentCheckpointId } ?: fail(UiIssue.InvalidInput)
                restoreCheckpoint(sessionId, checkpoint.displayNumber)
            }
            CommandAction.Mark -> createCheckpoint(sessionId)
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

    private suspend fun createCheckpoint(sessionId: String): String {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        val latest = repository.database.turnDao().latest(sessionId)
        if (latest?.status != TURN_STATUS_COMPLETED) return commandText.noMarkBoundary()
        val checkpoints = repository.database.checkpointDao().getForSession(sessionId)
        val number = (checkpoints.maxOfOrNull { it.displayNumber } ?: 0) + 1
        val point = CheckpointEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            displayNumber = number,
            boundaryTurnSequence = latest.sequence,
            creationOrder = repository.database.checkpointDao().nextCreationOrder(sessionId),
            createdAt = System.currentTimeMillis(),
        )
        repository.database.withTransaction {
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            repository.database.checkpointDao().insert(point)
            repository.database.conversationDao().update(session.copy(currentCheckpointId = point.id))
        }
        return commandText.checkpointMarked(number)
    }

    private suspend fun restoreCheckpoint(sessionId: String, displayNumber: Int): String {
        var restored: CheckpointEntity? = null
        var removedAssetPaths: List<String> = emptyList()
        repository.database.withTransaction {
            val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
            if (repository.database.turnDao().isRunning(sessionId)) fail(UiIssue.Busy)
            val selected = repository.database.checkpointDao().getByNumber(sessionId, displayNumber)
                ?: fail(UiIssue.InvalidInput)
            removedAssetPaths = repository.database.attachmentDao().pathsAfterTurn(sessionId, selected.boundaryTurnSequence)
            repository.database.turnDao().deleteAfter(sessionId, selected.boundaryTurnSequence)
            repository.database.historySummaryDao().deleteAfter(sessionId, selected.boundaryTurnSequence)
            repository.database.checkpointDao().deleteAfter(sessionId, selected.creationOrder)
            repository.database.conversationDao().update(
                session.copy(currentCheckpointId = selected.id, unread = false, lastActivityAt = System.currentTimeMillis()),
            )
            restored = selected
        }
        val point = restored ?: fail(UiIssue.Failed)
        repository.cleanupUnreferencedAssets(removedAssetPaths)
        commandMessages.update { it - sessionId }
        return commandText.checkpointRestored(point.displayNumber)
    }

    private suspend fun recordCommand(sessionId: String, commandInput: String, receipt: String) {
        val session = repository.database.conversationDao().get(sessionId) ?: fail(UiIssue.InvalidInput)
        val persona = repository.database.personaDao().get(session.personaId) ?: fail(UiIssue.InvalidInput)
        val messages = listOf(
            ChatMessage(UUID.randomUUID().toString(), text = commandInput),
            ChatMessage(UUID.randomUUID().toString(), text = receipt, assistant = true, author = persona.name),
        )
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

    private suspend fun configuredModel(role: UiModelRole): ConfiguredModel? = configuredModel(role.name)

    private fun Map<String, String>.status(target: ModelProbeTarget): CapabilityStatus =
        this[target.name]?.let { runCatching { CapabilityStatus.valueOf(it) }.getOrNull() } ?: CapabilityStatus.UNKNOWN

    private fun ModelCapabilityReport.toUiProbe(): ModelProbe {
        val failures = failuresByTarget.mapNotNull { (target, failure) ->
            val uiTarget = when (target) {
                ModelProbeTarget.AVAILABILITY -> ProbeTarget.Availability
                ModelProbeTarget.VISION -> ProbeTarget.Vision
                ModelProbeTarget.NATIVE_STREAMING -> ProbeTarget.Streaming
                ModelProbeTarget.STRUCTURED_TOOL_CALLS -> null
            } ?: return@mapNotNull null
            uiTarget to ProbeFailure(failure.kind.toUiIssue(), failure.httpStatusCode)
        }.toMap()
        return ModelProbe(
            availability = availability.toUiStatus(),
            vision = vision.toUiStatus(),
            streaming = nativeStreaming.toUiStatus(),
            issue = failures.values.firstOrNull()?.issue,
            failuresByTarget = failures,
        )
    }

    private fun CapabilityStatus.toUiStatus(): ProbeStatus = when (this) {
        CapabilityStatus.SUPPORTED -> ProbeStatus.Supported
        CapabilityStatus.UNSUPPORTED -> ProbeStatus.Unsupported
        CapabilityStatus.UNKNOWN -> ProbeStatus.Unknown
    }

    private fun ModelProbeFailureKind.toUiIssue(): UiIssue = when (this) {
        ModelProbeFailureKind.NETWORK -> UiIssue.Connection
        ModelProbeFailureKind.AUTHENTICATION -> UiIssue.Authentication
        ModelProbeFailureKind.HTTP -> UiIssue.ServiceResponse
        ModelProbeFailureKind.API_REJECTED -> UiIssue.ServiceRejected
        ModelProbeFailureKind.PROTOCOL -> UiIssue.ServiceProtocol
        ModelProbeFailureKind.TIMED_OUT -> UiIssue.TimedOut
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

    private fun Throwable.toUiIssue(): UiIssue = when (this) {
        is UiServiceException -> issue
        is CredentialVault.CredentialUnavailableException -> UiIssue.Authentication
        is ModelRequestException.Network -> UiIssue.Connection
        is ModelRequestException.HttpStatus -> if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || statusCode == HttpURLConnection.HTTP_FORBIDDEN) UiIssue.Authentication else UiIssue.ServiceResponse
        is ModelRequestException.ApiRejected -> if (
            httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED || httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN
        ) UiIssue.Authentication else UiIssue.ServiceRejected
        is ModelRequestException.Protocol -> UiIssue.ServiceProtocol
        is ModelRequestException.StreamInterrupted -> UiIssue.Connection
        is ModelRequestException.ContextLimit -> UiIssue.InvalidInput
        is ModelRequestException.TimedOut -> UiIssue.TimedOut
        is FileNotFoundException -> UiIssue.InvalidInput
        else -> UiIssue.Failed
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

    private fun decodeCatalog(value: String): List<String> = value.split(CATALOG_SEPARATOR).filter(String::isNotEmpty)

    private fun encodeCatalog(items: List<String>): String = items.distinct().joinToString(CATALOG_SEPARATOR)

    private fun fail(issue: UiIssue): Nothing = throw UiServiceException(issue)

    private data class ConfiguredModel(
        val service: ModelServiceEntity,
        val modelId: String,
        val capabilities: ModelCapabilityReport,
    )

    private data class RunModel(
        val persistedServiceId: String,
        val modelId: String,
        val selection: ModelSelection,
    )

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
        val text: String,
        val issue: UiIssue?,
        val running: Boolean,
        val toolProgressActive: Boolean = false,
    )

    private data class CommandMessageGroup(
        val beforeTurnSequence: Long,
        val messages: List<ChatMessage>,
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
    ) {
        val output = StringBuilder()
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
        const val TAG_SEPARATOR = "\u001f"
        const val CATALOG_SEPARATOR = "\u001f"

        fun emptyCheckpointId(sessionId: String) = "empty:$sessionId"
    }
}
