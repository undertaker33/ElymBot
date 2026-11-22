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
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.ElymBotDatabase
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class AppSessionIntegrationTest {
    private val fixture = IntegrationFixtureRule()
    private val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(fixture).around(activityRule)

    @Test
    fun mainActivityStreamsAcrossTwoSessionsAndPersistsReplyAfterLeavingPage() {
        val compose = activityRule
        val strings = compose.activity

        waitForText(compose, fixture.personaName)
        waitForText(compose, strings.getString(R.string.elym_active))
        compose.onNodeWithText(fixture.personaName).performClick()
        waitForDescription(compose, strings.getString(R.string.elym_chat_menu))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        compose.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(compose, strings.getString(R.string.elym_sessions))
        openNewSession(compose, strings.getString(R.string.elym_new_session))
        enterPrompt(compose, strings.getString(R.string.elym_type_message), strings.getString(R.string.elym_send), fixture.promptA)

        val requestA = fixture.server.awaitRequest(fixture.promptA)
        assertTrue("A request must use native SSE streaming", requestA.contains("\"stream\":true"))
        waitForText(compose, fixture.firstDelta)
        val sessionTitleA = fixture.sessionTitle(fixture.promptA)
        val sessionTitleB = fixture.sessionTitle(fixture.promptB)
        val sessionA = fixture.awaitSession(sessionTitleA)

        compose.onNodeWithContentDescription(strings.getString(R.string.elym_back)).performClick()
        waitForText(compose, sessionTitleA)
        openNewSession(compose, strings.getString(R.string.elym_new_session))
        enterPrompt(compose, strings.getString(R.string.elym_type_message), strings.getString(R.string.elym_send), fixture.promptB)

        val requestB = fixture.server.awaitRequest(fixture.promptB)
        assertTrue("B request must use native SSE streaming", requestB.contains("\"stream\":true"))
        waitForText(compose, fixture.secondSessionFirstDelta)
        val sessionB = fixture.awaitSession(sessionTitleB)
        assertEquals("RUNNING", fixture.latestTurn(sessionA.id).status)
        assertEquals("RUNNING", fixture.latestTurn(sessionB.id).status)

        compose.onNodeWithContentDescription(strings.getString(R.string.elym_back)).performClick()
        waitForText(compose, sessionTitleA)
        compose.onNodeWithText(sessionTitleA, substring = true).performClick()
        waitForText(compose, fixture.firstDelta)
        fixture.server.releaseSecondDelta()
        waitForText(compose, fixture.secondDelta)

        compose.onNodeWithContentDescription(strings.getString(R.string.elym_back)).performClick()
        waitForText(compose, sessionTitleA)
        compose.waitForIdle()
        fixture.server.releaseCompletion()
        fixture.server.releaseBCompletion()
        fixture.awaitCompleted(sessionA.id)
        fixture.awaitCompleted(sessionB.id)

        val persistedTurn = fixture.latestTurn(sessionA.id)
        assertEquals("COMPLETED", persistedTurn.status)
        val messages = fixture.messages(sessionA.id, persistedTurn.sequence)
        assertEquals(1, messages.count { it.message.role == "USER" })
        assertEquals(1, messages.count { it.message.role == "ASSISTANT" })
        val assistantA = messages.single { it.message.role == "ASSISTANT" }.message.text
        assertEquals(fixture.firstDelta + fixture.secondDelta, assistantA)
        assertTrue("A must not contain B's reply", messages.none { it.message.text.contains(fixture.secondSessionFirstDelta) })
        val persistedBTurn = fixture.latestTurn(sessionB.id)
        assertEquals("COMPLETED", persistedBTurn.status)
        val messagesB = fixture.messages(sessionB.id, persistedBTurn.sequence)
        assertEquals(1, messagesB.count { it.message.role == "USER" })
        assertEquals(1, messagesB.count { it.message.role == "ASSISTANT" })
        assertEquals(fixture.secondSessionFirstDelta + fixture.secondSessionReply, messagesB.single { it.message.role == "ASSISTANT" }.message.text)
        assertTrue("B must not contain A's reply", messagesB.none { it.message.text.contains(fixture.firstDelta) })
        assertTrue("A must be marked unread after completion outside its page", fixture.isUnread(sessionA.id))
        assertUnreadBadgeBelongsTo(compose, sessionTitleA, strings.getString(R.string.elym_unread))
        assertTrue("B must be marked unread after completion outside its page", fixture.isUnread(sessionB.id))
        assertUnreadBadgeBelongsTo(compose, sessionTitleB, strings.getString(R.string.elym_unread))
    }

    private fun openNewSession(compose: ComposeContentTestRule, label: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        val plus = compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes()
        if (plus.isNotEmpty()) compose.onNodeWithContentDescription(label).performClick()
        else compose.onNodeWithText(label).performClick()
    }

    private fun enterPrompt(compose: ComposeContentTestRule, inputLabel: String, sendLabel: String, prompt: String) {
        waitForDescription(compose, inputLabel)
        compose.onNodeWithContentDescription(inputLabel).performTextInput(prompt)
        compose.onNodeWithContentDescription(sendLabel).performClick()
    }

    private fun waitForDescription(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertUnreadBadgeBelongsTo(compose: ComposeContentTestRule, sessionTitle: String, unreadLabel: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            val titleNodes = compose.onAllNodesWithText(sessionTitle, substring = true).fetchSemanticsNodes()
            compose.onAllNodesWithText(unreadLabel, useUnmergedTree = true).fetchSemanticsNodes()
                .any { badge -> titleNodes.any { title -> badge.boundsInRoot.overlaps(title.boundsInRoot) } }
        }
        val titleNodes = compose.onAllNodesWithText(sessionTitle, substring = true).fetchSemanticsNodes()
        val badgeNodes = compose.onAllNodesWithText(unreadLabel, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(
            "The unread badge must be rendered in the requested session row",
            badgeNodes.any { badge -> titleNodes.any { title -> badge.boundsInRoot.overlaps(title.boundsInRoot) } },
        )
    }
}

private class IntegrationFixtureRule : TestRule {
    lateinit var prefix: String
        private set
    lateinit var promptA: String
        private set
    lateinit var promptB: String
        private set
    val firstDelta = "A-first"
    val secondDelta = "A-second"
    val secondSessionFirstDelta = "B-first"
    val secondSessionReply = "B-finished"
    lateinit var server: LocalSseServer
        private set

    private lateinit var appFixture: AndroidTestAppFixture
    lateinit var personaName: String
        private set
    private lateinit var database: ElymBotDatabase
    private lateinit var serviceId: String
    private var previousTextAssignment: ModelAssignmentEntity? = null

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                setUp()
                base.evaluate()
            } finally {
                tearDown()
            }
        }
    }

    private fun setUp() {
        appFixture = AndroidTestAppFixture()
        appFixture.start()
        prefix = "w01-${UUID.randomUUID().toString().take(16)}"
        personaName = appFixture.personaName
        promptA = "$prefix A request"
        promptB = "$prefix B request"
        server = LocalSseServer(promptA, promptB, firstDelta, secondDelta, secondSessionFirstDelta, secondSessionReply).also(LocalSseServer::start)
        serviceId = "w01-${UUID.randomUUID()}"
        database = appFixture.database
        runBlocking {
            withContext(Dispatchers.IO) {
                val modelDao = database.modelConfigurationDao()
                previousTextAssignment = modelDao.getAssignment("Text")
                val baseUrl = "http://localhost:${server.port}/v1"
                modelDao.upsertService(
                    ModelServiceEntity(
                        id = serviceId,
                        provider = "integration-test",
                        name = "W01 local SSE",
                        baseUrl = baseUrl,
                        modelId = "w01-local-model",
                        catalogValue = "[\"w01-local-model\"]",
                    ),
                )
                modelDao.upsertAssignment(ModelAssignmentEntity("Text", serviceId, "w01-local-model"))
                modelDao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = serviceId,
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

    fun awaitSession(title: String): ConversationEntity {
        var found: ConversationEntity? = null
        runBlocking {
            withContext(Dispatchers.IO) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (found == null && System.nanoTime() < deadline) {
                    found = database.conversationDao().getDedicated(appFixture.personaId).firstOrNull { it.title == title }
                    if (found == null) Thread.sleep(25)
                }
            }
        }
        return found ?: error("No dedicated session was persisted for '$title'.")
    }

    fun sessionTitle(prompt: String): String = prompt.take(26)

    fun awaitCompleted(sessionId: String) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (System.nanoTime() < deadline && database.turnDao().latest(sessionId)?.status != "COMPLETED") {
                    Thread.sleep(25)
                }
                assertEquals("COMPLETED", database.turnDao().latest(sessionId)?.status)
            }
        }
    }

    fun latestTurn(sessionId: String) = runBlocking { withContext(Dispatchers.IO) { database.turnDao().latest(sessionId)!! } }

    fun messages(sessionId: String, sequence: Long) = runBlocking {
        withContext(Dispatchers.IO) { database.messageDao().forTurn(sessionId, sequence) }
    }

    fun isUnread(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) { database.conversationDao().get(sessionId)!!.unread }
    }

    private fun tearDown() {
        if (::server.isInitialized) {
            server.releaseSecondDelta()
            server.releaseCompletion()
            server.releaseBCompletion()
        }
        try {
            if (::database.isInitialized && ::serviceId.isInitialized) {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        val modelDao = database.modelConfigurationDao()
                        if (modelDao.getAssignment("Text")?.serviceId == serviceId) {
                            modelDao.deleteAssignment("Text")
                            previousTextAssignment?.let { modelDao.upsertAssignment(it) }
                        }
                        modelDao.deleteAssignmentsForService(serviceId)
                        modelDao.deleteProbes(serviceId)
                        modelDao.deleteService(serviceId)
                    }
                }
            }
        } finally {
            if (::appFixture.isInitialized) appFixture.close()
            if (::server.isInitialized) server.close()
        }
    }
}

