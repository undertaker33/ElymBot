package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.AgentMessage
import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.MediaResourceReference
import com.elymbot.core.contract.MessageReference
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.PlatformHistoryVisibility
import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundProcessing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val QQ_PLATFORM_ID = "qq"

internal enum class OneBotConversationKind {
    PRIVATE,
    GROUP,
}

internal enum class OneBotPayloadError {
    MALFORMED,
    MISSING_ACCOUNT,
    MISSING_SENDER,
    MISSING_GROUP,
    MISSING_MESSAGE_ID,
}

internal sealed interface OneBotPayloadParseResult {
    data class Message(val event: OneBotInboundEvent) : OneBotPayloadParseResult

    data object Ignored : OneBotPayloadParseResult

    data class Invalid(val error: OneBotPayloadError) : OneBotPayloadParseResult
}

/** Raw media locations remain private to the QQ adapter and are never copied into Core metadata. */
internal data class OneBotMediaSource(
    val resourceId: String,
    val segmentType: String,
    val fileToken: String?,
    val downloadUrl: String?,
    val displayName: String?,
)

internal data class OneBotInboundEvent(
    val message: AgentMessage,
    val accountId: String,
    val senderId: String,
    val groupId: String?,
    val conversationKind: OneBotConversationKind,
    val mentionsSelf: Boolean,
    val mentionsAll: Boolean,
    val text: String,
    val mediaSources: List<OneBotMediaSource>,
) {
    val eventKey: String
        get() = "$QQ_PLATFORM_ID:$accountId:${message.messageId.orEmpty()}"

    fun toEnvelope(
        processing: PlatformInboundProcessing,
        historyVisibility: PlatformHistoryVisibility,
    ): PlatformInboundEnvelope {
        val contextOnly = processing == PlatformInboundProcessing.CONTEXT_ONLY
        val origin = if (contextOnly && historyVisibility == PlatformHistoryVisibility.GROUP_PUBLIC) {
            groupHistoryRoute()
        } else {
            message.origin
        }
        return PlatformInboundEnvelope(
            message = message.copy(
                origin = origin,
                target = if (contextOnly) null else message.target,
            ),
            processing = processing,
            historyVisibility = historyVisibility,
        )
    }

    fun withText(value: String): OneBotInboundEvent {
        val textReplaced = message.segments.mapNotNull { segment ->
            when (segment) {
                is AgentMessageSegment.Text -> null
                else -> segment
            }
        }
        return copy(
            message = message.copy(segments = listOf(AgentMessageSegment.Text(value)) + textReplaced),
            text = value,
        )
    }

    private fun groupHistoryRoute(): MessageRoute = MessageRoute(
        channelId = QQ_PLATFORM_ID,
        conversationId = "group:${groupId.orEmpty()}",
        accountId = accountId,
        actorId = senderId,
    )
}

