package com.elymbot.android

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.ModelAssignmentEntity
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ModelServiceEntity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFailureIntegrationTest {
    private val fixtureRule = AndroidTestAppFixtureRule()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture get() = fixtureRule.fixture

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixtureRule).around(compose)

    private lateinit var server: InterruptedThenSuccessServer
    private var serviceId: String? = null
    private var previousTextAssignment: ModelAssignmentEntity? = null

    @Test
    fun interruptedStreamShowsRetryAndReusesTheExistingUserMessage() {
        val prefix = "w01-${UUID.randomUUID().toString().take(12)}"
        val prompt = "$prefix retry"
        server = InterruptedThenSuccessServer(prompt, "recovered-$prefix").also(InterruptedThenSuccessServer::start)
        configureModel(server.port)

        val strings = compose.activity
        waitForText(compose, fixture.personaName)
        compose.onNodeWithText(fixture.personaName).performClick()
        waitForDescription(compose, strings.getString(R.string.elym_chat_menu))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        compose.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(compose, strings.getString(R.string.elym_sessions))
        openNewSession(compose, strings.getString(R.string.elym_new_session))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_type_message)).performTextInput(prompt)
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_send)).performClick()

        val failedRequest = server.awaitAttempt(1)
        assertTrue(failedRequest.contains("\"stream\":true"))
        assertEquals(1, failedRequest.windowed(prompt.length).count { it == prompt })
        waitForText(compose, strings.getString(R.string.elym_reply_failed))
        waitForText(compose, strings.getString(R.string.elym_connection_error))
        waitForText(compose, strings.getString(R.string.elym_retry))

        val sessionTitle = prompt.take(26)
        val session = awaitSession(sessionTitle)
        val failedTurn = latestTurn(session.id)
        assertTrue("A disconnected stream must leave the turn retryable", failedTurn.retryable)
        val originalUser = messages(session.id, failedTurn.sequence).single { it.message.role == "USER" }.message

        compose.onNodeWithText(strings.getString(R.string.elym_retry)).performClick()
        val retryRequest = server.awaitAttempt(2)
        assertTrue(retryRequest.contains("\"stream\":true"))
        assertEquals(1, retryRequest.windowed(prompt.length).count { it == prompt })
        waitForText(compose, server.reply)
        waitForCompleted(session.id)

        val completedTurn = latestTurn(session.id)
        assertEquals(failedTurn.sequence, completedTurn.sequence)
        assertNotEquals(failedTurn.attemptId, completedTurn.attemptId)
        assertEquals("COMPLETED", completedTurn.status)
        val completedMessages = messages(session.id, completedTurn.sequence)
        assertEquals(1, completedMessages.count { it.message.role == "USER" })
        assertEquals(1, completedMessages.count { it.message.role == "ASSISTANT" })
        assertEquals(originalUser.id, completedMessages.single { it.message.role == "USER" }.message.id)
        assertEquals(server.reply, completedMessages.single { it.message.role == "ASSISTANT" }.message.text)
    }

    @After
    fun tearDown() {
        val configuredServiceId = serviceId
        if (configuredServiceId != null && fixtureRule.fixture.let { runCatching { it.database }.isSuccess }) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val dao = fixture.database.modelConfigurationDao()
                    if (dao.getAssignment("Text")?.serviceId == configuredServiceId) {
                        dao.deleteAssignment("Text")
                        previousTextAssignment?.let { assignment -> dao.upsertAssignment(assignment) }
                    }
                    dao.deleteAssignmentsForService(configuredServiceId)
                    dao.deleteProbes(configuredServiceId)
                    dao.deleteService(configuredServiceId)
                }
            }
        }
        if (::server.isInitialized) server.close()
    }

    private fun configureModel(port: Int) {
        val id = "w01-${UUID.randomUUID()}"
        serviceId = id
        val database = fixture.database
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = database.modelConfigurationDao()
                previousTextAssignment = dao.getAssignment("Text")
                val baseUrl = "http://localhost:$port/v1"
                dao.upsertService(
                    ModelServiceEntity(
                        id = id,
                        provider = "integration-test",
                        name = "W01 interrupted stream",
                        baseUrl = baseUrl,
                        modelId = "w01-local-model",
                        catalogValue = "[\"w01-local-model\"]",
                    ),
                )
                dao.upsertAssignment(ModelAssignmentEntity("Text", id, "w01-local-model"))
                dao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = id,
                        modelId = "w01-local-model",
                        baseUrl = baseUrl,
                        target = "NATIVE_STREAMING",
                        status = "SUPPORTED",
                        failureKind = null,
                        httpStatusCode = null,
                    ),
                )
            }
        }
    }

    private fun openNewSession(compose: ComposeContentTestRule, label: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(label).performClick()
        } else {
            compose.onNodeWithText(label).performClick()
        }
    }

    private fun waitForDescription(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSession(title: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var found = fixture.database.conversationDao().getDedicated(fixture.personaId).firstOrNull { it.title == title }
            while (found == null && System.nanoTime() < deadline) {
                Thread.sleep(25)
                found = fixture.database.conversationDao().getDedicated(fixture.personaId).firstOrNull { it.title == title }
            }
            requireNotNull(found) { "No dedicated session was persisted for '$title'." }
        }
    }

    private fun latestTurn(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) { requireNotNull(fixture.database.turnDao().latest(sessionId)) }
    }

    private fun messages(sessionId: String, sequence: Long) = runBlocking {
        withContext(Dispatchers.IO) { fixture.database.messageDao().forTurn(sessionId, sequence) }
    }

    private fun waitForCompleted(sessionId: String) {
        compose.waitUntil(20_000) { latestTurn(sessionId).status == "COMPLETED" }
    }
}

