package com.astrbot.android.data.http

import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AstrBotHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        RuntimeLogRepository.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `execute supports get request and redacts api keys in logs`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("X-Test", "ok")
                .setBody("""{"ok":true}"""),
        )
        val client = OkHttpAstrBotHttpClient()

        val response = client.execute(
            HttpRequestSpec(
                method = HttpMethod.GET,
                url = server.url("/v1/models?key=secret-value&alt=sse").toString(),
            ),
        )

        assertEquals(200, response.code)
        assertEquals("""{"ok":true}""", response.body)
        assertEquals("ok", response.headers["X-Test"]?.single())
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("key=***") })
        assertFalse(RuntimeLogRepository.logs.value.any { it.contains("secret-value") })
    }

    @Test
    fun `execute sends json body and custom headers`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"created":true}"""),
        )
        val client = OkHttpAstrBotHttpClient()

        client.execute(
            HttpRequestSpec(
                method = HttpMethod.POST,
                url = server.url("/chat/completions").toString(),
                headers = mapOf("Authorization" to "Bearer test-token"),
                body = """{"message":"hello"}""",
                contentType = "application/json",
            ),
        )

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        requireNotNull(request)
        assertEquals("POST", request.method)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("""{"message":"hello"}""", request.body.readUtf8())
        assertEquals("application/json", request.getHeader("Content-Type"))
    }

    @Test
    fun `execute maps read timeout to timeout category`() {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        val client = OkHttpAstrBotHttpClient()

        val error = runCatching {
            client.execute(
                HttpRequestSpec(
                    method = HttpMethod.GET,
                    url = server.url("/slow").toString(),
                    readTimeoutMs = 100,
                ),
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error is AstrBotHttpException)
        assertEquals(HttpFailureCategory.TIMEOUT, (error as AstrBotHttpException).category)
    }

    @Test
    fun `execute stream emits response lines in order`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("data: first\n\ndata: second\n\n"),
        )
        val client = OkHttpAstrBotHttpClient()
        val lines = mutableListOf<String>()

        runBlocking {
            client.executeStream(
                HttpRequestSpec(
                    method = HttpMethod.POST,
                    url = server.url("/stream").toString(),
                    body = "{}",
                    contentType = "application/json",
                ),
            ) { line: String ->
                lines += line
            }
        }

        assertEquals(listOf("data: first", "", "data: second", ""), lines)
    }

    @Test
    fun `execute bytes returns raw binary payload`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(byteArrayOf(1, 2, 3, 4))),
        )
        val client = OkHttpAstrBotHttpClient()

        val bytes = client.executeBytes(
            HttpRequestSpec(
                method = HttpMethod.GET,
                url = server.url("/binary").toString(),
            ),
        )

        assertEquals(listOf<Byte>(1, 2, 3, 4), bytes.toList())
    }

    @Test
    fun `execute stream on 400 includes error body in exception message`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"invalid request: empty messages"}}"""),
        )
        val client = OkHttpAstrBotHttpClient()

        val error = runCatching {
            runBlocking {
                client.executeStream(
                    HttpRequestSpec(
                        method = HttpMethod.POST,
                        url = server.url("/chat").toString(),
                        body = "{}",
                        contentType = "application/json",
                    ),
                ) { /* no-op */ }
            }
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message!!.contains("400"))
        assertTrue(error.message!!.contains("empty messages"))
    }

    @Test
    fun `execute bytes on 400 includes error body in exception message`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"bad input"}"""),
        )
        val client = OkHttpAstrBotHttpClient()

        val error = runCatching {
            client.executeBytes(
                HttpRequestSpec(
                    method = HttpMethod.GET,
                    url = server.url("/audio").toString(),
                ),
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message!!.contains("400"))
        assertTrue(error.message!!.contains("bad input"))
    }

    @Test
    fun `sanitize error body redacts api keys`() {
        val client = OkHttpAstrBotHttpClient()
        val raw = """{"error":"fail","api_key":"sk-12345secret"}"""
        val sanitized = client.sanitizeErrorBody(raw)
        assertTrue(sanitized.contains("error"))
        assertFalse(sanitized.contains("sk-12345secret"))
    }

    @Test
    fun `sanitize error body truncates long bodies`() {
        val client = OkHttpAstrBotHttpClient()
        val raw = "x".repeat(2000)
        val sanitized = client.sanitizeErrorBody(raw)
        assertTrue(sanitized.length <= 1000)
    }
}
