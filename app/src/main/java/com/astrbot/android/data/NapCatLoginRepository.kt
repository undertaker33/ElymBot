package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NapCatLoginRepository {
    private const val PREFS_NAME = "napcat_login_state"
    private const val KEY_LAST_QUICK_LOGIN_UIN = "last_quick_login_uin"

    private var preferences: SharedPreferences? = null
    private val _loginState = MutableStateFlow(NapCatLoginState())
    val loginState: StateFlow<NapCatLoginState> = _loginState.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedQuickLoginUin = loadSavedQuickLoginUin()
        if (savedQuickLoginUin.isNotBlank()) {
            _loginState.value = _loginState.value.copy(quickLoginUin = savedQuickLoginUin)
        }
    }

    fun markBridgeUnavailable(details: String) {
        NapCatLoginService.clearCredential()
        _loginState.value = _loginState.value.copy(
            bridgeReady = false,
            authenticated = false,
            isLogin = false,
            isOffline = false,
            qrCodeUrl = "",
            quickLoginUin = _loginState.value.quickLoginUin.ifBlank { loadSavedQuickLoginUin() },
            statusText = details,
            loginError = "",
            lastUpdated = System.currentTimeMillis(),
        )
    }

    fun refresh(manual: Boolean = false): NapCatLoginState {
        val healthUrl = requireBridgeHealthUrl() ?: return _loginState.value

        return try {
            var nextState = mergeWithPendingChallenge(
                NapCatLoginService.fetchLoginState(healthUrl),
            )
            if (nextState.isLogin && nextState.uin.isNotBlank() && nextState.quickLoginUin != nextState.uin) {
                runCatching {
                    NapCatLoginService.setQuickLoginAccount(healthUrl, nextState.uin)
                }.onSuccess {
                    saveQuickLoginUinLocally(nextState.uin)
                    nextState = nextState.copy(quickLoginUin = nextState.uin)
                    RuntimeLogRepository.append("QQ quick login account auto-saved: ${nextState.uin}")
                }.onFailure { error ->
                    RuntimeLogRepository.append(
                        "QQ quick login account auto-save failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            } else if (nextState.isLogin && nextState.uin.isNotBlank()) {
                saveQuickLoginUinLocally(nextState.uin)
                if (nextState.quickLoginUin.isBlank()) {
                    nextState = nextState.copy(quickLoginUin = nextState.uin)
                }
            }
            _loginState.value = nextState
            if (manual) {
                RuntimeLogRepository.append(
                    "QQ login state refreshed: login=${nextState.isLogin} offline=${nextState.isOffline} quick=${nextState.quickLoginUin.ifBlank { "-" }}",
                )
            }
            nextState
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                authenticated = false,
                statusText = message,
                loginError = message,
                lastUpdated = System.currentTimeMillis(),
            )
            if (manual) {
                RuntimeLogRepository.append("QQ login refresh error: $message")
            }
            _loginState.value
        }
    }

    fun refreshQrCode() {
        val healthUrl = requireBridgeHealthUrl() ?: return
        try {
            NapCatLoginService.refreshQrCode(healthUrl)
            _loginState.value = clearedChallengeState(
                _loginState.value.copy(
                    bridgeReady = true,
                    statusText = "QQ login QR refresh requested",
                    loginError = "",
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            RuntimeLogRepository.append("QQ login QR refresh requested")
        } catch (error: Exception) {
            applyActionError("QQ login QR refresh error", error)
            return
        }
        refresh(manual = true)
    }

    fun quickLoginSavedAccount() {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val uin = _loginState.value.quickLoginUin.ifBlank {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "No quick login account is saved yet",
                loginError = "No quick login account is saved yet",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }
        try {
            NapCatLoginService.quickLogin(healthUrl, uin)
            _loginState.value = clearedChallengeState(
                _loginState.value.copy(
                    bridgeReady = true,
                    statusText = "Quick login requested for $uin",
                    loginError = "",
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            RuntimeLogRepository.append("QQ quick login requested: $uin")
        } catch (error: Exception) {
            applyActionError("QQ quick login failed", error)
            return
        }
        refresh(manual = true)
    }

    fun saveQuickLoginAccount(uin: String) {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val cleanedUin = uin.trim()
        if (cleanedUin.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "QQ account is required",
                loginError = "QQ account is required",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        try {
            NapCatLoginService.setQuickLoginAccount(healthUrl, cleanedUin)
            saveQuickLoginUinLocally(cleanedUin)
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                quickLoginUin = cleanedUin,
                statusText = "Quick login account saved: $cleanedUin",
                loginError = "",
                lastUpdated = System.currentTimeMillis(),
            )
            RuntimeLogRepository.append("QQ quick login account saved: $cleanedUin")
        } catch (error: Exception) {
            applyActionError("QQ quick login account save failed", error)
        }
    }

    fun passwordLogin(uin: String, password: String) {
        performLoginAction(
            actionLabel = "QQ password login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.passwordLogin(healthUrl, cleanedUin, cleanedPassword)
        }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        performLoginAction(
            actionLabel = "QQ captcha login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.captchaLogin(
                baseUrl = healthUrl,
                uin = cleanedUin,
                password = cleanedPassword,
                ticket = ticket.trim(),
                randstr = randstr.trim(),
                sid = sid.trim(),
            )
        }
    }

    fun newDeviceLogin(uin: String, password: String) {
        val sig = _loginState.value.newDeviceSig
        if (sig.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "No pending new device verification request",
                loginError = "No pending new device verification request",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        performLoginAction(
            actionLabel = "QQ new device login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.newDeviceLogin(
                baseUrl = healthUrl,
                uin = cleanedUin,
                password = cleanedPassword,
                newDeviceSig = sig,
            )
        }
    }

    private fun performLoginAction(
        actionLabel: String,
        uin: String,
        password: String,
        block: (String, String, String) -> NapCatLoginService.LoginActionResult,
    ) {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val cleanedUin = uin.trim()
        val cleanedPassword = password.trim()
        if (cleanedUin.isBlank() || cleanedPassword.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "QQ account and password are required",
                loginError = "QQ account and password are required",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        try {
            val result = block(healthUrl, cleanedUin, cleanedPassword)
            applyLoginActionResult(actionLabel, cleanedUin, result)
        } catch (error: Exception) {
            applyActionError("$actionLabel failed", error)
        }
    }

    private fun applyLoginActionResult(
        actionLabel: String,
        uin: String,
        result: NapCatLoginService.LoginActionResult,
    ) {
        when {
            result.needCaptcha -> {
                _loginState.value = _loginState.value.copy(
                    bridgeReady = true,
                    quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                    needCaptcha = true,
                    captchaUrl = result.captchaUrl,
                    needNewDevice = false,
                    newDeviceJumpUrl = "",
                    newDeviceSig = "",
                    statusText = "Captcha verification is required for password login",
                    loginError = "Captcha required",
                    lastUpdated = System.currentTimeMillis(),
                )
                RuntimeLogRepository.append("$actionLabel requires captcha verification")
            }

            result.needNewDevice -> {
                _loginState.value = _loginState.value.copy(
                    bridgeReady = true,
                    quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                    needCaptcha = false,
                    captchaUrl = "",
                    needNewDevice = true,
                    newDeviceJumpUrl = result.newDeviceJumpUrl,
                    newDeviceSig = result.newDeviceSig,
                    statusText = "New device verification is required",
                    loginError = "New device verification required",
                    lastUpdated = System.currentTimeMillis(),
                )
                RuntimeLogRepository.append("$actionLabel requires new device verification")
            }

            result.completed -> {
                _loginState.value = clearedChallengeState(
                    _loginState.value.copy(
                        bridgeReady = true,
                        quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                        statusText = "$actionLabel requested",
                        loginError = "",
                        lastUpdated = System.currentTimeMillis(),
                    ),
                )
                RuntimeLogRepository.append("$actionLabel requested: $uin")
                refresh(manual = true)
            }
        }
    }

    private fun mergeWithPendingChallenge(nextState: NapCatLoginState): NapCatLoginState {
        val current = _loginState.value
        val quickLoginUin = nextState.quickLoginUin
            .ifBlank { current.quickLoginUin }
            .ifBlank { loadSavedQuickLoginUin() }

        if (nextState.isLogin) {
            return clearedChallengeState(
                nextState.copy(
                    quickLoginUin = quickLoginUin,
                ),
            )
        }

        val hasPendingChallenge = current.needCaptcha || current.needNewDevice
        if (!hasPendingChallenge) {
            return nextState.copy(quickLoginUin = quickLoginUin)
        }

        return nextState.copy(
            quickLoginUin = quickLoginUin,
            needCaptcha = current.needCaptcha,
            captchaUrl = current.captchaUrl,
            needNewDevice = current.needNewDevice,
            newDeviceJumpUrl = current.newDeviceJumpUrl,
            newDeviceSig = current.newDeviceSig,
            statusText = current.statusText,
            loginError = current.loginError,
        )
    }

    private fun clearedChallengeState(state: NapCatLoginState): NapCatLoginState {
        return state.copy(
            needCaptcha = false,
            captchaUrl = "",
            needNewDevice = false,
            newDeviceJumpUrl = "",
            newDeviceSig = "",
        )
    }

    private fun applyActionError(prefix: String, error: Exception) {
        val message = error.message ?: error.javaClass.simpleName
        _loginState.value = _loginState.value.copy(
            bridgeReady = true,
            statusText = message,
            loginError = message,
            lastUpdated = System.currentTimeMillis(),
        )
        RuntimeLogRepository.append("$prefix: $message")
    }

    private fun saveQuickLoginUinLocally(uin: String) {
        val cleanedUin = uin.trim()
        if (cleanedUin.isBlank()) return
        preferences
            ?.edit()
            ?.putString(KEY_LAST_QUICK_LOGIN_UIN, cleanedUin)
            ?.apply()
    }

    private fun loadSavedQuickLoginUin(): String {
        return preferences
            ?.getString(KEY_LAST_QUICK_LOGIN_UIN, "")
            .orEmpty()
            .trim()
    }

    private fun requireBridgeHealthUrl(): String? {
        val bridgeState = NapCatBridgeRepository.runtimeState.value
        val healthUrl = NapCatBridgeRepository.config.value.healthUrl
        if (bridgeState.status != "Running") {
            val reason = if (bridgeState.details.isBlank()) "Waiting for NapCat bridge" else bridgeState.details
            markBridgeUnavailable(reason)
            return null
        }
        return healthUrl
    }
}
