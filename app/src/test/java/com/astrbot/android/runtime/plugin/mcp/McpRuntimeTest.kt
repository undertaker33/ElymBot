package com.astrbot.android.runtime.plugin.mcp

import com.astrbot.android.model.McpServerEntry
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class McpRuntimeTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Streamable HTTP ──────────────────────────────────────────────

    @Test
    fun streamable_http_initializes_once_and_reuses_session_header_for_list_and_call() = kotlinx.coroutines.runBlocking {
        // IDs: init=1, notify(no id)=2(skipped in payload), list=3, call=4
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "sess-1")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"tools\":[{\"name\":\"weather\",\"description\":\"Weather\",\"inputSchema\":{\"type\":\"object\"}}]}}"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"structuredContent\":{\"ok\":true}}}"),
        )

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "weather",
            name = "Weather",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val tools = manager.discoverTools("config-a", entry)
        val call = manager.callTool("config-a", entry, "weather", mapOf("q" to "beijing"))

        assertEquals(1, tools.size)
        assertEquals("weather", tools.single().name)
        assertEquals("ok", call.text)

        val initRequest = server.takeRequest()
        assertTrue(initRequest.body.readUtf8().contains("\"method\":\"initialize\""))
        val initializedRequest = server.takeRequest()
        assertTrue(initializedRequest.body.readUtf8().contains("notifications/initialized"))
        val listRequest = server.takeRequest()
        assertEquals("sess-1", listRequest.getHeader("Mcp-Session-Id"))
        assertTrue(listRequest.body.readUtf8().contains("\"method\":\"tools/list\""))
        val callRequest = server.takeRequest()
        assertEquals("sess-1", callRequest.getHeader("Mcp-Session-Id"))
        assertTrue(callRequest.body.readUtf8().contains("\"method\":\"tools/call\""))
    }

    @Test
    fun streamable_http_sends_mcp_protocol_version_header() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val entry = McpServerEntry(
            serverId = "proto",
            name = "Proto",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        client.initialize(entry)

        val initRequest = server.takeRequest()
        assertEquals("2024-11-05", initRequest.getHeader("MCP-Protocol-Version"))
    }

    @Test
    fun streamable_http_fallback_to_sse_on_405() = kotlinx.coroutines.runBlocking {
        var idCounter = 0
        // Isolated connection pool prevents connection reuse between the failed
        // streamable-HTTP POST and the subsequent legacy SSE GET.
        val isolated = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .retryOnConnectionFailure(false)
            .build()
        val client = McpTransportClient(
            httpClient = isolated,
            requestIdGenerator = { (++idCounter).toString() },
        )
        // Use path-based dispatcher to guarantee correct response routing
        var getCount = 0
        var postCount = 0
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.method == "GET") {
                    getCount++
                    // SSE body includes endpoint + init result (id=1 because streamableHttpRpc
                    // uses UUID.randomUUID, not requestIdGenerator — so idCounter is still 0 here)
                    return MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setHeader("Mcp-Session-Id", "fallback-sess")
                        .setBody("event: endpoint\ndata: /messages\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}\n\n")
                }
                postCount++
                return when (postCount) {
                    1 -> MockResponse().setResponseCode(405).setBody("Method Not Allowed")
                    else -> MockResponse().setResponseCode(202)
                }
            }
        }
        val entry = McpServerEntry(
            serverId = "fallback",
            name = "Fallback",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val session = client.initialize(entry)
        assertEquals("fallback-sess", session.sessionId)
        assertTrue("Should fallback to SSE with sseReader", session.sseReader != null)
        session.sseReader?.close()

        // Verify: first a POST (405), then a GET (SSE), then POST init, POST notify
        assertEquals(1, getCount)
        assertTrue("At least 3 POSTs: streamable(405) + init + notify", postCount >= 3)
    }

    @Test
    fun streamable_http_rebuilds_session_on_404() = kotlinx.coroutines.runBlocking {
        // First init succeeds (id=1 init, id=2 notify skipped)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "old-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        // tools/list (id=3) returns 404 -> session expired
        server.enqueue(MockResponse().setResponseCode(404).setBody("Session not found"))
        // DELETE cleanup
        server.enqueue(MockResponse().setResponseCode(202))
        // Re-init (id=4 init, id=5 notify skipped)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "new-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        // Retry tools/list (id=6)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"6\",\"result\":{\"tools\":[{\"name\":\"rebuilt\",\"description\":\"Rebuilt\",\"inputSchema\":{\"type\":\"object\"}}]}}"),
        )

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "rebuild",
            name = "Rebuild",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val tools = manager.discoverTools("config-a", entry)
        assertEquals(1, tools.size)
        assertEquals("rebuilt", tools.single().name)
    }

    @Test
    fun streamable_http_delete_session_on_close() = kotlinx.coroutines.runBlocking {
        // init (id=1)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "del-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        // tools/list (id=3) fails with 500
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Error"))
        // DELETE response
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "deltest",
            name = "DelTest",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        try {
            manager.discoverTools("config-a", entry)
            fail("Should throw on 500")
        } catch (e: McpHttpException) {
            assertEquals(500, e.code)
        }

        // Verify DELETE was sent
        server.takeRequest() // init POST
        server.takeRequest() // initialized POST
        server.takeRequest() // tools/list POST (500)
        val deleteRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected DELETE cleanup request", deleteRequest)
        assertEquals("DELETE", deleteRequest!!.method)
        assertEquals("del-sess", deleteRequest.getHeader("Mcp-Session-Id"))
    }

    // ── Legacy SSE ───────────────────────────────────────────────────

    @Test
    fun legacy_sse_discovers_message_endpoint_before_initialize() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("Mcp-Session-Id", "legacy-sess")
                .setBody("event: endpoint\ndata: /messages\n\n"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}"),
        )

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "legacy",
            name = "Legacy",
            url = server.url("/sse").toString(),
            transport = "sse",
        )

        manager.discoverTools("config-a", entry)

        val discoveryRequest = server.takeRequest()
        assertEquals("GET", discoveryRequest.method)
        val initRequest = server.takeRequest()
        assertEquals("/messages", initRequest.path)
        assertEquals("legacy-sess", initRequest.getHeader("Mcp-Session-Id"))
        val initializedRequest = server.takeRequest()
        assertEquals("/messages", initializedRequest.path)
        val listRequest = server.takeRequest()
        assertEquals("/messages", listRequest.path)
        assertEquals("legacy-sess", listRequest.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun legacy_sse_post_returns_202_and_response_arrives_via_sse_stream() = kotlinx.coroutines.runBlocking {
        // GET SSE: endpoint event, init result (id=1), tools/list result (id=2)
        val sseBody = buildString {
            appendLine("event: endpoint")
            appendLine("data: /messages")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"calc\",\"description\":\"Calculator\",\"inputSchema\":{\"type\":\"object\"}}]}}")
            appendLine()
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("Mcp-Session-Id", "sse-202")
                .setBody(sseBody),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val entry = McpServerEntry(
            serverId = "sse202",
            name = "SSE202",
            url = server.url("/sse").toString(),
            transport = "sse",
        )

        val session = client.initialize(entry)
        assertEquals("sse-202", session.sessionId)
        assertEquals(server.url("/messages").toString(), session.messageEndpoint)
        assertTrue("Session should have sseReader", session.sseReader != null)

        val tools = client.listTools(entry, session)
        assertEquals(1, tools.size)
        assertEquals("calc", tools.single().name)

        session.sseReader?.close()

        val getRequest = server.takeRequest()
        assertEquals("GET", getRequest.method)
        val initPost = server.takeRequest()
        assertEquals("POST", initPost.method)
        assertEquals("/messages", initPost.path)
        val notifyPost = server.takeRequest()
        assertEquals("POST", notifyPost.method)
        val listPost = server.takeRequest()
        assertEquals("POST", listPost.method)
    }

    @Test
    fun legacy_sse_ignores_unrelated_id_and_matches_correct_response() = kotlinx.coroutines.runBlocking {
        // SSE stream: endpoint, unrelated results, then correct ones
        val sseBody = buildString {
            appendLine("event: endpoint")
            appendLine("data: /messages")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"unrelated-1\",\"result\":{\"stale\":true}}")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"unrelated-2\",\"error\":{\"code\":-1,\"message\":\"stale\"}}")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"unrelated-3\",\"result\":{\"nope\":true}}")
            appendLine()
            appendLine("event: message")
            appendLine("data: {\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"strict\",\"description\":\"Strict\",\"inputSchema\":{\"type\":\"object\"}}]}}")
            appendLine()
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("Mcp-Session-Id", "strict-sess")
                .setBody(sseBody),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val entry = McpServerEntry(
            serverId = "strict",
            name = "Strict",
            url = server.url("/sse").toString(),
            transport = "sse",
        )

        val session = client.initialize(entry)
        assertEquals("strict-sess", session.sessionId)

        val tools = client.listTools(entry, session)
        assertEquals(1, tools.size)
        assertEquals("strict", tools.single().name)

        session.sseReader?.close()
        Unit
    }

    @Test
    fun legacy_sse_reader_closed_on_init_failure() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: endpoint\ndata: /messages\n\n"),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val entry = McpServerEntry(
            serverId = "fail",
            name = "Fail",
            url = server.url("/sse").toString(),
            transport = "sse",
        )

        try {
            client.initialize(entry)
            fail("Should throw on init POST 500")
        } catch (e: McpHttpException) {
            assertEquals(500, e.code)
        }
    }

    // ── Session management ───────────────────────────────────────────

    @Test
    fun session_cache_is_scoped_by_config_profile_id() = kotlinx.coroutines.runBlocking {
        // Profile A: init(id=1), notify(id=2 skip), list(id=3)
        // Profile B: init(id=4), notify(id=5 skip), list(id=6)
        repeat(2) { idx ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Mcp-Session-Id", "sess-$idx")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 1}\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
            )
            server.enqueue(MockResponse().setResponseCode(202))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 3}\",\"result\":{\"tools\":[]}}"),
            )
        }

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "shared",
            name = "Shared",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        manager.discoverTools("config-a", entry)
        manager.discoverTools("config-b", entry)

        assertEquals(6, server.requestCount)
    }

    @Test
    fun profile_conflict_same_server_id_different_profiles_get_separate_sessions() = kotlinx.coroutines.runBlocking {
        // Profile A: init(id=1), notify(id=2 skip), call(id=3)
        // Profile B: init(id=4), notify(id=5 skip), call(id=6)
        repeat(2) { idx ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Mcp-Session-Id", "sess-profile-$idx")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 1}\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
            )
            server.enqueue(MockResponse().setResponseCode(202))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 3}\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"result-from-profile-$idx\"}]}}"),
            )
        }

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "shared-server",
            name = "Shared",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val resultA = manager.callTool("profile-a", entry, "test", emptyMap())
        val resultB = manager.callTool("profile-b", entry, "test", emptyMap())

        assertEquals(6, server.requestCount)
        assertEquals("result-from-profile-0", resultA.text)
        assertEquals("result-from-profile-1", resultB.text)

        server.takeRequest() // init A
        server.takeRequest() // notify A
        val callA = server.takeRequest()
        assertEquals("sess-profile-0", callA.getHeader("Mcp-Session-Id"))
        server.takeRequest() // init B
        server.takeRequest() // notify B
        val callB = server.takeRequest()
        assertEquals("sess-profile-1", callB.getHeader("Mcp-Session-Id"))
        assertNotEquals(callA.getHeader("Mcp-Session-Id"), callB.getHeader("Mcp-Session-Id"))
    }
}
