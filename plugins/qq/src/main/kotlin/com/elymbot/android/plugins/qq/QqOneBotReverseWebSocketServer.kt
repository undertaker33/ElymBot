package com.elymbot.android.plugins.qq

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal sealed interface QqOneBotActionResult {
    data class Response(val json: JsonObject) : QqOneBotActionResult

    data object NotConnected : QqOneBotActionResult

    data object Unknown : QqOneBotActionResult
}

/** Minimal loopback reverse-WebSocket endpoint matching ON-04's NapCat configuration. */
internal class QqOneBotReverseWebSocketServer(
    private val onEvent: suspend (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val token: String,
    private val port: Int = ONEBOT_PORT,
) : AutoCloseable {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var currentConnection: QqOneBotSocketConnection? = null

    private var acceptJob: Job? = null
    private var scope: CoroutineScope? = null

    val isConnected: Boolean
        get() = currentConnection?.isOpen == true

    @Synchronized
    fun start(runtimeScope: CoroutineScope) {
        if (acceptJob?.isActive == true) return
        val listeningSocket = ServerSocket()
        listeningSocket.reuseAddress = true
        listeningSocket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port), 1)
        serverSocket = listeningSocket
        scope = runtimeScope
        acceptJob = runtimeScope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    listeningSocket.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: IOException) {
                    if (!isActive) break else continue
                }
                runtimeScope.launch(Dispatchers.IO) { handleClient(client) }
            }
        }
    }

    suspend fun sendAction(action: String, params: JsonObject): QqOneBotActionResult {
        val connection = currentConnection?.takeIf { it.isOpen } ?: return QqOneBotActionResult.NotConnected
        return connection.sendAction(action, params)
    }

    override fun close() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        currentConnection?.close()
        currentConnection = null
        onConnectionChanged(false)
        scope = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                if (!client.inetAddress.isLoopbackAddress) return
                client.tcpNoDelay = true
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())
                val headers = readRequestHeaders(input)
                if (!isAuthorizedWebSocketRequest(headers)) {
                    output.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(UTF8))
                    output.flush()
                    return
                }
                val key = headers.entries.firstOrNull { it.key.equals("Sec-WebSocket-Key", ignoreCase = true) }
                    ?.value
                    ?: throw IOException("WebSocket handshake is invalid")
                val accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                        .digest((key.trim() + WEBSOCKET_GUID).toByteArray(UTF8)),
                )
                output.write(
                    (
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n\r\n"
                        ).toByteArray(UTF8),
                )
                output.flush()
                val connection = QqOneBotSocketConnection(client, input, output, onEvent) { closed ->
                    synchronized(this) {
                        if (currentConnection === closed) {
                            currentConnection = null
                            onConnectionChanged(false)
                        }
                    }
                }
                synchronized(this) {
                    currentConnection?.close()
                    currentConnection = connection
                    onConnectionChanged(true)
                }
                connection.readLoop()
            } catch (_: IOException) {
                // A client that disconnects during handshake or frame transfer is discarded.
            } finally {
                if (currentConnection?.socket === client) {
                    currentConnection?.close()
                    currentConnection = null
                    onConnectionChanged(false)
                }
            }
        }
    }

    private fun isAuthorizedWebSocketRequest(headers: Map<String, String>): Boolean {
        val requestLine = headers[REQUEST_LINE_HEADER] ?: return false
        if (!requestLine.startsWith("GET /ws ")) return false
        val upgrade = headers.entries.firstOrNull { it.key.equals("Upgrade", ignoreCase = true) }
            ?.value
            ?.equals("websocket", ignoreCase = true) == true
        val authorization = headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value
            ?.trim()
        return upgrade && authorization == "Bearer $token"
    }

    private fun readRequestHeaders(input: BufferedInputStream): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (bytes.size() < MAX_HANDSHAKE_BYTES) {
            val next = input.read()
            if (next < 0) throw EOFException("WebSocket handshake ended early")
            bytes.write(next)
            matched = when {
                matched == 0 && next == '\r'.code -> 1
                matched == 1 && next == '\n'.code -> 2
                matched == 2 && next == '\r'.code -> 3
                matched == 3 && next == '\n'.code -> 4
                next == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) break
        }
        if (matched != 4) throw IOException("WebSocket handshake is too large")
        val lines = bytes.toString(UTF8.name()).split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty()
        val headers = linkedMapOf(REQUEST_LINE_HEADER to requestLine)
        lines.drop(1).forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }
        return headers
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val ONEBOT_PORT = 6199
        const val MAX_HANDSHAKE_BYTES = 16 * 1024
        const val REQUEST_LINE_HEADER = "<request-line>"
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val UTF8: Charset = Charsets.UTF_8
    }
}

