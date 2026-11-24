package com.elymbot.android.session

import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.PlatformInboundInboxEntity
import com.elymbot.android.data.ReplyDeliveryEntity
import com.elymbot.android.data.ReplyDeliveryTargetKind
import com.elymbot.android.data.PlatformTurnRouteEntity
import com.elymbot.android.runtime.pluginpackage.PluginInstanceAvailability
import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.MessageReference
import com.elymbot.core.contract.PlatformPluginHandle
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsHandle
import com.elymbot.core.contract.ReplySubmissionContext
import com.elymbot.core.contract.ToolInstallationIdentity
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Captures delivery routes once per execution attempt, then reuses them for every Reply call. */
internal class AppReplyTargetSnapshotProvider(
    private val database: ElymBotDatabase,
    private val pluginRuntimeManager: PluginRuntimeManager,
) : ReplyTargetSnapshotProvider {
    override suspend fun snapshot(context: ReplySubmissionContext): ReplyTargetSnapshot {
        val submissions = database.replySubmissionDao()
        val previous = submissions.firstSubmissionForAttempt(
            context.sessionId,
            context.turnSequence,
            context.executionId,
            context.attemptId,
        )
        if (previous != null) {
            val deliveries = database.replyDeliveryDao().forMessage(previous.messageId)
            val targets = deliveries.mapNotNull { it.toSnapshotTarget() }
            if (targets.isEmpty()) throw ReplyTargetAuthorizationException()
            return ReplyTargetSnapshot(targets, previous.historyVisibilityTargetKey)
        }

        val platformTurn = database.platformRouteDao().getTurnRoute(context.sessionId, context.turnSequence)
        if (platformTurn != null) {
            val identity = ToolInstallationIdentity(platformTurn.pluginId, platformTurn.installationInstanceId)
            val route = platformTurn.toPlatformTargetRoute()
            val inbound = database.platformRouteDao().getTriggeredInboundForTurn(context.sessionId, context.turnSequence)
                ?: throw ReplyTargetAuthorizationException()
            if (!isAuthorizedActiveQqTarget(identity, platformTurn, inbound)) throw ReplyTargetAuthorizationException()
            val key = route.deliveryTargetKey(identity)
            return ReplyTargetSnapshot(
                targets = listOf(ReplyDeliveryTarget(key, ReplyDeliveryTargetKind.PLATFORM, route, identity)),
                historyVisibilityTargetKey = key,
            )
        }

        val scheduledRun = database.scheduledTaskRunInboxDao().getForTurn(context.sessionId, context.turnSequence)
        if (scheduledRun != null) {
            if (scheduledRun.status != RUNNING) throw ReplyTargetAuthorizationException()
            val targets = mutableListOf(
                ReplyDeliveryTarget(APP_TRANSCRIPT_TARGET_KEY, ReplyDeliveryTargetKind.APP_TRANSCRIPT),
            )
            for (status in pluginRuntimeManager.statusSnapshot()) {
                if (!status.enabled || status.availability != PluginInstanceAvailability.ACTIVE || status.pluginId != QQ_PLUGIN_ID) {
                    continue
                }
                val ownerRoute = scheduledOwnerRoute(status.installationIdentity) ?: continue
                val key = ownerRoute.deliveryTargetKey(status.installationIdentity)
                targets += ReplyDeliveryTarget(
                    targetKey = key,
                    kind = ReplyDeliveryTargetKind.PLATFORM,
                    route = ownerRoute,
                    installationIdentity = status.installationIdentity,
                )
            }
            return ReplyTargetSnapshot(targets, APP_TRANSCRIPT_TARGET_KEY)
        }

        return ReplyTargetSnapshot(
            targets = listOf(ReplyDeliveryTarget(APP_TRANSCRIPT_TARGET_KEY, ReplyDeliveryTargetKind.APP_TRANSCRIPT)),
            historyVisibilityTargetKey = APP_TRANSCRIPT_TARGET_KEY,
        )
    }

    private suspend fun scheduledOwnerRoute(identity: ToolInstallationIdentity): MessageRoute? =
        pluginRuntimeManager.withActivePluginHandle(
            pluginId = identity.pluginId,
            installationIdentity = identity,
            handleType = PlatformPluginHandle::class.java,
        ) { platform ->
            val owner = database.platformRouteDao().getOwnerBinding(identity.pluginId)
            val settings = (platform as? QqPlatformSettingsHandle)?.settings?.value
            if (platform.platformId != QQ_PLATFORM_ID || settings == null || owner == null || !owner.ownerBound ||
                owner.installationInstanceId != identity.instanceId || owner.channelId != QQ_PLATFORM_ID ||
                owner.accountId.isBlank() || owner.actorId.isBlank() ||
                !isAuthorizedQqRoute(owner.conversationId, owner.actorId, settings.settings)
            ) {
                null
            } else {
                MessageRoute(
                    channelId = owner.channelId,
                    conversationId = owner.conversationId,
                    accountId = owner.accountId,
                    actorId = owner.actorId,
                )
            }
        }

    private suspend fun isAuthorizedActiveQqTarget(
        identity: ToolInstallationIdentity,
        turn: PlatformTurnRouteEntity,
        inbound: PlatformInboundInboxEntity,
    ): Boolean {
        if (pluginRuntimeManager.instanceAvailability(identity) != PluginInstanceAvailability.ACTIVE) return false
        val origin = turn.toOriginMessageRoute()
        val target = turn.toPlatformTargetRoute()
        if (inbound.pluginId != identity.pluginId || inbound.installationInstanceId != identity.instanceId ||
            inbound.sessionId != turn.sessionId || inbound.turnSequence != turn.turnSequence ||
            inbound.processing != com.elymbot.core.contract.PlatformInboundProcessing.TRIGGER_AGENT.name ||
            inbound.historyVisibility !in setOf(
                com.elymbot.core.contract.PlatformHistoryVisibility.PRIVATE.name,
                com.elymbot.core.contract.PlatformHistoryVisibility.GROUP_MEMBER.name,
            ) || inbound.channelId != origin.channelId || inbound.accountId != origin.accountId ||
            inbound.conversationId != origin.conversationId || inbound.actorId != origin.actorId ||
            inbound.targetChannelId != target.channelId || inbound.targetConversationId != target.conversationId ||
            inbound.targetAccountId != target.accountId || inbound.targetActorId.orEmpty() != target.actorId.orEmpty()
        ) return false
        return pluginRuntimeManager.withActivePluginHandle(
            pluginId = identity.pluginId,
            installationIdentity = identity,
            handleType = PlatformPluginHandle::class.java,
        ) { platform ->
            val settings = (platform as? QqPlatformSettingsHandle)?.settings?.value ?: return@withActivePluginHandle false
            platform.platformId == QQ_PLATFORM_ID && isAuthorizedQqReplyRoute(origin, target, settings.settings)
        } ?: false
    }

    private fun isAuthorizedQqRoute(conversationId: String, actorId: String, settings: QqPlatformSettings): Boolean =
        isAuthorizedQqInboundRoute(conversationId, actorId, settings)

    private fun ReplyDeliveryEntity.toSnapshotTarget(): ReplyDeliveryTarget? {
        return when (targetKind) {
            ReplyDeliveryTargetKind.APP_TRANSCRIPT.name -> ReplyDeliveryTarget(
                targetKey = targetKey,
                kind = ReplyDeliveryTargetKind.APP_TRANSCRIPT,
            )
            ReplyDeliveryTargetKind.PLATFORM.name -> {
                val identity = if (pluginId != null && installationInstanceId != null) {
                    ToolInstallationIdentity(pluginId, installationInstanceId)
                } else {
                    return null
                }
                val route = if (channelId != null && conversationId != null) {
                    MessageRoute(channelId, conversationId, accountId, actorId)
                } else {
                    return null
                }
                ReplyDeliveryTarget(targetKey, ReplyDeliveryTargetKind.PLATFORM, route, identity)
            }
            else -> null
        }
    }

    private fun MessageRoute.deliveryTargetKey(identity: ToolInstallationIdentity): String {
        val material = listOf(
            identity.pluginId,
            identity.instanceId,
            channelId,
            accountId.orEmpty(),
            conversationId,
            actorId.orEmpty(),
        ).joinToString("\u0000")
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(material.toByteArray(StandardCharsets.UTF_8))
        return "platform:$encoded"
    }

    private fun PlatformTurnRouteEntity.toOriginMessageRoute() = MessageRoute(
        channelId = channelId,
        conversationId = conversationId,
        accountId = accountId,
        actorId = actorId,
    )

    private companion object {
        const val QQ_PLUGIN_ID = "qq"
        const val QQ_PLATFORM_ID = "qq"
        const val RUNNING = "RUNNING"
    }
}

