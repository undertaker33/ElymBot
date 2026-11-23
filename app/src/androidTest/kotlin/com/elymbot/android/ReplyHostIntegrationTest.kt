package com.elymbot.android

import android.security.NetworkSecurityPolicy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.ModelAssignmentEntity
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ModelServiceEntity
import com.elymbot.android.data.RoomHistoryStore
import com.elymbot.core.tool.ReplyToolSource
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryReadRequest
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ReplyHostIntegrationTest {
    private val appFixtureRule = AndroidTestAppFixtureRule()
    private val replyFixtureRule = ReplyHostFixtureRule(appFixture = { appFixtureRule.fixture })
    private val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(appFixtureRule).around(replyFixtureRule).around(activityRule)

    @Test
    fun pendingReplyStaysHiddenAndStopCancelsItWithoutHistoryLeak() {
        val first = "reply-first-${UUID.randomUUID().toString().take(6)}"
        val pending = "reply-pending-${UUID.randomUUID().toString().take(6)}"
        val canceled = "reply-canceled-${UUID.randomUUID().toString().take(6)}"
        replyFixtureRule.server.prepare(
            listOf(
                ReplyItem(first, 0),
                ReplyItem(pending, ReplyToolSource.MAX_SUGGESTED_DELAY_MILLIS),
                ReplyItem(canceled, ReplyToolSource.MAX_SUGGESTED_DELAY_MILLIS),
            ),
        )
        val prompt = "reply-${UUID.randomUUID().toString().take(10)}"
        sendPromptThroughMainActivity(prompt)

        val session = replyFixtureRule.awaitSession(prompt)
        val firstRequest = replyFixtureRule.awaitInitialRequest(session.id)
        assertTrue("The host must send a complete-response request", firstRequest.contains("\"stream\":false"))
        assertFalse("The host must not request SSE for Reply turns", firstRequest.contains("\"stream\":true"))
        assertTrue("The Reply tool must be present in the request", firstRequest.contains("\"name\":\"reply\""))
        val followUp = replyFixtureRule.server.awaitFollowUpRequest()
        assertTrue("The tool result request must also be non-streaming", followUp.contains("\"stream\":false"))
        assertTrue("The follow-up must contain the durable Reply result", followUp.contains("Reply accepted for durable delivery"))
        val pendingTurn = replyFixtureRule.awaitTurnStarted(session.id)
        replyFixtureRule.awaitDeliveryStatusForText(session.id, pendingTurn.sequence, pending, "PENDING")
        replyFixtureRule.awaitDeliveryStatusForText(session.id, pendingTurn.sequence, canceled, "PENDING")
        waitForText(activityRule, first)
        assertNoText(activityRule, pending)
        assertNoText(activityRule, canceled)
        assertEquals(listOf(prompt, first), replyFixtureRule.history(session.id, pendingTurn.sequence).map { it.text })

        replyFixtureRule.server.releaseFinalResponse()
        val turn = replyFixtureRule.awaitCompleted(session.id)

        val stopDelivery = activityRule.activity.getString(R.string.elym_stop_delivery)
        waitForDescription(activityRule, stopDelivery)
        activityRule.onNodeWithContentDescription(stopDelivery).performClick()
        replyFixtureRule.awaitDeliveryStatusForText(session.id, turn.sequence, pending, "CANCELLED")
        replyFixtureRule.awaitDeliveryStatusForText(session.id, turn.sequence, canceled, "CANCELLED")

        val stored = replyFixtureRule.messages(session.id, turn.sequence)
        val assistantRows = stored.filter { it.message.role == "ASSISTANT" }
        assertEquals(listOf(first, pending, canceled), assistantRows.map { it.message.text })
        val deliveryStatuses = assistantRows.associate { row ->
            row.message.text to replyFixtureRule.deliveryStatus(row.message.id)
        }
        assertEquals("DELIVERED", deliveryStatuses[first])
        assertEquals("CANCELLED", deliveryStatuses[pending])
        assertEquals("CANCELLED", deliveryStatuses[canceled])
        assertNoText(activityRule, pending)
        assertNoText(activityRule, canceled)

        val history = replyFixtureRule.history(session.id, turn.sequence)
        assertEquals(listOf(prompt, first), history.map { it.text })
        assertTrue("Canceled pending text must not enter Core history", history.none { it.text == pending || it.text == canceled })
    }

    @Test
    fun oneDelayedReplyReleasesAtItsDeadlineWithoutAnotherModelOrUserWrite() {
        val first = "first-immediate-${UUID.randomUUID().toString().take(7)}"
        val text = "single-delayed-${UUID.randomUUID().toString().take(8)}"
        replyFixtureRule.server.prepare(listOf(ReplyItem(first, 0), ReplyItem(text, 5_000)))
        val prompt = "single-${UUID.randomUUID().toString().take(10)}"
        sendPromptThroughMainActivity(prompt)

        val session = replyFixtureRule.awaitSession(prompt)
        val request = replyFixtureRule.awaitInitialRequest(session.id)
        assertTrue(request.contains("\"stream\":false"))
        replyFixtureRule.server.awaitFollowUpRequest()
        replyFixtureRule.server.releaseFinalResponse()
        val turn = replyFixtureRule.awaitCompleted(session.id)
        val service = (activityRule.activity.application as ElymBotApplication).uiService
        runBlocking { withContext(Dispatchers.IO) { service.setViewedConversation(session.id) } }
        waitForText(activityRule, first)
        replyFixtureRule.awaitDeliveryStatusForText(session.id, turn.sequence, text, "PENDING")
        val activityBeforeRelease = replyFixtureRule.session(session.id).lastActivityAt
        assertFalse(replyFixtureRule.session(session.id).unread)
        assertEquals(listOf(prompt, first), replyFixtureRule.history(session.id, turn.sequence).map { it.text })
        assertNoText(activityRule, text)

        activityRule.runOnUiThread { activityRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForText(activityRule, activityRule.activity.getString(R.string.elym_sessions))
        runBlocking { withContext(Dispatchers.IO) { service.setViewedConversation(null) } }
        assertNoText(activityRule, activityRule.activity.getString(R.string.elym_unread))

        replyFixtureRule.awaitDeliveryStatusForText(session.id, turn.sequence, text, "DELIVERED")
        waitForText(activityRule, text)
        waitForText(activityRule, activityRule.activity.getString(R.string.elym_unread))
        val releasedSession = replyFixtureRule.session(session.id)
        assertTrue("A background App release must mark the session unread", releasedSession.unread)
        assertTrue("A background App release must update last activity", releasedSession.lastActivityAt > activityBeforeRelease)
        assertEquals("DELIVERED", replyFixtureRule.deliveryStatusForText(session.id, turn.sequence, text))
        val history = replyFixtureRule.history(session.id, turn.sequence)
        assertEquals(listOf(prompt, first, text), history.map { it.text })
        activityRule.onNodeWithText(requireNotNull(session.title)).performClick()
        waitForText(activityRule, text)
        assertMessageOrder(first, text)
        assertEquals("Delivery must not start another model request", 2, replyFixtureRule.server.receivedRequests)
        assertEquals(turn.sequence, replyFixtureRule.latestTurn(session.id).sequence)
        assertEquals(1, replyFixtureRule.messages(session.id, turn.sequence).count { it.message.role == "USER" })
    }

    @Test
    fun mainActivityRetryReusesTheUserMessageAndCompletesThroughReply() {
        val reply = "retry-reply-${UUID.randomUUID().toString().take(8)}"
        replyFixtureRule.server.prepareFailFirstRequest(listOf(ReplyItem(reply, 0)))
        val prompt = "retry-${UUID.randomUUID().toString().take(10)}"
        sendPromptThroughMainActivity(prompt)

        val session = replyFixtureRule.awaitSession(prompt)
        val firstRequest = replyFixtureRule.awaitInitialRequest(session.id)
        assertTrue(firstRequest.contains("\"stream\":false"))
        val failedTurn = replyFixtureRule.awaitFinished(session.id)
        assertEquals("FAILED", failedTurn.status)
        assertEquals("MODEL_REJECTED", failedTurn.failureKind)
        assertTrue("Retryable failure kind was ${failedTurn.failureKind}", failedTurn.retryable)
        val originalUser = replyFixtureRule.messages(session.id, failedTurn.sequence)
            .single { it.message.role == "USER" }.message
        assertEquals(prompt, originalUser.text)
        assertTrue(replyFixtureRule.messages(session.id, failedTurn.sequence).none { it.message.role == "ASSISTANT" })
        assertEquals(1, replyFixtureRule.server.receivedRequests)

        waitForText(activityRule, activityRule.activity.getString(R.string.elym_retry))
        activityRule.onNodeWithText(activityRule.activity.getString(R.string.elym_retry)).performClick()
        val retriedRequest = replyFixtureRule.server.awaitRetryInitialRequest()
        assertTrue(retriedRequest.contains("\"stream\":false"))
        assertTrue(retriedRequest.contains(prompt))
        replyFixtureRule.server.awaitFollowUpRequest()
        replyFixtureRule.server.releaseFinalResponse()
        val completedTurn = replyFixtureRule.awaitCompleted(session.id)
        assertEquals(failedTurn.sequence, completedTurn.sequence)
        assertTrue("Retry must create a new execution attempt", failedTurn.attemptId != completedTurn.attemptId)
        waitForText(activityRule, reply)

        val stored = replyFixtureRule.messages(session.id, completedTurn.sequence)
        assertEquals(1, stored.count { it.message.role == "USER" })
        assertEquals(originalUser.id, stored.single { it.message.role == "USER" }.message.id)
        assertEquals(listOf(prompt, reply), replyFixtureRule.history(session.id, completedTurn.sequence).map { it.text })
    }

    private fun sendPromptThroughMainActivity(prompt: String) {
        val compose = activityRule
        val strings = compose.activity
        waitForText(compose, replyFixtureRule.personaName)
        compose.onNodeWithText(replyFixtureRule.personaName).performClick()
        waitForDescription(compose, strings.getString(R.string.elym_chat_menu))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_chat_menu)).performClick()
        compose.onNodeWithText(strings.getString(R.string.elym_sessions)).performClick()
        waitForText(compose, strings.getString(R.string.elym_sessions))
        openNewSession(compose, strings.getString(R.string.elym_new_session))
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_type_message)).performTextInput(prompt)
        compose.onNodeWithContentDescription(strings.getString(R.string.elym_send)).performClick()
    }

    private fun openNewSession(compose: ComposeContentTestRule, label: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
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
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoText(compose: ComposeContentTestRule, value: String) {
        compose.waitUntil(timeoutMillis = 1_000) {
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun assertMessageOrder(first: String, second: String) {
        val firstNode = activityRule.onAllNodesWithText(first, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        val secondNode = activityRule.onAllNodesWithText(second, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        assertTrue("Delivered messages must remain in Reply order", firstNode.boundsInRoot.top < secondNode.boundsInRoot.top)
    }

}

internal data class ReplyItem(val text: String, val delayMillis: Int)

internal class ReplyHostFixtureRule(
    private val appFixture: () -> AndroidTestAppFixture,
    private val structuredToolCallsStatus: String = "SUPPORTED",
) : TestRule {
    lateinit var server: LocalCompleteReplyServer
        private set
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
        val app = appFixture()
        database = app.database
        personaName = app.personaName
        server = LocalCompleteReplyServer().also(LocalCompleteReplyServer::start)
        serviceId = "reply-host-${UUID.randomUUID()}"
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = database.modelConfigurationDao()
                previousTextAssignment = dao.getAssignment("Text")
                val baseUrl = "http://localhost:${server.port}/v1"
                dao.upsertService(
                    ModelServiceEntity(
                        id = serviceId,
                        provider = "integration-test",
                        name = "Reply host local model",
                        baseUrl = baseUrl,
                        modelId = "reply-host-model",
                        catalogValue = "[\"reply-host-model\"]",
                    ),
                )
                dao.upsertAssignment(ModelAssignmentEntity("Text", serviceId, "reply-host-model"))
                dao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = serviceId,
                        modelId = "reply-host-model",
                        baseUrl = baseUrl,
                        target = "STRUCTURED_TOOL_CALLS",
                        status = structuredToolCallsStatus,
                        failureKind = null,
                        httpStatusCode = null,
                    ),
                )
            }
        }
    }

    fun awaitSession(prompt: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val title = prompt.take(26)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var found = database.conversationDao().getDedicated(appFixture().personaId).firstOrNull { it.title == title }
            while (found == null && System.nanoTime() < deadline) {
                Thread.sleep(25)
                found = database.conversationDao().getDedicated(appFixture().personaId).firstOrNull { it.title == title }
            }
            requireNotNull(found) { "No dedicated session was persisted for '$title'." }
        }
    }

    fun awaitInitialRequest(sessionId: String, turnIndex: Int = 0): String = try {
        server.awaitInitialRequest(turnIndex)
    } catch (failure: AssertionError) {
        val turn = runBlocking {
            withContext(Dispatchers.IO) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var latest = database.turnDao().latest(sessionId)
                while (latest?.status == "RUNNING" && System.nanoTime() < deadline) {
                    Thread.sleep(25)
                    latest = database.turnDao().latest(sessionId)
                }
                latest
            }
        }
        throw AssertionError(
            "Local model request failed: receivedRequests=${server.receivedRequests}, " +
                "turnStatus=${turn?.status}, failureKind=${turn?.failureKind}, " +
                "localhostCleartext=${NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("localhost")}.",
            failure,
        )
    }

    fun awaitCompleted(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var turn = database.turnDao().latest(sessionId)
            while (turn?.status != "COMPLETED" && System.nanoTime() < deadline) {
                Thread.sleep(25)
                turn = database.turnDao().latest(sessionId)
            }
            requireNotNull(turn).also { assertEquals("COMPLETED", it.status) }
        }
    }

    fun awaitTurnStarted(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var turn = database.turnDao().latest(sessionId)
            while (turn == null && System.nanoTime() < deadline) {
                Thread.sleep(25)
                turn = database.turnDao().latest(sessionId)
            }
            requireNotNull(turn) { "No turn was persisted for '$sessionId'." }
        }
    }

    fun latestTurn(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) { requireNotNull(database.turnDao().latest(sessionId)) }
    }

    fun awaitFinished(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var turn = database.turnDao().latest(sessionId)
            while ((turn == null || turn.status == "RUNNING") && System.nanoTime() < deadline) {
                Thread.sleep(25)
                turn = database.turnDao().latest(sessionId)
            }
            requireNotNull(turn) { "No terminal turn was persisted for '$sessionId'." }
        }
    }

    fun messages(sessionId: String, sequence: Long) = runBlocking {
        withContext(Dispatchers.IO) { database.messageDao().forTurn(sessionId, sequence) }
    }

    fun deliveryStatus(messageId: String) = runBlocking {
        withContext(Dispatchers.IO) { database.replyDeliveryDao().forMessage(messageId).single().status }
    }

    fun deliveryStatusForText(sessionId: String, sequence: Long, text: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val message = database.messageDao().forTurn(sessionId, sequence)
                .single { it.message.role == "ASSISTANT" && it.message.text == text }
            database.replyDeliveryDao().forMessage(message.message.id).single().status
        }
    }

    fun awaitDeliveryStatusForText(sessionId: String, sequence: Long, text: String, expected: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var status: String? = null
            while (status != expected && System.nanoTime() < deadline) {
                val message = database.messageDao().forTurn(sessionId, sequence)
                    .firstOrNull { it.message.role == "ASSISTANT" && it.message.text == text }
                status = message?.let { database.replyDeliveryDao().forMessage(it.message.id).singleOrNull()?.status }
                if (status == expected) break
                Thread.sleep(25)
            }
            assertEquals(expected, status)
        }
    }

    fun session(sessionId: String) = runBlocking {
        withContext(Dispatchers.IO) { requireNotNull(database.conversationDao().get(sessionId)) }
    }

    fun history(sessionId: String, sequence: Long) = runBlocking {
        withContext(Dispatchers.IO) {
            RoomHistoryStore(database).read(
                HistoryReadRequest(
                    sessionId = sessionId,
                    upperBoundExclusive = sequence + 1,
                    direction = HistoryDirection.OLDEST_TO_NEWEST,
                    pageSize = 100,
                    tokenBudget = 2_000,
                ),
            ).records
        }
    }

    private fun tearDown() {
        try {
            if (::database.isInitialized && ::serviceId.isInitialized) {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        val dao = database.modelConfigurationDao()
                        if (dao.getAssignment("Text")?.serviceId == serviceId) {
                            dao.deleteAssignment("Text")
                            previousTextAssignment?.let { dao.upsertAssignment(it) }
                        }
                        dao.deleteAssignmentsForService(serviceId)
                        dao.deleteProbes(serviceId)
                        dao.deleteService(serviceId)
                    }
                }
            }
        } finally {
            if (::server.isInitialized) server.close()
        }
    }
}