private class InterruptedThenSuccessServer(
    val prompt: String,
    val reply: String,
) : AutoCloseable {
    private val socket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }
    private val executor = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val requestBodies = CopyOnWriteArrayList<String>()
    private val attemptSignals = listOf(CountDownLatch(1), CountDownLatch(1))
    private val failure = AtomicReference<Throwable?>()
    @Volatile private var acceptThread: Thread? = null
    val port: Int get() = socket.localPort

    fun start() {
        acceptThread = Thread {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    activeSockets += client
                    executor.execute { serve(client) }
                } catch (_: IOException) {
                    if (!socket.isClosed) failure.compareAndSet(null, IOException("Local endpoint accept failed."))
                }
            }
        }.apply {
            name = "elymbot-w01-interrupted-stream"
            isDaemon = true
            start()
        }
    }

    fun awaitAttempt(attempt: Int): String {
        check(attempt in 1..2)
        check(attemptSignals[attempt - 1].await(15, TimeUnit.SECONDS)) { "Request attempt $attempt was not received." }
        failure.get()?.let { throw AssertionError("Local endpoint failed.", it) }
        return requestBodies[attempt - 1]
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = readLine(input) ?: return
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                if (split > 0 && line.substring(0, split).equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(split + 1).trim().toInt()
                }
            }
            val bodyBytes = ByteArray(contentLength)
            DataInputStream(input).readFully(bodyBytes)
            val body = String(bodyBytes, UTF_8)
            check(requestLine.startsWith("POST /v1/chat/completions ")) { "Unexpected request: $requestLine" }
            check(body.contains("\"stream\":true")) { "App did not request a streaming response." }
            check(body.contains(prompt)) { "Request did not contain the submitted prompt." }
            val attempt = requestBodies.size + 1
            check(attempt <= 2) { "Unexpected extra request attempt $attempt." }
            requestBodies += body
            attemptSignals[attempt - 1].countDown()
            if (attempt == 1) sendTruncatedStream(client) else sendCompletedStream(client)
        } catch (caught: Throwable) {
            failure.compareAndSet(null, caught)
            attemptSignals.forEach(CountDownLatch::countDown)
        } finally {
            activeSockets -= client
            runCatching { client.close() }
        }
    }

    private fun sendTruncatedStream(client: Socket) {
        val output = openChunkedStream(client)
        writeChunk(output, event(deltaPayload("partial")))
        output.flush()
        client.shutdownOutput()
    }

    private fun sendCompletedStream(client: Socket) {
        val output = openChunkedStream(client)
        writeChunk(output, event(deltaPayload(reply)))
        writeChunk(output, event("""{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""))
        writeChunk(output, event("[DONE]"))
        output.write("0\r\n\r\n".toByteArray(UTF_8))
        output.flush()
    }

    private fun openChunkedStream(client: Socket): BufferedOutputStream = BufferedOutputStream(client.getOutputStream()).also { output ->
        output.write(
            (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "Transfer-Encoding: chunked\r\n\r\n"
            ).toByteArray(UTF_8),
        )
        output.flush()
    }

    private fun writeChunk(output: BufferedOutputStream, bytes: ByteArray) {
        output.write(bytes.size.toString(16).toByteArray(UTF_8))
        output.write("\r\n".toByteArray(UTF_8))
        output.write(bytes)
        output.write("\r\n".toByteArray(UTF_8))
    }

    private fun event(payload: String): ByteArray = "data: $payload\n\n".toByteArray(UTF_8)

    private fun deltaPayload(content: String): String =
        """{"choices":[{"index":0,"delta":{"content":"$content"},"finish_reason":null}]}"""

    private fun readLine(input: BufferedInputStream): String? {
        val line = StringBuilder()
        while (true) {
            val next = input.read()
            if (next < 0) return if (line.isEmpty()) null else line.toString()
            if (next == '\n'.code) return line.toString().trimEnd('\r')
            line.append(next.toChar())
        }
    }

    override fun close() {
        runCatching { socket.close() }
        activeSockets.forEach { runCatching { it.close() } }
        executor.shutdownNow()
        acceptThread?.join(1_000)
    }
}
