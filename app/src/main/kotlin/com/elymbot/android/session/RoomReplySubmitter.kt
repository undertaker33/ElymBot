package com.elymbot.android.session

import androidx.room.withTransaction
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.MessageEntity
import com.elymbot.android.data.ReplyDeliveryEntity
import com.elymbot.android.data.ReplyDeliveryStatus
import com.elymbot.android.data.ReplyDeliveryTargetKind
import com.elymbot.android.data.ReplySubmissionEntity
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ReplyFailureCode
import com.elymbot.core.contract.ReplyRejectionCode
import com.elymbot.core.contract.ReplySubmission
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.ReplySubmitter
import com.elymbot.core.contract.ToolInstallationIdentity
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class ReplyDeliveryTarget(
    val targetKey: String,
    val kind: ReplyDeliveryTargetKind,
    val route: MessageRoute? = null,
    val installationIdentity: ToolInstallationIdentity? = null,
)

data class ReplyTargetSnapshot(
    val targets: List<ReplyDeliveryTarget>,
    val historyVisibilityTargetKey: String,
)

fun interface ReplyTargetSnapshotProvider {
    suspend fun snapshot(context: ReplySubmissionContext): ReplyTargetSnapshot
}

internal const val APP_TRANSCRIPT_TARGET_KEY = "app:transcript"

internal class ReplyTargetAuthorizationException : Exception()

