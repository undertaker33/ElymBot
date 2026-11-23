package com.elymbot.core.contract

import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/** A plugin that owns one host-managed messaging platform connection. */
interface PlatformPluginHandle : PluginHandle {
    val platformId: String

    /** Short-lived observable state for the host UI; login challenge data must not be persisted. */
    val state: StateFlow<PlatformRuntimeState>

    /** Prepare and start platform resources only after the host has activated this instance. */
    suspend fun start(runtimeHost: PlatformRuntimeHost, inboundSink: PlatformInboundSink)

    suspend fun refreshLogin()

    suspend fun reconnect()

    suspend fun logout()

    suspend fun send(request: PlatformOutboundMessage): PlatformDeliveryResult

    /** Copies one plugin-owned media reference into an App-owned destination with a hard byte cap. */
    suspend fun copyMedia(
        reference: MediaResourceReference,
        destination: OutputStream,
        maxBytes: Long,
    ): PlatformMediaReadResult
}

/** The App binds this capability to the active plugin's private data directory. */
interface PlatformRuntimeHost {
    suspend fun launchGuest(
        rootfsRelativePath: String,
        guestEntry: String,
    ): PlatformGuestProcess
}

/** Host-owned process control without exposing Android or java.lang.Process types to a plugin. */
interface PlatformGuestProcess {
    val isAlive: Boolean

    suspend fun awaitExit(timeoutMillis: Long): Int?

    suspend fun stop()
}

fun interface PlatformInboundSink {
    /** The host rechecks active installation identity and route authorization before accepting. */
    suspend fun accept(message: PlatformInboundEnvelope): PlatformInboundResult
}

/** Normalized input plus the platform policy outcome needed by the host's history boundary. */
data class PlatformInboundEnvelope(
    val message: AgentMessage,
    val processing: PlatformInboundProcessing,
    val historyVisibility: PlatformHistoryVisibility,
)

enum class PlatformInboundProcessing {
    /** Accepted message starts one host Agent turn after App binding authorization. */
    TRIGGER_AGENT,

    /** Accepted public material is stored as history and never starts an Agent turn. */
    CONTEXT_ONLY,
}

enum class PlatformHistoryVisibility {
    PRIVATE,
    GROUP_MEMBER,
    GROUP_PUBLIC,
}

sealed interface PlatformInboundResult {
    data object Accepted : PlatformInboundResult

    data object Duplicate : PlatformInboundResult

    data class Rejected(val reason: PlatformInboundRejection) : PlatformInboundResult
}

enum class PlatformInboundRejection {
    INACTIVE_INSTANCE,
    UNAUTHORIZED_ROUTE,
    INVALID_MESSAGE,
    PERSISTENCE,
}

data class PlatformOutboundMessage(
    val target: MessageRoute,
    val segments: List<AgentMessageSegment>,
    val reference: MessageReference? = null,
)

sealed interface PlatformDeliveryResult {
    data class Delivered(val remoteMessageId: String? = null) : PlatformDeliveryResult

    data class Failed(
        val reason: PlatformDeliveryFailure,
        val retryable: Boolean,
    ) : PlatformDeliveryResult

    /** The remote side may have applied the action; the host must reconcile before retrying. */
    data class Unknown(val reason: PlatformDeliveryFailure) : PlatformDeliveryResult
}

enum class PlatformDeliveryFailure {
    NOT_CONNECTED,
    INVALID_TARGET,
    UNSUPPORTED_MEDIA,
    MESSAGE_REJECTED,
    RESPONSE_INVALID,
    CONNECTION_LOST,
    INTERNAL,
}

data class PlatformRuntimeState(
    val phase: PlatformRuntimePhase,
    val progressPercent: Int? = null,
    val accountId: String? = null,
    /** UI-only QR payload; implementations must not write it to logs, events, or model context. */
    val loginChallenge: PlatformLoginChallenge? = null,
    val failure: PlatformRuntimeFailure? = null,
)

data class PlatformLoginChallenge(
    val qrContent: String,
    val expiresAtEpochMillis: Long? = null,
)

enum class PlatformRuntimePhase {
    STOPPED,
    PREPARING,
    STARTING,
    WAITING_FOR_LOGIN,
    CONNECTING,
    CONNECTED,
    STOPPING,
    FAILED,
}

data class PlatformRuntimeFailure(
    val reason: PlatformRuntimeFailureReason,
    val retryable: Boolean,
)

enum class PlatformRuntimeFailureReason {
    ASSET_PREPARATION,
    STARTUP,
    AUTHENTICATION,
    CONNECTION,
    PROTOCOL,
    INTERNAL,
}

sealed interface PlatformMediaReadResult {
    data class Copied(
        val contentType: String,
        val sizeBytes: Long,
        val displayName: String? = null,
    ) : PlatformMediaReadResult

    data object NotFound : PlatformMediaReadResult

    data object TooLarge : PlatformMediaReadResult

    data object Unsupported : PlatformMediaReadResult

    data object Failed : PlatformMediaReadResult
}
