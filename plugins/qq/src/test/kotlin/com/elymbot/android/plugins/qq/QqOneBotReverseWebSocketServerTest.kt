package com.elymbot.android.plugins.qq

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class QqOneBotReverseWebSocketServerTest {
    @Test
    fun requiresExactBearerTokenBeforeWebSocketUpgrade() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = QqOneBotReverseWebSocketServer(
            onEvent = {},
            onConnectionChanged = {},
            token = TEST_TOKEN,
            port = port,
        )
        try {
            server.start(scope)

            assertTrue(request(port, authorization = null).startsWith("HTTP/1.1 401"))
            assertTrue(request(port, authorization = "Bearer wrong-token").startsWith("HTTP/1.1 401"))
            assertTrue(request(port, authorization = "Bearer $TEST_TOKEN").startsWith("HTTP/1.1 101"))
        } finally {
            server.close()
            scope.cancel()
        }
    }

    private fun request(port: Int, authorization: String?): String = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
        val request = buildString {
            append("GET /ws HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
            authorization?.let { append("Authorization: $it\r\n") }
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.US_ASCII))
            flush()
        }
        BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII)).use { it.readLine().orEmpty() }
    }

    private companion object {
        const val TEST_TOKEN = "synthetic-test-token-1234567890"
        const val SOCKET_TIMEOUT_MILLIS = 2_000
    }
}
