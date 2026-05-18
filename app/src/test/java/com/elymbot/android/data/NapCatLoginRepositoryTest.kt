package com.elymbot.android.data

import com.elymbot.android.feature.qq.domain.QqWebUiTokenProvider
import com.elymbot.android.model.NapCatLoginState
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapCatLoginRepositoryTest {
    private val runtimeWebUiTokenProvider = QqWebUiTokenProvider { "runtime-webui-token" }
    private val loginService = NapCatLoginTestFixtures.loginService
    private val repository = NapCatLoginTestFixtures.repository

    @After
    fun tearDown() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        NapCatLoginTestFixtures.reset()
        setLoginState(NapCatLoginState())
    }

    @Test
    fun refresh_preserves_logged_in_state_when_bridge_runtime_is_temporarily_unavailable() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        setLoginState(
            NapCatLoginState(
                bridgeReady = true,
                authenticated = true,
                isLogin = true,
                uin = "123456",
                nick = "Test QQ",
                statusText = "QQ login ready",
            ),
        )

        val state = repository.refresh(webUiTokenProvider = runtimeWebUiTokenProvider)

        assertTrue(state.isLogin)
        assertFalse(state.bridgeReady)
        assertTrue(state.authenticated)
        assertEquals("123456", state.uin)
        assertEquals("Test QQ", state.nick)
        assertEquals("Bridge is not connected", state.statusText)
    }

    @Test
    fun refresh_qr_code_ignores_rapid_repeat_requests() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        NapCatBridgeRepository.markRunning()
        setLoginState(
            NapCatLoginState(
                bridgeReady = true,
                isLogin = false,
                qrCodeUrl = "",
                statusText = "Waiting for login QR code",
            ),
        )
        var refreshQrCodeCalls = 0
        loginService.setPostJsonOverrideForTests { endpoint, _, _ ->
            when {
                endpoint.endsWith("/auth/login") -> JSONObject().apply {
                    put("code", 0)
                    put("data", JSONObject().put("Credential", "stable-token"))
                }

                endpoint.endsWith("/QQLogin/RefreshQRcode") -> JSONObject().apply {
                    refreshQrCodeCalls += 1
                    put("code", 0)
                    put("message", "success")
                    put("data", JSONObject())
                }

                endpoint.endsWith("/QQLogin/CheckLoginStatus") -> JSONObject().apply {
                    put("code", 0)
                    put(
                        "data",
                        JSONObject().apply {
                            put("isLogin", false)
                            put("isOffline", false)
                            put("qrcodeurl", "")
                        },
                    )
                }

                else -> error("Unexpected endpoint: $endpoint")
            }
        }

        repository.refreshQrCode(webUiTokenProvider = runtimeWebUiTokenProvider)
        repository.refreshQrCode(webUiTokenProvider = runtimeWebUiTokenProvider)

        assertEquals(1, refreshQrCodeCalls)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setLoginState(state: NapCatLoginState) {
        val field = repository::class.java.getDeclaredField("_loginState").apply {
            isAccessible = true
        }
        val flow = field.get(repository) as MutableStateFlow<NapCatLoginState>
        flow.value = state
    }
}
