package com.elymbot.android.session

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.PersonaEntity
import com.elymbot.android.data.PlatformAssetImportResult
import com.elymbot.android.data.PlatformInboundInboxEntity
import com.elymbot.android.data.PlatformInboundSegmentEntity
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.data.PlatformTurnRouteEntity
import com.elymbot.core.contract.AgentEvent
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.MediaResourceReference
import com.elymbot.core.contract.MessageReference
import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.PlatformMediaReadResult
import com.elymbot.core.contract.PlatformDeliveryResult
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.PlatformOutboundMessage
import com.elymbot.core.contract.PlatformPluginHandle
import com.elymbot.core.contract.PlatformRuntimeHost
import com.elymbot.core.contract.PlatformRuntimePhase
import com.elymbot.core.contract.PlatformRuntimeState
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsHandle
import com.elymbot.core.contract.QqPlatformSettingsRejection
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import com.elymbot.core.contract.SlashCommandParser
import com.elymbot.core.contract.ToolSource
import com.elymbot.core.contract.ToolInstallationIdentity
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val serviceJob = SupervisorJob()
        val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
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
            withContext(NonCancellable) {
                serviceJob.cancelAndJoin()
                database.close()
                assetsRoot.deleteRecursively()
            }
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
        val serviceJob = SupervisorJob()
        val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
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
            withContext(NonCancellable) {
                serviceJob.cancelAndJoin()
                database.close()
                assetsRoot.deleteRecursively()
            }
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

    @Test
    fun platformMediaImportEnforcesHostByteCapAndRemovesRejectedPartialFiles() = runBlocking {
        val root = File(context.cacheDir, "platform-media-import-${System.nanoTime()}")
        val assets = PrivateAssetStore(context, root = root)
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Vx8AAAAASUVORK5CYII=",
        )

        try {
            val stored = assets.importPlatformMedia(maxBytes = imageBytes.size.toLong()) { output ->
                output.write(imageBytes)
                PlatformMediaReadResult.Copied("application/octet-stream", imageBytes.size.toLong())
            }
            assertTrue(stored is PlatformAssetImportResult.Stored)
            val relativePath = (stored as PlatformAssetImportResult.Stored).asset.relativePath
            assertTrue(assets.file(relativePath).readBytes().contentEquals(imageBytes))
            assertEquals("image/png", detectPlatformImageMimeType(assets.file(relativePath)))

            val forgedImage = assets.importPlatformMedia(maxBytes = 4) { output ->
                output.write(byteArrayOf(5, 6, 7, 8))
                PlatformMediaReadResult.Copied("image/png", 4)
            }
            assertTrue(forgedImage is PlatformAssetImportResult.Stored)
            val forgedPath = (forgedImage as PlatformAssetImportResult.Stored).asset.relativePath
            assertNull(detectPlatformImageMimeType(assets.file(forgedPath)))
            assets.delete(forgedPath)

            val oversized = assets.importPlatformMedia(maxBytes = 3) { output ->
                output.write(byteArrayOf(5, 6, 7, 8))
                PlatformMediaReadResult.Copied("image/png", 4)
            }
            assertTrue(oversized is PlatformAssetImportResult.Rejected)
            assertEquals(
                PlatformMediaReadResult.TooLarge,
                (oversized as PlatformAssetImportResult.Rejected).result,
            )
            assertEquals(1, root.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun platformMediaImportCancellationRemovesPartialFile() = runBlocking {
        val root = File(context.cacheDir, "platform-media-cancel-${System.nanoTime()}")
        val assets = PrivateAssetStore(context, root = root)

        try {
            try {
                assets.importPlatformMedia(maxBytes = 64) { output ->
                    output.write(byteArrayOf(1, 2, 3))
                    throw CancellationException("Cancel after creating the partial private asset")
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // The importer must retire a file created before cancellation.
            }
            assertEquals(0, root.walkTopDown().count { it.isFile })

            val canceledAtHandoff = launch {
                assets.importPlatformMedia(maxBytes = 64) { output ->
                    output.write(byteArrayOf(9, 8, 7))
                    currentCoroutineContext().cancel(CancellationException("Cancel as the IO result returns"))
                    PlatformMediaReadResult.Copied("image/png", 3)
                }
            }
            canceledAtHandoff.join()
            assertEquals(0, root.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inboundImageReaderGatesCopyAndDeletesWhenAuthorizationChangesDuringRead() = runBlocking {
        val root = File(context.cacheDir, "platform-media-reader-${System.nanoTime()}")
        val assets = PrivateAssetStore(context, root = root)
        val reader = PlatformInboundMediaReader(assets)
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Vx8AAAAASUVORK5CYII=",
        )
        val reference = MediaResourceReference("qq:account:message:0", "image/*", sizeBytes = imageBytes.size.toLong())
        var authorized = false
        val deniedHandle = RecordingPlatformHandle(imageBytes, "application/octet-stream")

        try {
            val denied = reader.copyImage(deniedHandle, reference, maxBytes = 1024) { authorized }
            assertEquals(PlatformInboundImageResult.Unauthorized, denied)
            assertEquals(0, deniedHandle.copyCalls)

            val authorizationChecks = AtomicInteger()
            val revokedBeforeCopyHandle = RecordingPlatformHandle(imageBytes, "application/octet-stream")
            val revokedBeforeCopy = reader.copyImage(revokedBeforeCopyHandle, reference, maxBytes = 1024) {
                authorizationChecks.incrementAndGet() == 1
            }
            assertEquals(PlatformInboundImageResult.Unauthorized, revokedBeforeCopy)
            assertEquals(0, revokedBeforeCopyHandle.copyCalls)
            assertEquals(0, root.walkTopDown().count { it.isFile })

            authorized = true
            val revokingHandle = RecordingPlatformHandle(imageBytes, "application/octet-stream") {
                authorized = false
            }
            val revokedDuringCopy = reader.copyImage(revokingHandle, reference, maxBytes = 1024) { authorized }
            assertEquals(PlatformInboundImageResult.Unauthorized, revokedDuringCopy)
            assertEquals(1, revokingHandle.copyCalls)
            assertEquals(0, root.walkTopDown().count { it.isFile })

            authorized = true
            val permittedHandle = RecordingPlatformHandle(imageBytes, "application/octet-stream")
            val permitted = reader.copyImage(permittedHandle, reference, maxBytes = 1024) { authorized }
            assertTrue(permitted is PlatformInboundImageResult.Stored)
            assertEquals("image/png", (permitted as PlatformInboundImageResult.Stored).mediaType)
            assets.delete(permitted.asset.relativePath)
            assertEquals(0, root.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inboundImageReaderCancellationAfterCopyDuringAuthorizationCheckDeletesStoredFile() = runBlocking {
        val root = File(context.cacheDir, "platform-media-post-copy-cancel-${System.nanoTime()}")
        val assets = PrivateAssetStore(context, root = root)
        val reader = PlatformInboundMediaReader(assets)
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Vx8AAAAASUVORK5CYII=",
        )
        val reference = MediaResourceReference("qq:account:message:0", "image/*", sizeBytes = imageBytes.size.toLong())
        val postCopyAuthorizationCheck = CompletableDeferred<Unit>()
        val authorizationChecks = AtomicInteger()
        val handle = RecordingPlatformHandle(imageBytes, "application/octet-stream")

        try {
            val read = launch {
                reader.copyImage(handle, reference, maxBytes = 1024) {
                    if (authorizationChecks.incrementAndGet() <= 2) {
                        true
                    } else {
                        postCopyAuthorizationCheck.complete(Unit)
                        awaitCancellation()
                    }
                }
            }
            try {
                postCopyAuthorizationCheck.await()
                assertEquals(1, handle.copyCalls)
                assertEquals(1, root.walkTopDown().count { it.isFile })
            } finally {
                read.cancelAndJoin()
            }
            assertEquals(0, root.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun platformAccountGateDefersUnknownAccountAndRejectsKnownReplacement() {
        assertEquals(PlatformAccountGate.WAITING_FOR_ACCOUNT, platformAccountGate(null, "account-1"))
        assertEquals(PlatformAccountGate.WAITING_FOR_ACCOUNT, platformAccountGate("  ", "account-1"))
        assertEquals(PlatformAccountGate.MATCH, platformAccountGate(" account-1 ", "account-1"))
        assertEquals(PlatformAccountGate.ACCOUNT_CHANGED, platformAccountGate("account-2", "account-1"))
    }

    @Test
    fun normalizedInboundSegmentsKeepOrderAndUnsupportedMediaMetadata() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assetsRoot = File(context.cacheDir, "platform-segment-assets-${System.nanoTime()}")
        val repository = ElymBotDataRepository(context, database, PrivateAssetStore(context, root = assetsRoot))

        try {
            repository.initialize()
            val persona = PersonaEntity(
                id = "media-persona",
                name = "Media test persona",
                description = "",
                tagsValue = "",
                prompt = "",
                favorite = false,
                coverPath = null,
                atlasTile = 0,
                coverX = 0f,
                coverY = 0f,
                avatarX = 0f,
                avatarY = 0f,
                sortOrder = 0,
            )
            database.personaDao().insert(persona)
            database.conversationDao().insert(
                ConversationEntity(
                    id = "media-session",
                    kind = "DEDICATED",
                    personaId = persona.id,
                    title = null,
                    mainKey = null,
                    currentCheckpointId = "media-checkpoint",
                    nextTurnSequence = 1,
                    unread = false,
                    createdAt = 1,
                    lastActivityAt = 1,
                ),
            )
            val inbox = PlatformInboundInboxEntity(
                id = "inbox-media-test",
                pluginId = "qq",
                installationInstanceId = "instance-1",
                channelId = "qq",
                accountId = "bot-account",
                conversationId = "private:user-1",
                actorId = "user-1",
                messageId = "remote-message-1",
                sessionId = "media-session",
                processing = "TRIGGER_AGENT",
                historyVisibility = "PRIVATE",
                policyRevision = 1,
                status = "QUEUED",
                text = "look",
                targetChannelId = "qq",
                targetConversationId = "private:user-1",
                targetAccountId = "bot-account",
                targetActorId = "user-1",
                referenceMessageId = null,
                turnSequence = null,
                receivedAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            )
            database.platformRouteDao().insertInbound(inbox)
            database.platformRouteDao().insertInboundSegments(
                listOf(
                    PlatformInboundSegmentEntity(
                        inboxId = inbox.id,
                        segmentIndex = 0,
                        kind = "TEXT",
                        textValue = "look",
                        resourceId = null,
                        mediaType = null,
                        displayName = null,
                        declaredSizeBytes = null,
                        privatePath = null,
                        byteSize = null,
                        projectionStatus = "TEXT_READY",
                    ),
                    PlatformInboundSegmentEntity(
                        inboxId = inbox.id,
                        segmentIndex = 1,
                        kind = "AUDIO",
                        textValue = null,
                        resourceId = "qq:bot-account:remote-message-1:1",
                        mediaType = "audio/*",
                        displayName = "voice.ogg",
                        declaredSizeBytes = 64,
                        privatePath = null,
                        byteSize = null,
                        projectionStatus = "NOT_PROJECTED",
                    ),
                ),
            )

            val segments = database.platformRouteDao().inboundSegments(inbox.id)
            assertEquals(listOf("TEXT", "AUDIO"), segments.map { it.kind })
            assertEquals("voice.ogg", segments[1].displayName)
            assertEquals(64L, requireNotNull(segments[1].declaredSizeBytes))
            assertEquals("NOT_PROJECTED", segments[1].projectionStatus)
        } finally {
            database.close()
            assetsRoot.deleteRecursively()
        }
    }

    @Test
    fun v6MigrationAddsMediaSegmentsAndKeepsExistingInboxRows() {
        val databaseName = "platform-media-v6-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE platform_inbound_inbox (id TEXT NOT NULL PRIMARY KEY)")
                            db.execSQL("INSERT INTO platform_inbound_inbox (id) VALUES ('existing-inbox')")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ElymBotDatabase.MIGRATION_6_7.migrate(db)
            db.query("SELECT id FROM platform_inbound_inbox").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing-inbox", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'platform_inbound_segments'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_platform_inbound_segments_inboxId'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
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

    private class RecordingPlatformHandle(
        private val bytes: ByteArray,
        private val contentType: String,
        private val afterCopy: () -> Unit = {},
    ) : PlatformPluginHandle {
        override val platformId = "qq"
        private val mutableState = MutableStateFlow(
            PlatformRuntimeState(phase = PlatformRuntimePhase.CONNECTED, accountId = "bot-account"),
        )
        override val state = mutableState.asStateFlow()
        override val toolSources: List<ToolSource> = emptyList()
        var copyCalls: Int = 0
            private set

        override suspend fun start(runtimeHost: PlatformRuntimeHost, inboundSink: PlatformInboundSink) = Unit
        override suspend fun stop() = Unit
        override suspend fun refreshLogin() = Unit
        override suspend fun reconnect() = Unit
        override suspend fun logout() = Unit
        override suspend fun send(request: PlatformOutboundMessage) = PlatformDeliveryResult.Delivered()

        override suspend fun copyMedia(
            reference: MediaResourceReference,
            destination: java.io.OutputStream,
            maxBytes: Long,
        ): PlatformMediaReadResult {
            copyCalls += 1
            destination.write(bytes)
            afterCopy()
            return PlatformMediaReadResult.Copied(contentType, bytes.size.toLong())
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