/** Converts OneBot message events into Core-owned message and route types. */
internal class OneBotMessageParser(
    private val json: Json = Json,
) {
    fun parse(payload: String): OneBotPayloadParseResult {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return OneBotPayloadParseResult.Invalid(OneBotPayloadError.MALFORMED)
        if (root.scalar("post_type") != "message") return OneBotPayloadParseResult.Ignored

        val conversationKind = when (root.scalar("message_type")) {
            "private" -> OneBotConversationKind.PRIVATE
            "group" -> OneBotConversationKind.GROUP
            else -> return OneBotPayloadParseResult.Ignored
        }
        val accountId = root.scalar("self_id")?.takeIf(String::isNotBlank)
            ?: return OneBotPayloadParseResult.Invalid(OneBotPayloadError.MISSING_ACCOUNT)
        val senderId = root.scalar("user_id")?.takeIf(String::isNotBlank)
            ?: return OneBotPayloadParseResult.Invalid(OneBotPayloadError.MISSING_SENDER)
        val groupId = if (conversationKind == OneBotConversationKind.GROUP) {
            root.scalar("group_id")?.takeIf(String::isNotBlank)
                ?: return OneBotPayloadParseResult.Invalid(OneBotPayloadError.MISSING_GROUP)
        } else {
            null
        }
        val messageId = root.scalar("message_id")?.takeIf(String::isNotBlank)
            ?: return OneBotPayloadParseResult.Invalid(OneBotPayloadError.MISSING_MESSAGE_ID)

        val parsedSegments = parseSegments(
            message = root["message"],
            fallbackRawMessage = root.scalar("raw_message").orEmpty(),
            accountId = accountId,
            messageId = messageId,
        )
        val targetRoute = MessageRoute(
            channelId = QQ_PLATFORM_ID,
            conversationId = if (groupId == null) "private:$senderId" else "group:$groupId",
            accountId = accountId,
            actorId = senderId,
        )
        val originRoute = MessageRoute(
            channelId = QQ_PLATFORM_ID,
            conversationId = if (groupId == null) {
                "private:$senderId"
            } else {
                "group:$groupId:member:$senderId"
            },
            accountId = accountId,
            actorId = senderId,
        )
        val timestampMillis = root.scalar("time")?.toLongOrNull()?.let { seconds ->
            seconds.takeIf { it >= 0 }?.times(1_000L)
        }
        val reference = parsedSegments.replyMessageId?.let { replyMessageId ->
            MessageReference(route = targetRoute, messageId = replyMessageId)
        }
        val message = AgentMessage(
            messageId = messageId,
            createdAtEpochMillis = timestampMillis,
            origin = originRoute,
            target = targetRoute,
            reference = reference,
            segments = parsedSegments.segments,
        )
        return OneBotPayloadParseResult.Message(
            OneBotInboundEvent(
                message = message,
                accountId = accountId,
                senderId = senderId,
                groupId = groupId,
                conversationKind = conversationKind,
                mentionsSelf = parsedSegments.mentionedUserIds.contains(accountId),
                mentionsAll = parsedSegments.mentionedAll,
                text = parsedSegments.text,
                mediaSources = parsedSegments.mediaSources,
            ),
        )
    }

    private fun parseSegments(
        message: JsonElement?,
        fallbackRawMessage: String,
        accountId: String,
        messageId: String,
    ): ParsedSegments {
        val array = message as? JsonArray
        if (array != null) {
            val builder = StringBuilder()
            val contentBuilder = StringBuilder()
            val output = mutableListOf<AgentMessageSegment>()
            val mediaSources = mutableListOf<OneBotMediaSource>()
            val mentionedUserIds = mutableSetOf<String>()
            var mentionedAll = false
            var replyMessageId: String? = null
            array.forEachIndexed { index, element ->
                val segment = element as? JsonObject ?: return@forEachIndexed
                val data = segment["data"] as? JsonObject
                when (segment.scalar("type")) {
                    "text" -> {
                        val text = data?.scalar("text").orEmpty()
                        appendText(builder, text)
                        appendText(contentBuilder, text)
                    }
                    "at" -> {
                        val target = data?.scalar("qq")?.takeIf(String::isNotBlank) ?: return@forEachIndexed
                        if (target.equals("all", ignoreCase = true)) {
                            mentionedAll = true
                            appendText(contentBuilder, "@all")
                        } else {
                            mentionedUserIds += target
                            appendText(contentBuilder, "@$target")
                        }
                    }
                    "reply" -> replyMessageId = data?.scalar("id")?.takeIf(String::isNotBlank)
                    "image", "record", "audio", "voice", "video", "file" -> {
                        val parsed = mediaSegment(
                            segmentType = segment.scalar("type").orEmpty(),
                            data = data,
                            accountId = accountId,
                            messageId = messageId,
                            segmentIndex = index,
                        ) ?: return@forEachIndexed
                        output += parsed.segment
                        mediaSources += parsed.source
                    }
                }
            }
            contentBuilder.toString().takeIf(String::isNotBlank)?.let {
                output.add(0, AgentMessageSegment.Text(it.trim()))
            }
            return ParsedSegments(
                text = builder.toString().trim(),
                segments = output,
                mediaSources = mediaSources,
                mentionedUserIds = mentionedUserIds,
                mentionedAll = mentionedAll,
                replyMessageId = replyMessageId,
            )
        }

        val rawText = (message as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf(String::isNotBlank)
            ?: fallbackRawMessage
        return parseRawMessage(rawText, accountId, messageId)
    }

    private fun parseRawMessage(rawMessage: String, accountId: String, messageId: String): ParsedSegments {
        val builder = StringBuilder()
        val contentBuilder = StringBuilder()
        val output = mutableListOf<AgentMessageSegment>()
        val mediaSources = mutableListOf<OneBotMediaSource>()
        val mentionedUserIds = mutableSetOf<String>()
        var mentionedAll = false
        var replyMessageId: String? = null
        var mediaIndex = 0
        var cursor = 0
        for (match in CQ_SEGMENT_REGEX.findAll(rawMessage)) {
            val plainText = rawMessage.substring(cursor, match.range.first)
            appendText(builder, plainText)
            appendText(contentBuilder, plainText)
            val segmentType = match.groupValues[1].lowercase()
            val data = parseCqData(match.groupValues.getOrElse(2) { "" })
            when (segmentType) {
                "at" -> {
                    val target = data["qq"].orEmpty()
                    if (target.equals("all", ignoreCase = true)) {
                        mentionedAll = true
                        appendText(contentBuilder, "@all")
                    } else if (target.isNotBlank()) {
                        mentionedUserIds += target
                        appendText(contentBuilder, "@$target")
                    }
                }
                "reply" -> replyMessageId = data["id"]?.takeIf(String::isNotBlank)
                "image", "record", "audio", "voice", "video", "file" -> {
                    val jsonData = JsonObject(data.mapValues { JsonPrimitive(it.value) })
                    val parsed = mediaSegment(segmentType, jsonData, accountId, messageId, mediaIndex)
                    if (parsed != null) {
                        output += parsed.segment
                        mediaSources += parsed.source
                        mediaIndex += 1
                    }
                }
            }
            cursor = match.range.last + 1
        }
        val trailingText = rawMessage.substring(cursor)
        appendText(builder, trailingText)
        appendText(contentBuilder, trailingText)
        val text = builder.toString().trim()
        val contentText = contentBuilder.toString().trim()
        if (contentText.isNotEmpty()) output.add(0, AgentMessageSegment.Text(contentText))
        return ParsedSegments(
            text = text,
            segments = output,
            mediaSources = mediaSources,
            mentionedUserIds = mentionedUserIds,
            mentionedAll = mentionedAll,
            replyMessageId = replyMessageId,
        )
    }

    private fun mediaSegment(
        segmentType: String,
        data: JsonObject?,
        accountId: String,
        messageId: String,
        segmentIndex: Int,
    ): ParsedMedia? {
        val normalizedType = when (segmentType) {
            "image" -> "image"
            "record", "audio", "voice" -> "audio"
            "video" -> "video"
            "file" -> "file"
            else -> return null
        }
        val resourceId = "$QQ_PLATFORM_ID:$accountId:$messageId:$segmentIndex"
        val displayName = data?.scalar("name")?.takeIf(String::isNotBlank)
        val sizeBytes = data?.scalar("size")?.toLongOrNull()?.takeIf { it >= 0 }
        val reference = MediaResourceReference(
            resourceId = resourceId,
            mediaType = when (normalizedType) {
                "image" -> "image/*"
                "audio" -> "audio/*"
                "video" -> "video/*"
                else -> "application/octet-stream"
            },
            displayName = displayName,
            sizeBytes = sizeBytes,
        )
        val source = OneBotMediaSource(
            resourceId = resourceId,
            segmentType = normalizedType,
            fileToken = data?.scalar("file")?.takeIf(String::isNotBlank),
            downloadUrl = data?.scalar("url")?.takeIf(String::isNotBlank),
            displayName = displayName,
        )
        val segment = when (normalizedType) {
            "image" -> AgentMessageSegment.Image(reference)
            "audio" -> AgentMessageSegment.Audio(reference)
            "video" -> AgentMessageSegment.Video(reference)
            else -> AgentMessageSegment.File(reference)
        }
        return ParsedMedia(segment, source)
    }

    private fun appendText(builder: StringBuilder, value: String) {
        if (value.isNotEmpty()) builder.append(value)
    }

    private fun parseCqData(value: String): Map<String, String> = value
        .split(',')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            part.substring(0, separator) to part.substring(separator + 1)
        }
        .toMap()

    private fun JsonObject.scalar(key: String): String? = this[key].scalar()

    private fun JsonElement?.scalar(): String? = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }

    private data class ParsedSegments(
        val text: String,
        val segments: List<AgentMessageSegment>,
        val mediaSources: List<OneBotMediaSource>,
        val mentionedUserIds: Set<String>,
        val mentionedAll: Boolean,
        val replyMessageId: String?,
    )

    private data class ParsedMedia(
        val segment: AgentMessageSegment,
        val source: OneBotMediaSource,
    )

    private companion object {
        val CQ_SEGMENT_REGEX = Regex("\\[CQ:([a-zA-Z0-9_]+)(?:,([^\\]]*))?]", RegexOption.IGNORE_CASE)
    }
}

private fun JsonObject.scalar(key: String): String? = this[key].scalar()

private fun JsonElement?.scalar(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    else -> null
}
