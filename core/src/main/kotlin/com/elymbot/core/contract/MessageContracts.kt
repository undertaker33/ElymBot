package com.elymbot.core.contract

/** A host-normalized route identity. Values are supplied by the trusted host adapter. */
data class MessageRoute(
    val channelId: String,
    val conversationId: String,
    val accountId: String? = null,
    val actorId: String? = null,
)

/** A stable reference to a message on a route. */
data class MessageReference(
    val route: MessageRoute,
    val messageId: String,
)

/** Opaque reference to media held by the host, without Android or plugin SDK types. */
data class MediaResourceReference(
    val resourceId: String,
    val mediaType: String,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
)

/** Typed, host-normalized content segments accepted at the Agent boundary. */
sealed interface AgentMessageSegment {
    data class Text(val value: String) : AgentMessageSegment

    data class Image(val resource: MediaResourceReference) : AgentMessageSegment

    data class Audio(val resource: MediaResourceReference) : AgentMessageSegment

    data class Video(val resource: MediaResourceReference) : AgentMessageSegment

    data class File(val resource: MediaResourceReference) : AgentMessageSegment
}

/**
 * Normalized inbound message metadata. The application or platform adapter creates
 * origin and target identities after validating them; model and plugin payloads do
 * not supply trusted routing or actor identity.
 */
data class AgentMessage(
    val messageId: String? = null,
    val createdAtEpochMillis: Long? = null,
    val origin: MessageRoute,
    val target: MessageRoute? = null,
    val reference: MessageReference? = null,
    val segments: List<AgentMessageSegment>,
)