internal class LocalCompleteReplyServer : AutoCloseable {
    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }
    private val workers = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val requestOrdinal = AtomicInteger()
    private val nextPlanId = AtomicInteger()
    private val plans = ConcurrentLinkedQueue<ReplyPlan>()
    private val plansByTurn = ConcurrentHashMap<Int, ReplyPlan>()
    private val retryInitialRequestReceived = ConcurrentHashMap<Int, CountDownLatch>()
    private val retryInitialRequestBodies = ConcurrentHashMap<Int, AtomicReference<String?>>()
    private val routeLock = Any()
    @Volatile private var activePlan: ReplyPlan? = null
    private val firstRequestReceived = ConcurrentHashMap<Int, CountDownLatch>()
    private val followUpRequestReceived = ConcurrentHashMap<Int, CountDownLatch>()
    private val firstRequestBodies = ConcurrentHashMap<Int, AtomicReference<String?>>()
    private val followUpRequestBodies = ConcurrentHashMap<Int, AtomicReference<String?>>()
    private val failure = AtomicReference<Throwable?>()
    @Volatile private var acceptThread: Thread? = null

    val port: Int get() = serverSocket.localPort
    val receivedRequests: Int get() = requestOrdinal.get()

    fun prepare(items: List<ReplyItem>): Int {
        return prepare(items, failFirstRequest = false)
    }

    fun prepareFailFirstRequest(items: List<ReplyItem>): Int = prepare(items, failFirstRequest = true)

    private fun prepare(items: List<ReplyItem>, failFirstRequest: Boolean): Int {
        require(items.isNotEmpty())
        val id = nextPlanId.getAndIncrement()
        plans += ReplyPlan(id, items, failFirstRequest)
        return id
    }

    fun start() {
        acceptThread = Thread {
            while (!serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    activeSockets += socket
                    workers.execute { serve(socket) }
                } catch (_: IOException) {
                    if (!serverSocket.isClosed) failure.compareAndSet(null, IOException("Local model endpoint accept failed."))
                }
            }
        }.apply {
            name = "elymbot-reply-local-endpoint"
            isDaemon = true
            start()
        }
    }

    fun awaitInitialRequest(turnIndex: Int = 0): String = awaitBody(
        firstRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) },
        firstRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) },
        "initial model request",
    )

    fun awaitFollowUpRequest(turnIndex: Int = 0): String = awaitBody(
        followUpRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) },
        followUpRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) },
        "Reply follow-up request",
    )

    fun awaitRetryInitialRequest(turnIndex: Int = 0): String = awaitBody(
        retryInitialRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) },
        retryInitialRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) },
        "retry initial model request",
    )

    fun releaseFinalResponse(turnIndex: Int = 0) {
        plansByTurn[turnIndex]?.allowFinalResponse?.countDown()
    }

    private fun awaitBody(latch: CountDownLatch, value: AtomicReference<String?>, description: String): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!latch.await(100, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
            failure.get()?.let { throw AssertionError("Local endpoint failed before receiving the $description.", it) }
        }
        failure.get()?.let { throw AssertionError("Local model endpoint failed.", it) }
        if (latch.count != 0L) {
            throw AssertionError("Local endpoint did not receive the $description; requests=${requestOrdinal.get()}.")
        }
        return value.get().orEmpty()
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 20_000
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
            requestOrdinal.incrementAndGet()
            check(requestLine.startsWith("POST /v1/chat/completions ")) { "Unexpected request line: $requestLine" }
            check(body.contains("\"stream\":false")) { "The host requested streaming for a Reply turn." }
            val routed = routeRequest()
            val turnIndex = routed.plan.id
            when (routed.stage) {
                RequestStage.FAIL_FIRST -> {
                    recordInitialRequest(turnIndex, body)
                    writeResponse(socket, "", statusCode = 503)
                }
                RequestStage.INITIAL -> {
                    recordInitialRequest(turnIndex, body)
                    writeResponse(socket, replyToolCompletion(routed.plan.items))
                }
                RequestStage.RETRY_INITIAL -> {
                    retryInitialRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) }.set(body)
                    retryInitialRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) }.countDown()
                    writeResponse(socket, replyToolCompletion(routed.plan.items))
                }
                RequestStage.FOLLOW_UP -> {
                    followUpRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) }.set(body)
                    followUpRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) }.countDown()
                    check(routed.plan.allowFinalResponse.await(30, TimeUnit.SECONDS)) { "The final completion was not released." }
                    writeResponse(socket, emptyStopCompletion())
                    synchronized(routeLock) {
                        if (activePlan === routed.plan) activePlan = null
                    }
                }
            }
        } catch (caught: Throwable) {
            failure.compareAndSet(null, caught)
            plansByTurn.values.forEach { it.allowFinalResponse.countDown() }
        } finally {
            activeSockets -= socket
            runCatching { socket.close() }
        }
    }

    private fun replyToolCompletion(items: List<ReplyItem>): String {
        val messages = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("text", item.text).put("delayMillis", item.delayMillis))
            }
        }
        val arguments = JSONObject().put("messages", messages).toString()
        val call = JSONObject()
            .put("id", "reply-call-1")
            .put("type", "function")
            .put("function", JSONObject().put("name", "reply").put("arguments", arguments))
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", JSONObject.NULL)
            .put("tool_calls", JSONArray().put(call))
        val choice = JSONObject()
            .put("index", 0)
            .put("message", message)
            .put("finish_reason", "tool_calls")
        return JSONObject()
            .put("id", "reply-completion-1")
            .put("object", "chat.completion")
            .put("choices", JSONArray().put(choice))
            .toString()
    }

    private fun routeRequest(): RoutedRequest = synchronized(routeLock) {
        val plan = activePlan ?: plans.poll()?.also {
            activePlan = it
            plansByTurn[it.id] = it
        } ?: throw IOException("No Reply plan was prepared for this model request.")
        when {
            plan.failFirstRequest && !plan.firstFailureSent -> {
                plan.firstFailureSent = true
                RoutedRequest(plan, RequestStage.FAIL_FIRST)
            }
            !plan.initialResponseSent -> {
                plan.initialResponseSent = true
                RoutedRequest(plan, if (plan.failFirstRequest) RequestStage.RETRY_INITIAL else RequestStage.INITIAL)
            }
            else -> RoutedRequest(plan, RequestStage.FOLLOW_UP)
        }
    }

    private fun recordInitialRequest(turnIndex: Int, body: String) {
        firstRequestBodies.computeIfAbsent(turnIndex) { AtomicReference<String?>(null) }.set(body)
        firstRequestReceived.computeIfAbsent(turnIndex) { CountDownLatch(1) }.countDown()
    }

    private fun emptyStopCompletion(): String {
        val choice = JSONObject()
            .put("index", 0)
            .put("message", JSONObject().put("role", "assistant").put("content", ""))
            .put("finish_reason", "stop")
        return JSONObject()
            .put("id", "reply-completion-2")
            .put("object", "chat.completion")
            .put("choices", JSONArray().put(choice))
            .toString()
    }

    private fun writeResponse(socket: Socket, body: String, statusCode: Int = 200) {
        val bytes = body.toByteArray(UTF_8)
        val reason = if (statusCode == 200) "OK" else "Service Unavailable"
        val headers =
            "HTTP/1.1 $statusCode $reason\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        val output = socket.getOutputStream()
        output.write(headers.toByteArray(UTF_8))
        output.write(bytes)
        output.flush()
    }

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
        plansByTurn.values.forEach { it.allowFinalResponse.countDown() }
        plans.forEach { it.allowFinalResponse.countDown() }
        runCatching { serverSocket.close() }
        activeSockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        acceptThread?.join(1_000)
    }

    private enum class RequestStage { FAIL_FIRST, INITIAL, RETRY_INITIAL, FOLLOW_UP }

    private data class RoutedRequest(val plan: ReplyPlan, val stage: RequestStage)

    private data class ReplyPlan(
        val id: Int,
        val items: List<ReplyItem>,
        val failFirstRequest: Boolean,
        val allowFinalResponse: CountDownLatch = CountDownLatch(1),
    ) {
        var firstFailureSent = false
        var initialResponseSent = false
    }
}