internal fun isAuthorizedQqReplyRoute(
    origin: MessageRoute,
    target: MessageRoute,
    settings: QqPlatformSettings,
): Boolean {
    val accountId = origin.accountId ?: return false
    val actorId = origin.actorId ?: return false
    if (actorId.isBlank()) return false
    if (origin.channelId != "qq" || target.channelId != origin.channelId || target.accountId != accountId) return false
    if (!isAuthorizedQqInboundRoute(origin.conversationId, actorId, settings)) return false
    if (origin.conversationId.startsWith("private:")) {
        return origin.conversationId == "private:$actorId" && target.conversationId == origin.conversationId &&
            (target.actorId == null || target.actorId == actorId)
    }
    val marker = ":member:"
    val markerIndex = origin.conversationId.indexOf(marker)
    if (!origin.conversationId.startsWith("group:") || markerIndex <= "group:".length) return false
    val groupId = origin.conversationId.substring("group:".length, markerIndex)
    if (origin.conversationId.substring(markerIndex + marker.length) != actorId || groupId.isBlank()) return false
    return target.conversationId == "group:$groupId" && (target.actorId == null || target.actorId == actorId)
}

internal fun messageReferenceMatchesTarget(
    reference: MessageReference?,
    target: MessageRoute?,
    contextOnlyRoute: MessageRoute? = null,
): Boolean = reference == null ||
    target != null && reference.route == target ||
    target == null && contextOnlyRoute != null && reference.route == contextOnlyRoute

