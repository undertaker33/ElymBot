package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.PlatformDeliveryFailure
import com.elymbot.core.contract.PlatformDeliveryResult
import com.elymbot.core.contract.PlatformGuestProcess
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.PlatformMediaReadResult
import com.elymbot.core.contract.PlatformOutboundMessage
import com.elymbot.core.contract.PlatformPluginHandle
import com.elymbot.core.contract.PlatformRuntimeFailure
import com.elymbot.core.contract.PlatformRuntimeFailureReason
import com.elymbot.core.contract.PlatformRuntimePhase
import com.elymbot.core.contract.PlatformRuntimeState
import com.elymbot.core.contract.PlatformLoginChallenge
import com.elymbot.core.contract.PlatformQrMatrix
import com.elymbot.core.contract.PlatformRuntimeHost
import com.elymbot.core.contract.PluginEntrypoint
import com.elymbot.core.contract.PluginHandle
import com.elymbot.core.contract.PluginHostContext
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsHandle
import com.elymbot.core.contract.QqPlatformSettingsRejection
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformCommandSettingsHandle
import com.elymbot.core.contract.QqPlatformCommandSettingsSnapshot
import com.elymbot.core.contract.QqPlatformCommandSettingsUpdateResult
import com.elymbot.core.contract.MessageReference
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Public no-argument entrypoint declared by the signed QQ plugin package. */
class QqPluginEntrypoint : PluginEntrypoint {
    override fun prepare(context: PluginHostContext): PluginHandle = QqPlatformPluginHandleImpl(context)
}

