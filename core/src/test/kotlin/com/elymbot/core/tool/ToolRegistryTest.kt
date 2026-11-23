package com.elymbot.core.tool

import com.elymbot.core.contract.HistoryCursor
import com.elymbot.core.contract.HistoryDirection
import com.elymbot.core.contract.HistoryMessageStatus
import com.elymbot.core.contract.HistoryReadPage
import com.elymbot.core.contract.HistoryReadRequest
import com.elymbot.core.contract.HistoryReader
import com.elymbot.core.contract.HistoryRecord
import com.elymbot.core.contract.HistoryRole
import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolAccessContext
import com.elymbot.core.contract.ToolAccessDecision
import com.elymbot.core.contract.ToolAccessDenial
import com.elymbot.core.contract.ToolAccessPolicy
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolProgress
import com.elymbot.core.contract.ToolRegistration
import com.elymbot.core.contract.ToolRegistrationOwner
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import com.elymbot.core.contract.AgentMessage
import com.elymbot.core.contract.MessageRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun pluginToolsRequireHostAuthorityAndAreRecheckedBeforeExecution() = runTest {
        val identity = ToolInstallationIdentity("sample-plugin", "install-2")
        var active = true
        var executions = 0
        var observedIdentity: ToolInstallationIdentity? = null
        val source = object : ToolSource {
            override val definition = registryTestDefinition("plugin-tool", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                executions += 1
                observedIdentity = context.installationIdentity
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
            }
        }
        val registration = ToolRegistration(source, ToolRegistrationOwner.Plugin(identity))
        val policy = object : ToolAccessPolicy {
            override fun canAdvertise(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ): Boolean = active && owner == ToolRegistrationOwner.Plugin(identity) &&
                context.installationIdentity == identity && context.scope == ToolScope.MAIN_SESSION &&
                context.inputMessage?.origin?.channelId == "qq"

            override suspend fun authorize(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ): ToolAccessDecision = if (
                active && owner == ToolRegistrationOwner.Plugin(identity) &&
                context.installationIdentity == identity && context.scope == ToolScope.MAIN_SESSION &&
                context.inputMessage?.origin?.channelId == "qq"
            ) {
                ToolAccessDecision.Allowed
            } else {
                ToolAccessDecision.Rejected(ToolAccessDenial.INSTALLATION_INACTIVE)
            }
        }
        val message = AgentMessage(
            messageId = "qq-message-1",
            origin = MessageRoute(channelId = "qq", conversationId = "chat-1", actorId = "user-1"),
            segments = emptyList(),
        )
        val accessContext = ToolAccessContext(
            scope = ToolScope.MAIN_SESSION,
            sessionId = "main",
            executionId = "execution-1",
            attemptId = "attempt-1",
            turnSequence = 4,
            historyUpperBoundExclusive = 4,
            inputMessage = message,
        )
        val registry = ToolRegistry(
            sources = emptyList(),
            registrations = listOf(registration),
            accessPolicy = policy,
        )

        assertEquals(listOf(source.definition), registry.definitionsFor(accessContext))
        assertTrue(registry.definitionsFor(accessContext.copy(scope = ToolScope.PERSONA_SESSION)).isEmpty())
        assertTrue(registry.definitionsFor(accessContext.copy(inputMessage = message.copy(
            origin = message.origin.copy(channelId = "app"),
        ))).isEmpty())

        active = false
        val revoked = registry.execute(
            ModelToolCall("call-1", "plugin-tool", "{\"text\":\"ok\"}"),
            ToolExecutionContext(
                sessionId = "main",
                turnSequence = 4,
                attemptId = "attempt-1",
                historyUpperBoundExclusive = 4,
                scope = ToolScope.MAIN_SESSION,
                executionId = "execution-1",
                inputMessage = message,
            ),
        )

        assertEquals(ToolResultStatus.REJECTED, revoked.status)
        assertEquals(0, executions)
        assertEquals("Tool access is not available.", revoked.content)

        active = true
        val allowed = registry.execute(
            ModelToolCall("call-2", "plugin-tool", "{\"text\":\"ok\"}"),
            ToolExecutionContext(
                sessionId = "main",
                turnSequence = 4,
                attemptId = "attempt-1",
                historyUpperBoundExclusive = 4,
                scope = ToolScope.MAIN_SESSION,
                executionId = "execution-1",
                inputMessage = message,
            ),
        )

        assertEquals(ToolResultStatus.SUCCESS, allowed.status)
        assertEquals(1, executions)
        assertEquals(identity, observedIdentity)
    }

    @Test
    fun pluginRegistrationFailsClosedWithoutHostAccessPolicy() = runTest {
        var executions = 0
        val source = object : ToolSource {
            override val definition = registryTestDefinition("private-plugin-tool", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                executions += 1
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "unexpected")
            }
        }
        val registry = ToolRegistry(
            sources = emptyList(),
            registrations = listOf(
                ToolRegistration(
                    source,
                    ToolRegistrationOwner.Plugin(ToolInstallationIdentity("private-plugin", "install-1")),
                ),
            ),
        )

        assertTrue(registry.definitionsFor(ToolScope.MAIN_SESSION).isEmpty())
        val result = registry.execute(
            ModelToolCall("call-1", "private-plugin-tool", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )

        assertEquals(ToolResultStatus.REJECTED, result.status)
        assertEquals(0, executions)
    }

    @Test
    fun rechecksPluginAccessAfterExecutionStateCallbackSuspends() = runTest {
        val identity = ToolInstallationIdentity("revocable-plugin", "install-3")
        var active = true
        var executions = 0
        val source = object : ToolSource {
            override val definition = registryTestDefinition("revocable-tool", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                executions += 1
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "unexpected")
            }
        }
        val policy = object : ToolAccessPolicy {
            override fun canAdvertise(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ) = active && owner == ToolRegistrationOwner.Plugin(identity)

            override suspend fun authorize(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ) = if (active && owner == ToolRegistrationOwner.Plugin(identity)) {
                ToolAccessDecision.Allowed
            } else {
                ToolAccessDecision.Rejected(ToolAccessDenial.INSTALLATION_INACTIVE)
            }
        }
        val registry = ToolRegistry(
            sources = emptyList(),
            registrations = listOf(ToolRegistration(source, ToolRegistrationOwner.Plugin(identity))),
            accessPolicy = policy,
        )
        val callbackEntered = CompletableDeferred<Unit>()
        val continueCallback = CompletableDeferred<Unit>()
        val states = mutableListOf<Boolean>()
        val execution = async {
            registry.execute(
                ModelToolCall("call-1", "revocable-tool", "{\"text\":\"ok\"}"),
                context(ToolScope.MAIN_SESSION),
            ) { activeState ->
                states += activeState
                if (activeState) {
                    callbackEntered.complete(Unit)
                    continueCallback.await()
                }
            }
        }

        callbackEntered.await()
        active = false
        continueCallback.complete(Unit)
        val result = execution.await()

        assertEquals(ToolResultStatus.REJECTED, result.status)
        assertEquals(listOf(true, false), states)
        assertEquals(0, executions)
    }

    @Test
    fun advertisementPropagatesCancellationFromHostPolicy() = runTest {
        val source = EchoTool("cancel-advertisement", setOf(ToolScope.MAIN_SESSION))
        val registry = ToolRegistry(
            sources = listOf(source),
            accessPolicy = object : ToolAccessPolicy {
                override fun canAdvertise(
                    definition: ToolDefinition,
                    owner: ToolRegistrationOwner,
                    context: ToolAccessContext,
                ): Boolean = throw CancellationException("cancelled")

                override suspend fun authorize(
                    definition: ToolDefinition,
                    owner: ToolRegistrationOwner,
                    context: ToolAccessContext,
                ) = ToolAccessDecision.Allowed
            },
        )

        try {
            registry.definitionsFor(ToolScope.MAIN_SESSION)
            throw AssertionError("Policy cancellation must propagate")
        } catch (_: CancellationException) {
            // Cancellation is not converted into a hidden-tool result.
        }
    }

    @Test
    fun policyFailureAndTimeoutFailClosedBeforeSourceExecution() = runTest {
        val identity = ToolInstallationIdentity("policy-plugin", "install-4")
        var executions = 0
        val source = object : ToolSource {
            override val definition = registryTestDefinition("policy-tool", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                executions += 1
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "unexpected")
            }
        }
        val registration = ToolRegistration(source, ToolRegistrationOwner.Plugin(identity))
        val failingPolicy = object : ToolAccessPolicy {
            override fun canAdvertise(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ) = true

            override suspend fun authorize(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ): ToolAccessDecision = error("policy unavailable")
        }
        val failing = ToolRegistry(
            sources = emptyList(),
            registrations = listOf(registration),
            accessPolicy = failingPolicy,
        ).execute(
            ModelToolCall("failure", "policy-tool", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )
        assertEquals(ToolResultStatus.REJECTED, failing.status)
        assertEquals(0, executions)

        val slowPolicy = object : ToolAccessPolicy {
            override fun canAdvertise(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ) = true

            override suspend fun authorize(
                definition: ToolDefinition,
                owner: ToolRegistrationOwner,
                context: ToolAccessContext,
            ): ToolAccessDecision {
                delay(1_000)
                return ToolAccessDecision.Allowed
            }
        }
        val timedOut = ToolRegistry(
            sources = emptyList(),
            executionTimeoutMillis = 10,
            registrations = listOf(registration),
            accessPolicy = slowPolicy,
        ).execute(
            ModelToolCall("timeout", "policy-tool", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )

        assertEquals(ToolResultStatus.FAILED, timedOut.status)
        assertTrue(timedOut.content.contains("timed out"))
        assertEquals(0, executions)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun callerCancellationDuringToolExecutionPropagatesAndClearsProgress() = runTest {
        val sourceStarted = CompletableDeferred<Unit>()
        val source = object : ToolSource {
            override val definition = registryTestDefinition("cancellable", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                sourceStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val states = mutableListOf<Boolean>()
        val job = launch {
            ToolRegistry(listOf(source)).execute(
                ModelToolCall("cancel", "cancellable", "{\"text\":\"ok\"}"),
                context(ToolScope.MAIN_SESSION),
                onExecutionState = states::add,
            )
        }

        runCurrent()
        assertTrue(sourceStarted.isCompleted)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun rejectsDuplicateNamesAndOutOfScopeOrInvalidArguments() = runTest {
        val source = EchoTool("echo", setOf(ToolScope.MAIN_SESSION))
        try {
            ToolRegistry(listOf(source, EchoTool("echo", setOf(ToolScope.PERSONA_SESSION), "duplicate")))
            throw AssertionError("Duplicate tool name must be rejected")
        } catch (_: IllegalArgumentException) {
            // Duplicate names are rejected at registration time.
        }

        val registry = ToolRegistry(listOf(source))
        assertTrue(registry.definitionsFor(ToolScope.PERSONA_SESSION).isEmpty())
        val wrongScope = registry.execute(
            ModelToolCall("call-1", "echo", "{\"text\":\"ok\"}"),
            context(ToolScope.PERSONA_SESSION),
        )
        val invalidArguments = registry.execute(
            ModelToolCall("call-2", "echo", "{\"text\":\"ok\",\"extra\":1}"),
            context(ToolScope.MAIN_SESSION),
        )
        val unknown = registry.execute(
            ModelToolCall("call-3", "missing", "{}"),
            context(ToolScope.MAIN_SESSION),
        )

        assertEquals(ToolResultStatus.REJECTED, wrongScope.status)
        assertEquals(ToolResultStatus.REJECTED, invalidArguments.status)
        assertEquals(ToolResultStatus.REJECTED, unknown.status)
        assertEquals("call-1", wrongScope.callId)
        assertEquals("call-3", unknown.callId)
    }

    @Test
    fun sourceProgressTextIsDiscardedAndHostStateHasOnlyExecutionBounds() = runTest {
        val secret = "api_key=tool-private-value"
        val source = object : ToolSource {
            override val definition = registryTestDefinition("progress", setOf(ToolScope.MAIN_SESSION))

            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                onProgress(ToolProgress(secret))
                onProgress(ToolProgress(secret.repeat(20)))
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
            }
        }
        val states = mutableListOf<Boolean>()
        val result = ToolRegistry(listOf(source)).execute(
            ModelToolCall("progress-call", "progress", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
            onExecutionState = states::add,
        )

        assertEquals(listOf(true, false), states)
        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertFalse(result.content.contains(secret))
    }

    @Test
    fun timesOutToolAndKeepsResultBounded() = runTest {
        val slow = object : ToolSource {
            override val definition = registryTestDefinition("slow", setOf(ToolScope.MAIN_SESSION))
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ): ToolExecutionResult {
                delay(1_000)
                return ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "late")
            }
        }
        val registry = ToolRegistry(listOf(slow), executionTimeoutMillis = 10, maxResultCharacters = 8)
        val timedOut = registry.execute(
            ModelToolCall("timeout", "slow", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )
        assertEquals(ToolResultStatus.FAILED, timedOut.status)
        assertTrue(timedOut.content.contains("timed out"))

        val longResult = object : ToolSource {
            override val definition = registryTestDefinition("long", setOf(ToolScope.MAIN_SESSION))
            override suspend fun execute(
                call: ModelToolCall,
                context: ToolExecutionContext,
                onProgress: suspend (ToolProgress) -> Unit,
            ) = ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "123456789012345")
        }
        val bounded = ToolRegistry(listOf(longResult), maxResultCharacters = 8).execute(
            ModelToolCall("bounded", "long", "{\"text\":\"ok\"}"),
            context(ToolScope.MAIN_SESSION),
        )
        assertEquals("12345678…[truncated]", bounded.content)
    }

    @Test
    fun historyReadUsesBoundSessionAndFiltersRecordsPastUpperBound() = runTest {
        val reader = RecordingHistoryReader()
        val source = HistoryReadToolSource(reader, maxPageSize = 10, tokenBudget = 100)
        val registry = ToolRegistry(listOf(source))
        val result = registry.execute(
            ModelToolCall("history-1", "HistoryRead", "{\"fromTurn\":2,\"toTurn\":5,\"limit\":4}"),
            context(ToolScope.PERSONA_SESSION).copy(sessionId = "private-session", historyUpperBoundExclusive = 5),
        )
        val json = Json.parseToJsonElement(result.content).jsonObject
        val records = json["records"]!!.jsonArray

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertEquals("private-session", reader.lastRequest!!.sessionId)
        assertEquals(5L, reader.lastRequest!!.upperBoundExclusive)
        assertEquals(HistoryDirection.OLDEST_TO_NEWEST, reader.lastRequest!!.direction)
        assertEquals(2, records.size)
        assertEquals(2L, records.first().jsonObject["turnSequence"]!!.jsonPrimitive.content.toLong())
        assertTrue(records.first().jsonObject["successfulSilence"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(result.content.contains("secret-after-bound"))
        assertEquals("current", json["sessionScope"]!!.jsonPrimitive.content)
    }

    private fun context(scope: ToolScope) = ToolExecutionContext(
        sessionId = "session-1",
        turnSequence = 10,
        attemptId = "attempt-1",
        historyUpperBoundExclusive = 10,
        scope = scope,
    )

    private class EchoTool(name: String, scopes: Set<ToolScope>, idSuffix: String = name) : ToolSource {
        override val definition = registryTestDefinition(name, scopes).copy(id = "test.$idSuffix")

        override suspend fun execute(
            call: ModelToolCall,
            context: ToolExecutionContext,
            onProgress: suspend (ToolProgress) -> Unit,
        ) = ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
    }

    private class RecordingHistoryReader : HistoryReader {
        var lastRequest: HistoryReadRequest? = null

        override suspend fun read(request: HistoryReadRequest): HistoryReadPage {
            lastRequest = request
            return HistoryReadPage(
                records = listOf(
                    HistoryRecord("m2", 2, 0, HistoryRole.USER, "retained", HistoryMessageStatus.COMPLETE, successfulSilence = true),
                    HistoryRecord("m4", 4, 1, HistoryRole.ASSISTANT, "also retained", HistoryMessageStatus.STOPPED),
                    HistoryRecord("m5", 5, 0, HistoryRole.USER, "secret-after-bound", HistoryMessageStatus.COMPLETE),
                ),
                continuation = HistoryCursor(4, 1),
                hasMore = true,
            )
        }
    }
}

private fun registryTestDefinition(name: String, scopes: Set<ToolScope>) = ToolDefinition(
    id = "test.$name",
    name = name,
    description = "test tool",
    parametersJsonSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}""",
    scopes = scopes,
)
