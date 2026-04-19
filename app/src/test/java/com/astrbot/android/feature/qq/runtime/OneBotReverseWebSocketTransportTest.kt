package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotReverseWebSocketTransportTest {
    @Test
    fun send_without_active_socket_returns_failure() {
        val transport = OneBotReverseWebSocketTransport(
            port = 6199,
            path = "/ws",
            authToken = "token",
            onPayload = {},
            log = {},
        )

        val result = transport.send("""{"action":"send_private_msg"}""")

        assertTrue(result.isFailure)
        assertEquals("reverse_ws_not_connected", result.exceptionOrNull()?.message)
    }

    @Test
    fun authorization_rejects_missing_token() {
        assertFalse(
            isAuthorizedOneBotReverseWebSocket(
                headers = emptyMap(),
                authToken = "token",
            ),
        )
    }

    @Test
    fun authorization_rejects_blank_token() {
        assertFalse(
            isAuthorizedOneBotReverseWebSocket(
                headers = mapOf("authorization" to "Bearer   "),
                authToken = "token",
            ),
        )
    }

    @Test
    fun authorization_rejects_wrong_token() {
        assertFalse(
            isAuthorizedOneBotReverseWebSocket(
                headers = mapOf("authorization" to "Bearer wrong"),
                authToken = "token",
            ),
        )
    }

    @Test
    fun authorization_rejects_blank_auth_token_config() {
        assertFalse(
            isAuthorizedOneBotReverseWebSocket(
                headers = mapOf("authorization" to "Bearer anything"),
                authToken = "",
            ),
        )
    }

    @Test
    fun authorization_accepts_bearer_token() {
        assertTrue(
            isAuthorizedOneBotReverseWebSocket(
                headers = mapOf("authorization" to "Bearer token"),
                authToken = "token",
            ),
        )
    }

    @Test
    fun authorization_accepts_raw_token_for_existing_clients() {
        assertTrue(
            isAuthorizedOneBotReverseWebSocket(
                headers = mapOf("authorization" to "token"),
                authToken = "token",
            ),
        )
    }
}
