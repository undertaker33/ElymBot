package com.elymbot.core.tool

import com.elymbot.core.contract.ModelToolCall
import com.elymbot.core.contract.ReplySubmissionResult
import com.elymbot.core.contract.ReplySubmitter
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyToolSourceTest {
    @Test
    fun rejectsAggregateMessageTextAboveConfiguredLimitBeforeSubmission() = runBlocking {
        var submissions = 0
        val source = ReplyToolSource(
            ReplySubmitter {
                submissions += 1
                ReplySubmissionResult.Accepted(emptyList())
            },
        )
        val registry = ToolRegistry(listOf(source))
        val arguments = buildJsonObject {
            put("messages", buildJsonArray {
                repeat(4) {
                    add(buildJsonObject {
                        put("text", "x".repeat(ReplyToolSource.MAX_MESSAGE_CHARACTERS))
                        put("delayMillis", 0)
                    })
                }
            })
        }.toString()

        val result = registry.execute(
            ModelToolCall("reply-call", ReplyToolSource.TOOL_NAME, arguments),
            ToolExecutionContext(
                sessionId = "session",
                turnSequence = 1,
                attemptId = "attempt",
                historyUpperBoundExclusive = 1,
                scope = ToolScope.MAIN_SESSION,
            ),
        )

        assertEquals(ToolResultStatus.REJECTED, result.status)
        assertTrue(result.content.contains("invalid"))
        assertEquals(0, submissions)
    }

    @Test
    fun callerCancellationDuringDurableSubmissionPropagates() = runTest {
        val submitStarted = CompletableDeferred<Unit>()
        val registry = ToolRegistry(
            listOf(
                ReplyToolSource(
                    ReplySubmitter {
                        submitStarted.complete(Unit)
                        awaitCancellation()
                    },
                ),
            ),
        )
        val job = launch {
            registry.execute(
                ModelToolCall("reply-call", ReplyToolSource.TOOL_NAME, """{"messages":[{"text":"hello","delayMillis":0}]}"""),
                ToolExecutionContext(
                    sessionId = "session",
                    turnSequence = 1,
                    attemptId = "attempt",
                    historyUpperBoundExclusive = 1,
                    scope = ToolScope.MAIN_SESSION,
                ),
            )
        }

        submitStarted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }
}