internal fun isAuthorizedQqInboundRoute(
    conversationId: String,
    actorId: String,
    settings: QqPlatformSettings,
): Boolean {
    if (conversationId.startsWith("private:")) {
        if (conversationId.removePrefix("private:").let { it.isBlank() || it != actorId }) return false
        return !settings.whitelistEnabled || actorId in settings.allowedUserIds ||
            actorId in settings.allowedPrivateUserIds ||
            actorId in settings.adminUserIds && settings.adminPrivateBypassWhitelistEnabled
    }
    val marker = ":member:"
    val memberIndex = conversationId.indexOf(marker)
    if (!conversationId.startsWith("group:") || memberIndex <= "group:".length) return false
    val groupId = conversationId.substring("group:".length, memberIndex)
    if (groupId.isBlank() || conversationId.substring(memberIndex + marker.length) != actorId) return false
    return !settings.whitelistEnabled || actorId in settings.allowedUserIds ||
        actorId in settings.adminUserIds && settings.adminGroupBypassWhitelistEnabled ||
        groupId in settings.allowedGroupIds ||
        com.elymbot.core.contract.QqGroupMember(actorId, groupId) in settings.allowedGroupMembers
}

internal fun PlatformTurnRouteEntity.toPlatformTargetRoute() = MessageRoute(
    channelId = targetChannelId,
    conversationId = targetConversationId,
    accountId = targetAccountId,
    actorId = targetActorId.ifEmpty { null },
)

internal fun PlatformTurnRouteEntity.toPlatformReplyReference(): MessageReference? =
    referenceMessageId?.let { MessageReference(toPlatformTargetRoute(), it) }