internal class RoomReplySubmitter(
    private val database: ElymBotDatabase,
    private val targetSnapshotProvider: ReplyTargetSnapshotProvider = ReplyTargetSnapshotProvider {
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
    private val now: () -> Long = System::currentTimeMillis,
) : ReplySubmitter {
    override suspend fun submit(submission: ReplySubmission): ReplySubmissionResult {
        if (!submission.isValid()) return ReplySubmissionResult.Rejected(ReplyRejectionCode.INVALID_REQUEST)

        return try {
            database.withTransaction {
                val context = submission.context
                val session = database.conversationDao().get(context.sessionId)
                    ?: return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.SESSION_NOT_AVAILABLE)
                val turn = database.turnDao().get(context.sessionId, context.turnSequence)
                    ?: return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.STALE_ATTEMPT)
                if (turn.executionId != context.executionId || turn.attemptId != context.attemptId) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.STALE_ATTEMPT)
                }
                if (database.retiredReplyDao().isRetired(context.sessionId, context.executionId, context.attemptId)) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.STALE_ATTEMPT)
                }

                val existing = database.replySubmissionDao().forCall(
                    sessionId = context.sessionId,
                    executionId = context.executionId,
                    attemptId = context.attemptId,
                    turnSequence = context.turnSequence,
                    toolStepOrdinal = context.toolStepOrdinal,
                    toolCallOrdinal = context.toolCallOrdinal,
                )
                if (existing.isNotEmpty()) return@withTransaction replayResult(existing, submission)

                if (turn.status != RUNNING) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.STALE_ATTEMPT)
                }
                if (session.id != turn.sessionId) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.SESSION_NOT_AVAILABLE)
                }

                val targets = try {
                    targetSnapshotProvider.snapshot(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ReplyTargetAuthorizationException) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.NOT_AUTHORIZED)
                }
                if (!targets.isValid()) {
                    return@withTransaction ReplySubmissionResult.Rejected(ReplyRejectionCode.INVALID_REQUEST)
                }

                val acceptedAt = now()
                var availableAt = acceptedAt
                val messageIds = mutableListOf<String>()
                submission.messages.forEachIndexed { ordinal, reply ->
                    availableAt = Math.addExact(availableAt, reply.suggestedDelayMillis)
                    val messageId = UUID.randomUUID().toString()
                    val messageIndex = database.messageDao().nextIndex(context.sessionId, context.turnSequence)
                    database.messageDao().insert(
                        MessageEntity(
                            id = messageId,
                            sessionId = context.sessionId,
                            turnSequence = context.turnSequence,
                            messageIndex = messageIndex,
                            role = MESSAGE_ROLE_ASSISTANT,
                            text = reply.text,
                            category = MESSAGE_CATEGORY_HISTORY,
                            status = null,
                            author = turn.personaName,
                        ),
                    )
                    database.replySubmissionDao().insert(
                        ReplySubmissionEntity(
                            messageId = messageId,
                            sessionId = context.sessionId,
                            turnSequence = context.turnSequence,
                            executionId = context.executionId,
                            attemptId = context.attemptId,
                            toolStepOrdinal = context.toolStepOrdinal,
                            toolCallOrdinal = context.toolCallOrdinal,
                            messageOrdinal = ordinal,
                            toolCallId = context.toolCallId,
                            suggestedDelayMillis = reply.suggestedDelayMillis,
                            historyVisibilityTargetKey = targets.historyVisibilityTargetKey,
                            acceptedAtEpochMillis = acceptedAt,
                        ),
                    )
                    database.replyDeliveryDao().insertAll(
                        targets.targets.map { target ->
                            target.toEntity(
                                messageId = messageId,
                                historyVisibilityGate = target.targetKey == targets.historyVisibilityTargetKey,
                                availableAtEpochMillis = availableAt,
                                updatedAtEpochMillis = acceptedAt,
                            )
                        },
                    )
                    messageIds += messageId
                }
                ReplySubmissionResult.Accepted(messageIds)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ArithmeticException) {
            ReplySubmissionResult.Rejected(ReplyRejectionCode.INVALID_REQUEST)
        } catch (_: Exception) {
            ReplySubmissionResult.Failed(ReplyFailureCode.PERSISTENCE)
        }
    }

    private suspend fun replayResult(
        stored: List<ReplySubmissionEntity>,
        submission: ReplySubmission,
    ): ReplySubmissionResult {
        if (stored.size != submission.messages.size ||
            stored.map(ReplySubmissionEntity::messageOrdinal) != submission.messages.indices.toList() ||
            stored.any { it.toolCallId != submission.context.toolCallId }
        ) {
            return ReplySubmissionResult.Rejected(ReplyRejectionCode.INVALID_REQUEST)
        }
        for ((index, record) in stored.withIndex()) {
            val message = database.messageDao().get(record.messageId)
                ?: return ReplySubmissionResult.Rejected(ReplyRejectionCode.STALE_ATTEMPT)
            val requested = submission.messages[index]
            if (message.text != requested.text || record.suggestedDelayMillis != requested.suggestedDelayMillis) {
                return ReplySubmissionResult.Rejected(ReplyRejectionCode.INVALID_REQUEST)
            }
        }
        return ReplySubmissionResult.Accepted(stored.map(ReplySubmissionEntity::messageId))
    }

    private fun ReplySubmission.isValid(): Boolean {
        val context = context
        return context.sessionId.isNotBlank() && context.executionId.isNotBlank() && context.attemptId.isNotBlank() &&
            context.turnSequence > 0 && context.toolStepOrdinal >= 0 && context.toolCallOrdinal >= 0 &&
            context.toolCallId.isNotBlank() && messages.isNotEmpty() &&
            messages.all { it.text.isNotBlank() && it.suggestedDelayMillis >= 0 }
    }

    private fun ReplyTargetSnapshot.isValid(): Boolean {
        if (targets.isEmpty() || historyVisibilityTargetKey.isBlank()) return false
        if (targets.map(ReplyDeliveryTarget::targetKey).any(String::isBlank) ||
            targets.map(ReplyDeliveryTarget::targetKey).distinct().size != targets.size
        ) {
            return false
        }
        if (targets.count { it.targetKey == historyVisibilityTargetKey } != 1) return false
        if (targets.count { it.kind == ReplyDeliveryTargetKind.APP_TRANSCRIPT } > 1) return false
        return targets.all { target ->
            when (target.kind) {
                ReplyDeliveryTargetKind.APP_TRANSCRIPT -> target.targetKey == APP_TRANSCRIPT_TARGET_KEY &&
                    target.route == null && target.installationIdentity == null
                ReplyDeliveryTargetKind.PLATFORM -> target.route?.let { route ->
                    route.channelId.isNotBlank() && route.conversationId.isNotBlank()
                } == true && target.installationIdentity?.let { identity ->
                    identity.pluginId.isNotBlank() && identity.instanceId.isNotBlank()
                } == true
            }
        }
    }

    private fun ReplyDeliveryTarget.toEntity(
        messageId: String,
        historyVisibilityGate: Boolean,
        availableAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ) = ReplyDeliveryEntity(
        id = UUID.randomUUID().toString(),
        messageId = messageId,
        targetKey = targetKey,
        targetKind = kind.name,
        channelId = route?.channelId,
        conversationId = route?.conversationId,
        accountId = route?.accountId,
        actorId = route?.actorId,
        pluginId = installationIdentity?.pluginId,
        installationInstanceId = installationIdentity?.instanceId,
        status = ReplyDeliveryStatus.PENDING.name,
        historyVisibilityGate = historyVisibilityGate,
        availableAtEpochMillis = availableAtEpochMillis,
        remoteMessageId = null,
        failureCode = null,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private companion object {
        const val RUNNING = "RUNNING"
        const val MESSAGE_ROLE_ASSISTANT = "ASSISTANT"
        const val MESSAGE_CATEGORY_HISTORY = "HISTORY"
    }
}
