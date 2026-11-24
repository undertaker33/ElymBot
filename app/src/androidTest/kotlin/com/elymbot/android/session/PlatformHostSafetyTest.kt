package com.elymbot.android.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.data.PlatformTurnRouteEntity
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.MessageReference
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsHandle
import com.elymbot.core.contract.QqPlatformSettingsRejection
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.ToolSource
import com.elymbot.core.contract.ToolInstallationIdentity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformHostSafetyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun qqMemberOriginAuthorizesPlainGroupTargetAndKeepsReferenceOnTarget() {
        val origin = MessageRoute(
            channelId = "qq",
            conversationId = "group:group-7:member:user-9",
            accountId = "bot-account",
            actorId = "user-9",
        )
        val target = MessageRoute(
            channelId = "qq",
            conversationId = "group:group-7",
            accountId = "bot-account",
            actorId = "user-9",
        )
        val settings = QqPlatformSettings(allowedGroupIds = setOf("group-7"))
        val route = groupTurnRoute(origin, target)

        assertTrue(isAuthorizedQqReplyRoute(origin, target, settings))
        assertFalse(isAuthorizedQqReplyRoute(origin, target.copy(accountId = "other-account"), settings))
        assertFalse(isAuthorizedQqReplyRoute(origin, target.copy(conversationId = "group:other"), settings))
        assertFalse(isAuthorizedQqReplyRoute(origin, target.copy(actorId = "other-user"), settings))
        assertFalse(isAuthorizedQqReplyRoute(origin, target, settings.copy(allowedGroupIds = emptySet())))
        assertEquals(target, route.toPlatformTargetRoute())
        assertEquals(MessageReference(target, "remote-message"), route.toPlatformReplyReference())
        assertTrue(messageReferenceMatchesTarget(MessageReference(target, "remote-message"), target))
        assertFalse(messageReferenceMatchesTarget(MessageReference(origin, "remote-message"), target))
        assertTrue(messageReferenceMatchesTarget(MessageReference(target, "public-reference"), null, target))
    }

    @Test
    fun ownerBindingCancellationAfterSettingsPersistRemovesOnlyAppGrant() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assetsRoot = File(context.cacheDir, "platform-owner-safety-${System.nanoTime()}")
        val repository = ElymBotDataRepository(context, database, PrivateAssetStore(context, root = assetsRoot))
        repository.initialize()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val service = ElymBotSessionService(
            context = context,
            repository = repository,
            connectionProvider = RoomModelServiceConnectionProvider(repository),
            modelGateway = UnusedModelGateway,
            agentRuntime = com.elymbot.core.contract.AgentRuntime { emptyFlow<AgentEvent>() },
            commandParser = SlashCommandParser { _, _ -> CommandParseResult.NotCommand },
            scope = serviceScope,
        )
        val settings = PersistThenCancelSettingsHandle()

        try {
            try {
                service.persistOwnerBinding(
                    identity = ToolInstallationIdentity("qq", "instance-1"),
                    accountId = "bot-account",
                    userId = "owner-user",
                    settingsHandle = settings,
                )
                fail("Expected cancellation after the plugin published its first settings write")
            } catch (_: CancellationException) {
                // The host must reconcile the plugin's actual durable state before retiring its pending marker.
            }

            assertTrue(settings.settings.value.settings.allowedPrivateUserIds.isEmpty())
            assertEquals(null, database.platformRouteDao().getOwnerBinding("qq"))
        } finally {
            serviceScope.cancel()
            database.close()
            assetsRoot.deleteRecursively()
        }
    }

    @Test
    fun uncertainOwnerGrantCompensationKeepsThePendingFailClosedMarker() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assetsRoot = File(context.cacheDir, "platform-owner-fail-closed-${System.nanoTime()}")
        val repository = ElymBotDataRepository(context, database, PrivateAssetStore(context, root = assetsRoot))
        repository.initialize()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val service = ElymBotSessionService(
            context = context,
            repository = repository,
            connectionProvider = RoomModelServiceConnectionProvider(repository),
            modelGateway = UnusedModelGateway,
            agentRuntime = com.elymbot.core.contract.AgentRuntime { emptyFlow<AgentEvent>() },
            commandParser = SlashCommandParser { _, _ -> CommandParseResult.NotCommand },
            scope = serviceScope,
        )
        val settings = PersistThenCancelSettingsHandle(rejectCompensation = true)

        try {
            try {
                service.persistOwnerBinding(
                    identity = ToolInstallationIdentity("qq", "instance-1"),
                    accountId = "bot-account",
                    userId = "owner-user",
                    settingsHandle = settings,
                )
                fail("Expected cancellation after the plugin published its first settings write")
            } catch (_: CancellationException) {
                // An uncertain settings write keeps the host-owned grant provenance for fail-closed checks.
            }

            assertTrue("The uncertain permission remains in plugin state", "owner-user" in settings.settings.value.settings.allowedPrivateUserIds)
            val binding = database.platformRouteDao().getOwnerBinding("qq")
            assertFalse(binding?.ownerBound ?: true)
            assertTrue(binding?.autoGrantedPrivatePermission ?: false)
        } finally {
            serviceScope.cancel()
            database.close()
            assetsRoot.deleteRecursively()
        }
    }

    @Test
    fun cancellationDuringGuestHandoffStopsTheCreatedGuest() = runBlocking {
        val guest = RecordingGuestProcess()
        try {
            handoffPlatformResource(
                launch = { register ->
                    register(guest)
                    throw CancellationException("Cancelled after native process creation")
                },
                cleanup = { it.stop() },
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // The registered process remains host-owned until handoff completes.
        }

        assertTrue(guest.stopped)
    }

    private fun groupTurnRoute(origin: MessageRoute, target: MessageRoute) =
        PlatformTurnRouteEntity(
            sessionId = "external-session",
            turnSequence = 1,
            pluginId = "qq",
            installationInstanceId = "instance-1",
            channelId = origin.channelId,
            accountId = requireNotNull(origin.accountId),
            conversationId = origin.conversationId,
            actorId = requireNotNull(origin.actorId),
            targetChannelId = target.channelId,
            targetConversationId = target.conversationId,
            targetAccountId = requireNotNull(target.accountId),
            targetActorId = target.actorId.orEmpty(),
            referenceMessageId = "remote-message",
        )

    private class PersistThenCancelSettingsHandle(
        private val rejectCompensation: Boolean = false,
    ) : QqPlatformSettingsHandle {
        private val mutableSettings = MutableStateFlow(QqPlatformSettingsSnapshot(0, QqPlatformSettings()))
        override val settings = mutableSettings.asStateFlow()
        override val toolSources: List<ToolSource> = emptyList()
        private var cancelAfterFirstWrite = true

        override suspend fun updateSettings(
            expectedRevision: Long,
            settings: QqPlatformSettings,
        ): QqPlatformSettingsUpdateResult {
            val current = mutableSettings.value
            if (current.revision != expectedRevision) return QqPlatformSettingsUpdateResult.Conflict(current)
            if (cancelAfterFirstWrite) {
                cancelAfterFirstWrite = false
                mutableSettings.value = QqPlatformSettingsSnapshot(current.revision + 1, settings)
                throw CancellationException("Plugin published settings before returning Saved")
            }
            if (rejectCompensation) {
                return QqPlatformSettingsUpdateResult.Rejected(QqPlatformSettingsRejection.STORE_UNAVAILABLE)
            }
            val saved = QqPlatformSettingsSnapshot(current.revision + 1, settings)
            mutableSettings.value = saved
            return QqPlatformSettingsUpdateResult.Saved(saved)
        }

        override fun close() = Unit
    }

    private class RecordingGuestProcess : com.elymbot.core.contract.PlatformGuestProcess {
        var stopped = false
            private set
        override val isAlive: Boolean get() = !stopped
        override suspend fun awaitExit(timeoutMillis: Long): Int? = null
        override suspend fun stop() {
            stopped = true
        }
    }

    private object UnusedModelGateway : ModelGateway {
        override suspend fun listModels(serviceId: String): List<String> = emptyList()
        override suspend fun probe(model: ModelReference) = ModelCapabilityReport(
            model = model,
            availability = CapabilityStatus.UNKNOWN,
            vision = CapabilityStatus.UNKNOWN,
            nativeStreaming = CapabilityStatus.UNKNOWN,
            structuredToolCalls = CapabilityStatus.UNKNOWN,
        )
        override fun stream(request: ModelRequest) = emptyFlow<ModelStreamEvent>()
    }
}