private class LocalSseServer(
    private val promptA: String,
    private val promptB: String,
    private val firstDelta: String,
    private val secondDelta: String,
    private val secondSessionFirstDelta: String,
    private val secondSessionReply: String,
) : AutoCloseable {
    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }
    private val workers = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val aReceived = CountDownLatch(1)
    private val bReceived = CountDownLatch(1)
    private val aSecondGate = CountDownLatch(1)
    private val aCompletionGate = CountDownLatch(1)
    private val bCompletionGate = CountDownLatch(1)
    private val aRequestBody = AtomicReference<String?>()
    private val bRequestBody = AtomicReference<String?>()
    private val failure = AtomicReference<Throwable?>()
    @Volatile private var acceptThread: Thread? = null

    val port: Int get() = serverSocket.localPort

    fun start() {
        acceptThread = Thread {
            while (!serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    activeSockets += socket
                    workers.execute { serve(socket) }
                } catch (_: IOException) {
                    if (!serverSocket.isClosed) failure.compareAndSet(null, IOException("Local test endpoint accept failed."))
                }
            }
        }.apply {
            name = "elymbot-w01-local-sse"
            isDaemon = true
            start()
        }
    }

    fun awaitRequest(prompt: String): String {
        val latch = if (prompt == promptA) aReceived else bReceived
        check(latch.await(15, TimeUnit.SECONDS)) { "Local endpoint did not receive '$prompt'." }
        failure.get()?.let { throw AssertionError("Local endpoint failed.", it) }
        return if (prompt == promptA) aRequestBody.get().orEmpty() else bRequestBody.get().orEmpty()
    }

    fun releaseSecondDelta() {
        aSecondGate.countDown()
    }

    fun releaseCompletion() {
        aCompletionGate.countDown()
    }

    fun releaseBCompletion() {
        bCompletionGate.countDown()
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
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
            val requestBytes = ByteArray(contentLength)
            DataInputStream(input).readFully(requestBytes)
            val body = String(requestBytes, UTF_8)
            check(requestLine.startsWith("POST /v1/chat/completions ")) { "Unexpected request line: $requestLine" }
            check(body.contains("\"stream\":true")) { "The model request did not request native streaming." }
            when {
                body.contains(promptA) -> {
                    aRequestBody.set(body)
                    aReceived.countDown()
                    streamA(socket)
                }
                body.contains(promptB) -> {
                    bRequestBody.set(body)
                    bReceived.countDown()
                    streamB(socket)
                }
                else -> throw IOException("The endpoint received an unknown test prompt.")
            }
        } catch (caught: Throwable) {
            failure.compareAndSet(null, caught)
        } finally {
            activeSockets -= socket
            runCatching { socket.close() }
        }
    }

    private fun streamA(socket: Socket) {
        val output = openEventStream(socket)
        writeEvent(output, deltaPayload(firstDelta))
        check(aSecondGate.await(60, TimeUnit.SECONDS)) { "The A second-delta gate was not released." }
        writeEvent(output, deltaPayload(secondDelta))
        check(aCompletionGate.await(60, TimeUnit.SECONDS)) { "The A completion gate was not released." }
        writeEvent(output, terminalPayload())
        writeEvent(output, "[DONE]")
        finish(output)
    }

    private fun streamB(socket: Socket) {
        val output = openEventStream(socket)
        writeEvent(output, deltaPayload(secondSessionFirstDelta))
        check(bCompletionGate.await(60, TimeUnit.SECONDS)) { "The B completion gate was not released." }
        writeEvent(output, deltaPayload(secondSessionReply))
        writeEvent(output, terminalPayload())
        writeEvent(output, "[DONE]")
        finish(output)
    }

    private fun openEventStream(socket: Socket): BufferedOutputStream {
        val output = BufferedOutputStream(socket.getOutputStream())
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
        return output
    }

    private fun writeEvent(output: BufferedOutputStream, payload: String) {
        val bytes = ("data: $payload\n\n").toByteArray(UTF_8)
        output.write(bytes.size.toString(16).toByteArray(UTF_8))
        output.write("\r\n".toByteArray(UTF_8))
        output.write(bytes)
        output.write("\r\n".toByteArray(UTF_8))
        output.flush()
    }

    private fun finish(output: BufferedOutputStream) {
        output.write("0\r\n\r\n".toByteArray(UTF_8))
        output.flush()
    }

    private fun deltaPayload(content: String): String =
        """{"choices":[{"index":0,"delta":{"content":"$content"},"finish_reason":null}]}"""

    private fun terminalPayload(): String =
        """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""

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
        releaseSecondDelta()
        releaseCompletion()
        releaseBCompletion()
        runCatching { serverSocket.close() }
        activeSockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        acceptThread?.join(1_000)
    }
}
