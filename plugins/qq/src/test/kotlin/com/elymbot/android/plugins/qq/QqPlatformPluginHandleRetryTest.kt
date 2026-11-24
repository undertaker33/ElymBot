package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformGuestProcess
import com.elymbot.core.contract.AgentMessageSegment
import com.elymbot.core.contract.PlatformInboundResult
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.PlatformRuntimeHost
import com.elymbot.core.contract.PlatformRuntimePhase
import com.elymbot.core.contract.PluginHostContext
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import com.elymbot.core.contract.ToolInstallationIdentity
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqPlatformPluginHandleRetryTest {
    @Test
    fun reconnectCancellationStopsGuestReturnedFromSuspendedLaunch() = runBlocking {
        val directory = Files.createTempDirectory("qq-reconnect-cancel").toFile()
        val host = TestRuntimeHost()
        val handle = newHandle(directory)
        val sink = PlatformInboundSink { PlatformInboundResult.Accepted }
        val launchEntered = CompletableDeferred<Unit>()
        val releaseLaunch = CompletableDeferred<Unit>()
        lateinit var reconnectJob: Job

        try {
            handle.start(host, sink)
            host.pauseNextLaunch = {
                launchEntered.complete(Unit)
                withContext(NonCancellable) { releaseLaunch.await() }
            }

            reconnectJob = launch { handle.reconnect() }
            withTimeout(TEST_TIMEOUT_MILLIS) { launchEntered.await() }
            reconnectJob.cancel(CancellationException("synthetic reconnect cancellation"))
            releaseLaunch.complete(Unit)
            reconnectJob.join()

            assertTrue(reconnectJob.isCancelled)
            assertEquals(2, host.processes.size)
            assertEquals(1, host.processes.last().stopCalls)
            assertFalse(host.processes.last().isAlive)
            assertEquals(PlatformRuntimePhase.STOPPED, handle.state.value.phase)
        } finally {
            handle.stop()
            handle.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun stashedEventReplaysAfterWindowWithoutAnotherInboundEventUsingCurrentSink() = runBlocking {
        val directory = Files.createTempDirectory("qq-stash-replay").toFile()
        val host = TestRuntimeHost()
        val handle = newHandle(directory)
        val firstSinkMessages = CopyOnWriteArrayList<String>()
        val replayedMessage = CompletableDeferred<String>()
        val firstSink = PlatformInboundSink { envelope ->
            firstSinkMessages += envelope.message.messageId.orEmpty()
            PlatformInboundResult.Accepted
        }
        val currentSink = PlatformInboundSink { envelope ->
            val text = envelope.message.segments
                .filterIsInstance<AgentMessageSegment.Text>()
                .joinToString("") { it.value }
            replayedMessage.complete(text)
            PlatformInboundResult.Accepted
        }
        val currentSettings = handle.settings.value

        try {
            val save = handle.updateSettings(
                expectedRevision = currentSettings.revision,
                settings = currentSettings.settings.copy(
                    whitelistEnabled = false,
                    privateChatRequiresWakeWord = false,
                    commandAliases = mapOf("alias" to "help"),
                    privateRateLimit = QqPlatformRateLimitSettings(
                        windowMillis = STASH_WINDOW_MILLIS,
                        maxMessages = 1,
                        strategy = QqPlatformRateLimitStrategy.STASH,
                        maxStashedMessages = 2,
                        maxStashAgeMillis = TEST_TIMEOUT_MILLIS,
                    ),
                ),
            )
            assertTrue(save is QqPlatformSettingsUpdateResult.Saved)

            handle.start(host, firstSink)
            handle.acceptOneBotPayload(privatePayload("1", "first"))
            handle.start(host, currentSink)
            handle.acceptOneBotPayload(privatePayload("2", "/alias argument"))
            val stashedSettings = handle.settings.value
            val currentRules = stashedSettings.settings.copy(commandAliases = mapOf("alias" to "reset"))
            assertTrue(
                handle.updateSettings(stashedSettings.revision, currentRules) is QqPlatformSettingsUpdateResult.Saved,
            )

            assertEquals(listOf("1"), firstSinkMessages)
            assertEquals("/reset argument", withTimeout(TEST_TIMEOUT_MILLIS) { replayedMessage.await() })
            assertEquals(listOf("1"), firstSinkMessages)
        } finally {
            handle.stop()
            handle.close()
            directory.deleteRecursively()
        }
    }

    private fun newHandle(directory: java.io.File) = QqPlatformPluginHandleImpl(
        context = PluginHostContext(
            installationIdentity = ToolInstallationIdentity("qq", "synthetic-instance"),
            dataDirectory = directory,
        ),
        runtimeAssetPreparationOverride = {},
        oneBotServerStartOverride = {},
    )

    private fun privatePayload(messageId: String, text: String) =
        """{"post_type":"message","message_type":"private","self_id":"1000","user_id":"2000","message_id":"$messageId","message":[{"type":"text","data":{"text":"$text"}}]}"""

    private class TestRuntimeHost : PlatformRuntimeHost {
        val processes = mutableListOf<TestGuestProcess>()
        var pauseNextLaunch: (suspend () -> Unit)? = null

        override suspend fun launchGuest(rootfsRelativePath: String, guestEntry: String): PlatformGuestProcess {
            val process = TestGuestProcess()
            processes += process
            pauseNextLaunch?.let { pause ->
                pauseNextLaunch = null
                pause()
            }
            return process
        }
    }

    private class TestGuestProcess : PlatformGuestProcess {
        @Volatile
        override var isAlive: Boolean = true
            private set

        var stopCalls: Int = 0
            private set

        override suspend fun awaitExit(timeoutMillis: Long): Int? = null

        override suspend fun stop() {
            delay(1)
            stopCalls += 1
            isAlive = false
        }
    }

    private companion object {
        const val STASH_WINDOW_MILLIS = 750L
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