internal class QqPlatformPluginHandleImpl(
    context: PluginHostContext,
    resourceLoader: ClassLoader = QqPlatformPluginHandleImpl::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
    private val runtimeAssetPreparationOverride: (suspend () -> Unit)? = null,
    private val oneBotServerStartOverride: (() -> Unit)? = null,
) : PlatformPluginHandle, QqPlatformSettingsHandle, QqPlatformCommandSettingsHandle {
    private val dataDirectory = context.dataDirectory.canonicalFile
    private val assets = QqRuntimeAssets(dataDirectory, resourceLoader)
    private val settingsStore = QqPlatformSettingsStore(dataDirectory)
    private val loginApi = QqNapCatLoginApi(assets.runtimeSecretFile())
    private val mediaSources = QqMediaSourceRegistry()
    private val operationMutex = Mutex()
    private val managementCommandHandler = QqManagementCommandHandler(
        settingsStore = settingsStore,
        operationMutex = operationMutex,
        isCurrentAccount = ::isCurrentAccount,
    )
    private val inboundAdapter = QqOneBotInboundAdapter(
        mediaSources = mediaSources,
        commandHandler = managementCommandHandler,
        currentSettingsProvider = { settingsStore.settings.value.settings },
        currentCommandSettingsProvider = { settingsStore.commandSettings.value.settings },
        commandReplySender = ::sendManagementReply,
    )
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replayWakeups = Channel<Unit>(Channel.CONFLATED)
    private val qrChallengeLock = Any()
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(PlatformRuntimeState(PlatformRuntimePhase.STOPPED))

    @Volatile
    private var runtimeHost: PlatformRuntimeHost? = null

    @Volatile
    private var activeInboundSink: PlatformInboundSink? = null

    @Volatile
    private var guestProcess: PlatformGuestProcess? = null

    @Volatile
    private var oneBotServer: QqOneBotReverseWebSocketServer? = null

    @Volatile
    private var monitorJob: Job? = null

    @Volatile
    private var stashReplayJob: Job? = null

    @Volatile
    private var cachedQrContent: String? = null

    @Volatile
    private var cachedQrMatrix: PlatformQrMatrix? = null

    override val platformId: String = QQ_PLATFORM_ID
    override val toolSources: List<com.elymbot.core.contract.ToolSource> = emptyList()
    override val state: StateFlow<PlatformRuntimeState> = mutableState.asStateFlow()
    override val settings: StateFlow<QqPlatformSettingsSnapshot> = settingsStore.settings
    override val commandSettings: StateFlow<QqPlatformCommandSettingsSnapshot> = settingsStore.commandSettings

    override suspend fun start(runtimeHost: PlatformRuntimeHost, inboundSink: PlatformInboundSink) {
        operationMutex.withLock {
            check(!closed.get()) { "QQ plugin installation is closed" }
            this.runtimeHost = runtimeHost
            this.activeInboundSink = inboundSink
            if (guestProcess?.isAlive == true) return@withLock
            try {
                startGuestLocked(runtimeHost)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { stopGuestLocked() }
                mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STOPPED)
                throw cancelled
            } catch (failure: Exception) {
                cleanupAfterFailure()
                mutableState.value = PlatformRuntimeState(
                    phase = PlatformRuntimePhase.FAILED,
                    failure = PlatformRuntimeFailure(
                        reason = if (failure is QqRuntimePreparationException) {
                            PlatformRuntimeFailureReason.ASSET_PREPARATION
                        } else {
                            PlatformRuntimeFailureReason.STARTUP
                        },
                        retryable = true,
                    ),
                )
                throw failure
            }
        }
    }

    override suspend fun refreshLogin() {
        check(guestProcess?.isAlive == true) { "QQ runtime is not running" }
        clearQrChallengeCache()
        mutableState.value = mutableState.value.copy(loginChallenge = null)
        loginApi.refreshQr()
        refreshObservedLoginState()
    }

    override suspend fun stop() {
        operationMutex.withLock {
            check(!closed.get()) { "QQ plugin installation is closed" }
            stopGuestLocked()
        }
    }

    override suspend fun reconnect() {
        operationMutex.withLock {
            check(!closed.get()) { "QQ plugin installation is closed" }
            val host = runtimeHost ?: throw IllegalStateException("QQ runtime has not been started")
            try {
                stopGuestLocked()
                startGuestLocked(host)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { stopGuestLocked() }
                mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STOPPED)
                throw cancelled
            } catch (failure: Exception) {
                cleanupAfterFailure()
                mutableState.value = PlatformRuntimeState(
                    phase = PlatformRuntimePhase.FAILED,
                    failure = PlatformRuntimeFailure(
                        if (failure is QqRuntimePreparationException) {
                            PlatformRuntimeFailureReason.ASSET_PREPARATION
                        } else {
                            PlatformRuntimeFailureReason.STARTUP
                        },
                        retryable = true,
                    ),
                )
                throw failure
            }
        }
    }

    override suspend fun logout() {
        operationMutex.withLock {
            check(!closed.get()) { "QQ plugin installation is closed" }
            inboundAdapter.clearStashes()
            val host = runtimeHost ?: return@withLock
            if (guestProcess?.isAlive != true) return@withLock

            stopGuestLocked()
            loginApi.invalidateCredential()
            delay(LOGOUT_RESTART_DELAY_MILLIS)
            try {
                startGuestLocked(host)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { stopGuestLocked() }
                mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STOPPED)
                throw cancelled
            } catch (failure: Exception) {
                cleanupAfterFailure()
                mutableState.value = PlatformRuntimeState(
                    phase = PlatformRuntimePhase.FAILED,
                    failure = PlatformRuntimeFailure(
                        if (failure is QqRuntimePreparationException) {
                            PlatformRuntimeFailureReason.ASSET_PREPARATION
                        } else {
                            PlatformRuntimeFailureReason.STARTUP
                        },
                        retryable = true,
                    ),
                )
                throw IOException("QQ logout restart failed", failure)
            }

            repeat(LOGOUT_STATUS_ATTEMPTS) {
                delay(LOGOUT_STATUS_INTERVAL_MILLIS)
                val login = try {
                    loginApi.status()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (guestProcess?.isAlive == true && login != null && !login.isLoggedIn) {
                    mutableState.value = PlatformRuntimeState(
                        phase = PlatformRuntimePhase.WAITING_FOR_LOGIN,
                        progressPercent = readProgress(),
                        loginChallenge = loginChallenge(login.qrContent),
                    )
                    return@withLock
                }
            }
            throw IOException("QQ logout could not be confirmed after runtime restart")
        }
    }

    override suspend fun send(request: PlatformOutboundMessage): PlatformDeliveryResult {
        val destination = parseTarget(request.target)
            ?: return PlatformDeliveryResult.Failed(PlatformDeliveryFailure.INVALID_TARGET, retryable = false)
        val unsupportedSegment = request.segments.any { it !is AgentMessageSegment.Text }
        if (unsupportedSegment) {
            return PlatformDeliveryResult.Failed(PlatformDeliveryFailure.UNSUPPORTED_MEDIA, retryable = false)
        }
        val text = request.segments.filterIsInstance<AgentMessageSegment.Text>().joinToString("") { it.value }
        if (text.isBlank() && request.reference == null) {
            return PlatformDeliveryResult.Failed(PlatformDeliveryFailure.MESSAGE_REJECTED, retryable = false)
        }
        val message = buildJsonArray {
            request.reference?.takeIf { isSameRoute(it.route, request.target) }?.let { reference ->
                add(buildJsonObject {
                    put("type", "reply")
                    put("data", buildJsonObject { put("id", reference.messageId) })
                })
            }
            if (text.isNotEmpty()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("data", buildJsonObject { put("text", text) })
                })
            }
        }
        val params = buildJsonObject {
            when (destination) {
                is QqOutboundTarget.Private -> put("user_id", destination.id)
                is QqOutboundTarget.Group -> put("group_id", destination.id)
            }
            put("message", message)
        }
        return when (val result = oneBotServer?.sendAction(destination.action, params)
            ?: QqOneBotActionResult.NotConnected
        ) {
            QqOneBotActionResult.NotConnected ->
                PlatformDeliveryResult.Failed(PlatformDeliveryFailure.NOT_CONNECTED, retryable = true)
            QqOneBotActionResult.Unknown ->
                PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.CONNECTION_LOST)
            is QqOneBotActionResult.Response -> result.json.toQqDeliveryResult()
        }
    }

    private suspend fun sendManagementReply(
        event: OneBotInboundEvent,
        text: String,
    ): PlatformDeliveryResult {
        val target = event.message.target
            ?: return PlatformDeliveryResult.Failed(PlatformDeliveryFailure.INVALID_TARGET, retryable = false)
        return send(
            PlatformOutboundMessage(
                target = target,
                segments = listOf(AgentMessageSegment.Text(text)),
            ),
        )
    }

    override suspend fun copyMedia(
        reference: com.elymbot.core.contract.MediaResourceReference,
        destination: OutputStream,
        maxBytes: Long,
    ): PlatformMediaReadResult = withContext(Dispatchers.IO) {
        val source = mediaSources.find(reference.resourceId) ?: return@withContext PlatformMediaReadResult.NotFound
        assets.copyMedia(source, destination, maxBytes)
    }

    override suspend fun updateSettings(
        expectedRevision: Long,
        settings: QqPlatformSettings,
    ): QqPlatformSettingsUpdateResult {
        if (closed.get()) return QqPlatformSettingsUpdateResult.Rejected(QqPlatformSettingsRejection.STORE_UNAVAILABLE)
        val result = settingsStore.updateSettings(expectedRevision, settings)
        if (result is QqPlatformSettingsUpdateResult.Saved) wakeStashReplayIfPending()
        return result
    }

    override suspend fun updateCommandSettings(
        expectedRevision: Long,
        settings: QqPlatformCommandSettings,
    ): QqPlatformCommandSettingsUpdateResult {
        if (closed.get()) return QqPlatformCommandSettingsUpdateResult.Rejected
        val result = settingsStore.updateCommandSettings(expectedRevision, settings)
        if (result is QqPlatformCommandSettingsUpdateResult.Saved) wakeStashReplayIfPending()
        return result
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        inboundAdapter.clearStashes()
        mutableState.value = mutableState.value.copy(phase = PlatformRuntimePhase.STOPPING)
        oneBotServer?.close()
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanupScope.launch {
            operationMutex.withLock {
                monitorJob?.cancelAndJoin()
                monitorJob = null
                stashReplayJob?.cancelAndJoin()
                stashReplayJob = null
                oneBotServer?.close()
                oneBotServer = null
                guestProcess?.let { process ->
                    guestProcess = null
                    try {
                        process.stop()
                    } catch (_: Exception) {
                        // The plugin is closing; the host process handle owns the final shutdown boundary.
                    }
                }
                runtimeScope.cancel()
                clearQrChallengeCache()
                mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STOPPED)
            }
            cleanupScope.cancel()
        }
    }

    private suspend fun launchGuest(host: PlatformRuntimeHost) {
        val process = host.launchGuest(assets.rootfsRelativePath, assets.guestEntryPath)
        if (closed.get()) {
            process.stop()
            throw CancellationException("QQ plugin installation closed during startup")
        }
        guestProcess = process
    }

    private suspend fun startGuestLocked(host: PlatformRuntimeHost) {
        mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.PREPARING)
        val preparationOverride = runtimeAssetPreparationOverride
        if (preparationOverride == null) {
            withContext(Dispatchers.IO) { assets.prepare() }
        } else {
            preparationOverride()
        }
        currentCoroutineContext().ensureActive()
        check(!closed.get()) { "QQ plugin installation is closed" }
        val serverStartOverride = oneBotServerStartOverride
        if (serverStartOverride == null) ensureOneBotServer() else serverStartOverride()
        mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STARTING, progressPercent = 0)
        launchGuest(host)
        currentCoroutineContext().ensureActive()
        startMonitor()
        startStashReplay()
        wakeStashReplayIfPending()
    }

    private fun ensureOneBotServer() {
        if (oneBotServer?.isConnected == true || oneBotServer != null) return
        if (activeInboundSink == null) throw IllegalStateException("QQ inbound sink is unavailable")
        val server = QqOneBotReverseWebSocketServer(
            onEvent = ::acceptOneBotPayload,
            onConnectionChanged = ::onOneBotConnectionChanged,
            token = assets.oneBotToken(),
        )
        server.start(runtimeScope)
        oneBotServer = server
    }

    internal suspend fun acceptOneBotPayload(payload: String) {
        val sink = activeInboundSink ?: return
        try {
            inboundAdapter.accept(payload, settings.value.settings, sink)
        } finally {
            wakeStashReplayIfPending()
        }
    }

    private fun startStashReplay() {
        if (stashReplayJob?.isActive == true) return
        stashReplayJob = runtimeScope.launch {
            while (currentCoroutineContext().isActive && !closed.get()) {
                replayWakeups.receive()
                while (currentCoroutineContext().isActive && !closed.get()) {
                    val sink = activeInboundSink ?: break
                    try {
                        inboundAdapter.releaseReady(settings.value.settings, sink)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Rejected or failed host acceptance releases its event reservation; keep watching other stashes.
                    }
                    if (!inboundAdapter.hasPendingStash()) break
                    delay(STASH_REPLAY_POLL_INTERVAL_MILLIS)
                }
            }
        }
    }

    private fun wakeStashReplayIfPending() {
        if (inboundAdapter.hasPendingStash()) replayWakeups.trySend(Unit)
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = runtimeScope.launch {
            monitorRuntime()
        }
    }

    private suspend fun monitorRuntime() {
        while (currentCoroutineContext().isActive && !closed.get()) {
            val process = guestProcess
            if (process == null || !process.isAlive) {
                operationMutex.withLock {
                    mutableState.value = PlatformRuntimeState(
                        phase = PlatformRuntimePhase.FAILED,
                        failure = PlatformRuntimeFailure(PlatformRuntimeFailureReason.STARTUP, retryable = true),
                    )
                }
                return
            }
            val progress = readProgress()
            if (loginApi.isReachable()) {
                try {
                    val login = loginApi.status()
                    val challenge = if (login.isLoggedIn) {
                        clearQrChallengeCache()
                        null
                    } else {
                        loginChallenge(login.qrContent)
                    }
                    val phase = when {
                        login.isLoggedIn && login.coreReady && oneBotServer?.isConnected == true ->
                            PlatformRuntimePhase.CONNECTED
                        login.isLoggedIn -> PlatformRuntimePhase.CONNECTING
                        login.qrContent != null -> PlatformRuntimePhase.WAITING_FOR_LOGIN
                        else -> PlatformRuntimePhase.STARTING
                    }
                    operationMutex.withLock {
                        clearStashesForAccountChange(login)
                        mutableState.value = PlatformRuntimeState(
                            phase = phase,
                            progressPercent = progress,
                            accountId = login.accountId,
                            loginChallenge = challenge,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    operationMutex.withLock {
                        mutableState.value = mutableState.value.copy(
                            phase = if (oneBotServer?.isConnected == true) {
                                PlatformRuntimePhase.CONNECTING
                            } else {
                                PlatformRuntimePhase.STARTING
                            },
                            progressPercent = progress,
                            failure = PlatformRuntimeFailure(PlatformRuntimeFailureReason.CONNECTION, retryable = true),
                        )
                    }
                }
            } else {
                clearQrChallengeCache()
                operationMutex.withLock {
                    mutableState.value = mutableState.value.copy(
                        phase = PlatformRuntimePhase.STARTING,
                        progressPercent = progress,
                        loginChallenge = null,
                    )
                }
            }
            delay(LOGIN_POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun refreshObservedLoginState() {
        if (!loginApi.isReachable()) throw IOException("NapCat login service is not available")
        val login = loginApi.status()
        val challenge = if (login.isLoggedIn) {
            clearQrChallengeCache()
            null
        } else {
            loginChallenge(login.qrContent)
        }
        operationMutex.withLock {
            clearStashesForAccountChange(login)
            mutableState.value = PlatformRuntimeState(
                phase = when {
                    login.isLoggedIn && login.coreReady && oneBotServer?.isConnected == true -> PlatformRuntimePhase.CONNECTED
                    login.isLoggedIn -> PlatformRuntimePhase.CONNECTING
                    login.qrContent != null -> PlatformRuntimePhase.WAITING_FOR_LOGIN
                    else -> PlatformRuntimePhase.STARTING
                },
                progressPercent = readProgress(),
                accountId = login.accountId,
                loginChallenge = challenge,
            )
        }
    }

    private suspend fun stopGuestLocked() {
        inboundAdapter.clearStashes()
        monitorJob?.cancelAndJoin()
        monitorJob = null
        stashReplayJob?.cancelAndJoin()
        stashReplayJob = null
        oneBotServer?.close()
        oneBotServer = null
        guestProcess?.let { process ->
            guestProcess = null
            process.stop()
        }
        clearQrChallengeCache()
        mutableState.value = PlatformRuntimeState(PlatformRuntimePhase.STOPPED)
    }

    private fun loginChallenge(content: String?): PlatformLoginChallenge? {
        if (content.isNullOrBlank()) {
            clearQrChallengeCache()
            return null
        }
        synchronized(qrChallengeLock) {
            if (cachedQrContent != content) {
                cachedQrContent = content
                cachedQrMatrix = QqLoginQrEncoder.encode(content)
            }
            return cachedQrMatrix?.let(::PlatformLoginChallenge)
        }
    }

    private fun clearQrChallengeCache() {
        synchronized(qrChallengeLock) {
            cachedQrContent = null
            cachedQrMatrix = null
        }
    }

    private suspend fun cleanupAfterFailure() {
        try {
            stopGuestLocked()
        } catch (_: Exception) {
            oneBotServer?.close()
            oneBotServer = null
            stashReplayJob?.cancel()
            stashReplayJob = null
            guestProcess = null
        }
    }

    private fun readProgress(): Int? = runCatching {
        val file = assets.guestProgressFile()
        if (!file.isFile || file.length() > MAX_PROGRESS_FILE_BYTES) return null
        file.readText(Charsets.UTF_8).trim().toIntOrNull()?.coerceIn(0, 100)
    }.getOrNull()

    private fun onOneBotConnectionChanged(connected: Boolean) {
        val current = mutableState.value
        if (current.phase == PlatformRuntimePhase.STOPPED || current.phase == PlatformRuntimePhase.STOPPING ||
            current.phase == PlatformRuntimePhase.FAILED
        ) return
        mutableState.value = if (connected) {
            current.copy(phase = PlatformRuntimePhase.CONNECTING, failure = null)
        } else if (current.phase == PlatformRuntimePhase.CONNECTED) {
            current.copy(
                phase = PlatformRuntimePhase.CONNECTING,
                failure = PlatformRuntimeFailure(PlatformRuntimeFailureReason.CONNECTION, retryable = true),
            )
        } else {
            current
        }
    }

    private fun parseTarget(route: MessageRoute): QqOutboundTarget? {
        if (route.channelId != QQ_PLATFORM_ID || route.accountId.isNullOrBlank()) return null
        val activeAccount = mutableState.value.accountId
        if (activeAccount != null && route.accountId != activeAccount) return null
        val privateId = route.conversationId.removePrefix("private:").takeIf {
            route.conversationId.startsWith("private:") && QQ_ID_PATTERN.matches(it)
        }
        if (privateId != null) return QqOutboundTarget.Private(privateId)
        val groupId = route.conversationId.removePrefix("group:").takeIf {
            route.conversationId.startsWith("group:") && QQ_ID_PATTERN.matches(it)
        } ?: return null
        return QqOutboundTarget.Group(groupId)
    }

    private fun isCurrentAccount(accountId: String): Boolean {
        val current = mutableState.value
        return !closed.get() && current.phase == PlatformRuntimePhase.CONNECTED &&
            current.accountId == accountId && guestProcess?.isAlive == true && oneBotServer?.isConnected == true
    }

    private fun clearStashesForAccountChange(login: QqNapCatLoginStatus) {
        val currentAccount = mutableState.value.accountId
        if (!login.isLoggedIn || currentAccount != null && currentAccount != login.accountId) {
            inboundAdapter.clearStashes()
        }
    }

    private fun isSameRoute(first: MessageRoute, second: MessageRoute): Boolean =
        first.channelId == second.channelId && first.conversationId == second.conversationId &&
            first.accountId == second.accountId

    private sealed interface QqOutboundTarget {
        val id: String
        val action: String

        data class Private(override val id: String) : QqOutboundTarget {
            override val action: String = "send_private_msg"
        }

        data class Group(override val id: String) : QqOutboundTarget {
            override val action: String = "send_group_msg"
        }
    }

    private companion object {
        const val LOGIN_POLL_INTERVAL_MILLIS = 2_000L
        const val LOGOUT_RESTART_DELAY_MILLIS = 1_500L
        const val LOGOUT_STATUS_INTERVAL_MILLIS = 3_000L
        const val LOGOUT_STATUS_ATTEMPTS = 12
        const val STASH_REPLAY_POLL_INTERVAL_MILLIS = 100L
        const val MAX_PROGRESS_FILE_BYTES = 16L
        val QQ_ID_PATTERN = Regex("[0-9]{1,20}")
    }
}

internal fun JsonObject.toQqDeliveryResult(): PlatformDeliveryResult {
    val status = (this["status"] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
    val retcodePrimitive = this["retcode"] as? JsonPrimitive
    val retcode = retcodePrimitive
        ?.takeUnless(JsonPrimitive::isString)
        ?.contentOrNull
        ?.toIntOrNull()

    if (status == "ok" && retcode == SUCCESS_RETCODE) {
        val messageId = (this["data"] as? JsonObject)
            ?.get("message_id")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
        return PlatformDeliveryResult.Delivered(messageId)
    }

    if (status == "failed" && retcode != null && retcode != SUCCESS_RETCODE && retcode != ASYNC_RETCODE) {
        return PlatformDeliveryResult.Failed(PlatformDeliveryFailure.MESSAGE_REJECTED, retryable = false)
    }

    return PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.RESPONSE_INVALID)
}

private const val SUCCESS_RETCODE = 0
private const val ASYNC_RETCODE = 1