private class QqOneBotSocketConnection(
    val socket: Socket,
    private val input: BufferedInputStream,
    private val output: BufferedOutputStream,
    private val onEvent: suspend (String) -> Unit,
    private val onClosed: (QqOneBotSocketConnection) -> Unit,
) : AutoCloseable {
    private val pendingActions = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val writeLock = Any()

    @Volatile
    var isOpen: Boolean = true
        private set

    suspend fun sendAction(action: String, params: JsonObject): QqOneBotActionResult {
        if (!isOpen || socket.isClosed) return QqOneBotActionResult.NotConnected
        val echo = UUID.randomUUID().toString()
        val response = CompletableDeferred<JsonObject>()
        pendingActions[echo] = response
        val request = buildJsonObject {
            put("action", action)
            put("params", params)
            put("echo", echo)
        }.toString()
        try {
            writeFrame(OPCODE_TEXT, request.toByteArray(UTF8))
        } catch (_: Exception) {
            pendingActions.remove(echo)
            return QqOneBotActionResult.Unknown
        }
        return try {
            QqOneBotActionResult.Response(withTimeout(ACTION_TIMEOUT_MILLIS) { response.await() })
        } catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) {
                QqOneBotActionResult.Unknown
            } else {
                throw cancelled
            }
        } catch (_: Exception) {
            QqOneBotActionResult.Unknown
        } finally {
            pendingActions.remove(echo)
        }
    }

    suspend fun readLoop() {
        var fragmentOpcode = 0
        val fragmentedPayload = ByteArrayOutputStream()
        try {
            while (isOpen) {
                val frame = readFrame() ?: break
                when (frame.opcode) {
                    OPCODE_CLOSE -> break
                    OPCODE_PING -> writeFrame(OPCODE_PONG, frame.payload)
                    OPCODE_PONG -> Unit
                    OPCODE_TEXT -> {
                        if (fragmentOpcode != 0) throw IOException("Nested WebSocket fragments are invalid")
                        if (frame.final) {
                            handleText(frame.payload)
                        } else {
                            fragmentOpcode = OPCODE_TEXT
                            fragmentedPayload.reset()
                            fragmentedPayload.write(frame.payload)
                        }
                    }
                    OPCODE_CONTINUATION -> {
                        if (fragmentOpcode == 0) throw IOException("Unexpected WebSocket continuation")
                        if (fragmentedPayload.size() + frame.payload.size > MAX_FRAME_BYTES) {
                            throw IOException("WebSocket message exceeded its limit")
                        }
                        fragmentedPayload.write(frame.payload)
                        if (frame.final) {
                            handleText(fragmentedPayload.toByteArray())
                            fragmentedPayload.reset()
                            fragmentOpcode = 0
                        }
                    }
                    else -> if (frame.opcode >= OPCODE_CLOSE) throw IOException("Unsupported WebSocket control frame")
                }
            }
        } finally {
            close()
        }
    }

    private suspend fun handleText(payload: ByteArray) {
        val text = String(payload, UTF8)
        val response = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val echo = response?.get("echo")?.jsonPrimitive?.contentOrNull
        if (echo != null && pendingActions.remove(echo)?.complete(response) == true) return
        onEvent(text)
    }

    private fun readFrame(): WebSocketFrame? {
        val first = input.read()
        if (first < 0) return null
        val second = input.read()
        if (second < 0) throw EOFException("WebSocket frame ended early")
        val final = first and 0x80 != 0
        if (first and 0x70 != 0) throw IOException("WebSocket extensions are unsupported")
        val opcode = first and 0x0f
        val masked = second and 0x80 != 0
        if (!masked) throw IOException("NapCat WebSocket frames must be masked")
        var length = (second and 0x7f).toLong()
        if (length == 126L) {
            length = ((readRequiredByte().toLong() shl 8) or readRequiredByte().toLong())
        } else if (length == 127L) {
            length = 0
            repeat(8) { length = (length shl 8) or readRequiredByte().toLong() }
            if (length < 0) throw IOException("Invalid WebSocket frame length")
        }
        if (length > MAX_FRAME_BYTES) throw IOException("WebSocket frame exceeded its limit")
        if (opcode >= OPCODE_CLOSE && (!final || length > MAX_CONTROL_FRAME_BYTES)) {
            throw IOException("Invalid WebSocket control frame")
        }
        val mask = ByteArray(4)
        readFully(mask)
        val payload = ByteArray(length.toInt())
        readFully(payload)
        payload.indices.forEach { index -> payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte() }
        return WebSocketFrame(final, opcode, payload)
    }

    private fun writeFrame(opcode: Int, payload: ByteArray) {
        if (!isOpen) throw IOException("OneBot connection is closed")
        if (payload.size > MAX_FRAME_BYTES) throw IOException("WebSocket payload exceeded its limit")
        synchronized(writeLock) {
            output.write(0x80 or opcode)
            when {
                payload.size < 126 -> output.write(payload.size)
                payload.size <= 0xffff -> {
                    output.write(126)
                    output.write(payload.size ushr 8)
                    output.write(payload.size)
                }
                else -> {
                    output.write(127)
                    for (shift in 56 downTo 0 step 8) output.write(payload.size.toLong().ushr(shift).toInt())
                }
            }
            output.write(payload)
            output.flush()
        }
    }

    private fun readRequiredByte(): Int = input.read().takeIf { it >= 0 }
        ?: throw EOFException("WebSocket frame ended early")

    private fun readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw EOFException("WebSocket frame ended early")
            offset += count
        }
    }

    override fun close() {
        if (!isOpen) return
        isOpen = false
        pendingActions.values.forEach { it.completeExceptionally(IOException("OneBot connection closed")) }
        pendingActions.clear()
        runCatching { socket.close() }
        onClosed(this)
    }

    private data class WebSocketFrame(val final: Boolean, val opcode: Int, val payload: ByteArray)

    private companion object {
        const val OPCODE_CONTINUATION = 0x0
        const val OPCODE_TEXT = 0x1
        const val OPCODE_CLOSE = 0x8
        const val OPCODE_PING = 0x9
        const val OPCODE_PONG = 0xa
        const val MAX_CONTROL_FRAME_BYTES = 125
        const val MAX_FRAME_BYTES = 1_048_576
        const val ACTION_TIMEOUT_MILLIS = 10_000L
        val UTF8: Charset = Charsets.UTF_8
    }
}
