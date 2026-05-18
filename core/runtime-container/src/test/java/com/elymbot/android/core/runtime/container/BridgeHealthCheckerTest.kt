package com.elymbot.android.core.runtime.container

import com.elymbot.android.core.runtime.network.RuntimeNetworkCapability
import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkResponse
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.elymbot.android.core.runtime.network.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHealthCheckerTest {

    @Test
    fun check_usesInjectedRuntimeNetworkTransport() = runBlocking {
        val transport = RecordingTransport(
            responses = listOf(
                RuntimeNetworkResponse(
                    statusCode = 204,
                    headers = emptyMap(),
                    bodyBytes = ByteArray(0),
                    traceId = "health",
                    durationMs = 3,
                ),
            ),
        )

        val result = BridgeHealthChecker(transport).check("http://127.0.0.1:6099")

        assertTrue(result.ok)
        assertEquals(204, result.code)
        assertEquals("HTTP 204", result.message)
        assertEquals(1, transport.requests.size)
        assertEquals("GET", transport.requests.single().method)
        assertEquals(RuntimeNetworkCapability.ACTIVE_CAPABILITY, transport.requests.single().capability)
        assertEquals(RuntimeTimeoutProfile.ACTIVE_CAPABILITY_CALLBACK, transport.requests.single().timeoutProfile)
        assertEquals(3000L, transport.requests.single().connectTimeoutMs)
        assertEquals(3000L, transport.requests.single().readTimeoutMs)
    }

    @Test
    fun checkWithRetry_retriesUntilTransportReturnsHealthyResponse() = runBlocking {
        val transport = RecordingTransport(
            responses = listOf(
                RuntimeNetworkResponse(
                    statusCode = 503,
                    headers = emptyMap(),
                    bodyBytes = "warming".toByteArray(),
                    traceId = "health-1",
                    durationMs = 3,
                ),
                RuntimeNetworkResponse(
                    statusCode = 200,
                    headers = emptyMap(),
                    bodyBytes = ByteArray(0),
                    traceId = "health-2",
                    durationMs = 3,
                ),
            ),
        )

        val result = BridgeHealthChecker(transport).checkWithRetry(
            url = "http://127.0.0.1:6099",
            attempts = 2,
            delayMs = 0,
        )

        assertTrue(result.ok)
        assertEquals(200, result.code)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun check_returnsFailureForBlankUrlWithoutCallingTransport() = runBlocking {
        val transport = RecordingTransport()

        val result = BridgeHealthChecker(transport).check("")

        assertFalse(result.ok)
        assertEquals(-1, result.code)
        assertEquals("Health URL is blank", result.message)
        assertTrue(transport.requests.isEmpty())
    }

    private class RecordingTransport(
        private val responses: List<RuntimeNetworkResponse> = emptyList(),
    ) : RuntimeNetworkTransport {
        val requests = mutableListOf<RuntimeNetworkRequest>()

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            requests += request
            return responses.getOrElse(requests.lastIndex) {
                RuntimeNetworkResponse(
                    statusCode = 500,
                    headers = emptyMap(),
                    bodyBytes = ByteArray(0),
                    traceId = "default",
                    durationMs = 0,
                )
            }
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> {
            error("Bridge health checks must not open text streams")
        }

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> {
            error("Bridge health checks must not open SSE streams")
        }
    }
}
