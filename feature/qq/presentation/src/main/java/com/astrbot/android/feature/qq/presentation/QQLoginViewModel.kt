package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.astrbot.android.feature.qq.domain.QqNewDeviceQrCodeResult
import com.astrbot.android.feature.qq.domain.QqNewDeviceQrPollResult
import com.astrbot.android.feature.qq.domain.QqPresentationLogPort
import com.astrbot.android.feature.qq.domain.QqWebUiCredentialPort
import com.astrbot.android.feature.qq.domain.QqWebUiTokenProvider
import com.astrbot.android.feature.qq.domain.model.NapCatLoginState
import com.astrbot.android.feature.qq.presentation.QqLoginState
import com.astrbot.android.feature.qq.presentation.QqPresentationIoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface QQLoginViewModelBindings {
    val loginState: StateFlow<NapCatLoginState>

    suspend fun refresh(manual: Boolean = false)

    suspend fun refreshQrCode()

    suspend fun quickLoginSavedAccount(uin: String? = null)

    suspend fun saveQuickLoginAccount(uin: String)

    suspend fun logoutCurrentAccount()

    suspend fun passwordLogin(uin: String, password: String)

    suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String)

    suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?)

    suspend fun getNewDeviceQRCode(): QqNewDeviceQrCodeResult

    suspend fun pollNewDeviceQRCode(bytesToken: String): QqNewDeviceQrPollResult

    fun log(message: String)
}

internal class DefaultQQLoginViewModelBindings @Inject constructor(
    @QqLoginState override val loginState: StateFlow<NapCatLoginState>,
    private val loginRepository: QqLoginRepositoryPort,
    webUiCredentialPort: QqWebUiCredentialPort,
    private val presentationLogPort: QqPresentationLogPort,
) : QQLoginViewModelBindings {
    private val webUiTokenProvider = QqWebUiTokenProvider {
        webUiCredentialPort.getOrCreateWebUiToken()
    }

    override suspend fun refresh(manual: Boolean) {
        loginRepository.refresh(webUiTokenProvider = webUiTokenProvider, manual = manual)
    }

    override suspend fun refreshQrCode() {
        loginRepository.refreshQrCode(webUiTokenProvider = webUiTokenProvider)
    }

    override suspend fun quickLoginSavedAccount(uin: String?) {
        loginRepository.quickLoginSavedAccount(uin = uin, webUiTokenProvider = webUiTokenProvider)
    }

    override suspend fun saveQuickLoginAccount(uin: String) {
        loginRepository.saveQuickLoginAccount(uin = uin, webUiTokenProvider = webUiTokenProvider)
    }

    override suspend fun logoutCurrentAccount() {
        loginRepository.logoutCurrentAccount(webUiTokenProvider = webUiTokenProvider)
    }

    override suspend fun passwordLogin(uin: String, password: String) {
        loginRepository.passwordLogin(
            uin = uin,
            password = password,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override suspend fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        loginRepository.captchaLogin(
            uin = uin,
            password = password,
            ticket = ticket,
            randstr = randstr,
            sid = sid,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?) {
        loginRepository.newDeviceLogin(
            uin = uin,
            password = password,
            verifiedToken = verifiedToken,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override suspend fun getNewDeviceQRCode(): QqNewDeviceQrCodeResult {
        return loginRepository.getNewDeviceQRCode(webUiTokenProvider = webUiTokenProvider)
    }

    override suspend fun pollNewDeviceQRCode(bytesToken: String): QqNewDeviceQrPollResult {
        return loginRepository.pollNewDeviceQRCode(
            bytesToken = bytesToken,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override fun log(message: String) {
        presentationLogPort.append(message)
    }
}

@HiltViewModel
class QQLoginViewModel @Inject constructor(
    private val bindings: QQLoginViewModelBindings,
    @QqPresentationIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val loginState: StateFlow<NapCatLoginState> = bindings.loginState

    private val pollingTracker = QQLoginPollingTracker()
    private var pollingJob: Job? = null
    private var buildMarkerLogged = false
    private var autoQrRefreshIssuedForBlankState = false

    fun onScreenVisible(screenTag: String, versionMarker: String) {
        when (pollingTracker.onScreenVisible(screenTag)) {
            QQLoginPollingTransition.START -> {
                logBuildMarkerIfNeeded(versionMarker)
                log(
                    "QQ login polling started: screens=${pollingTracker.activeScreenSummary()} intervalLoggedOutMs=3000 intervalLoggedInMs=5000",
                )
                startPollingLoop()
            }

            QQLoginPollingTransition.KEEP_RUNNING,
            QQLoginPollingTransition.NO_CHANGE,
            QQLoginPollingTransition.STOP,
            -> Unit
        }
    }

    fun onScreenHidden(screenTag: String) {
        when (pollingTracker.onScreenHidden(screenTag)) {
            QQLoginPollingTransition.STOP -> {
                pollingJob?.cancel()
                pollingJob = null
                autoQrRefreshIssuedForBlankState = false
                log("QQ login polling stopped: no visible QQ login screens remain")
            }

            QQLoginPollingTransition.START,
            QQLoginPollingTransition.KEEP_RUNNING,
            QQLoginPollingTransition.NO_CHANGE,
            -> Unit
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        pollingJob = null
        super.onCleared()
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    withContext(ioDispatcher) {
                        bindings.refresh()
                        maybeRequestQrCodeAutomatically()
                    }
                }
                delay(if (loginState.value.isLogin) 5000 else 3000)
            }
        }
    }

    fun refreshNow() {
        launchIo { bindings.refresh(manual = true) }
    }

    fun refreshQrCode() {
        launchIo { bindings.refreshQrCode() }
    }

    fun quickLoginSavedAccount(uin: String? = null) {
        launchIo { bindings.quickLoginSavedAccount(uin) }
    }

    fun saveQuickLoginAccount(uin: String) {
        launchIo { bindings.saveQuickLoginAccount(uin) }
    }

    fun logoutCurrentAccount() {
        launchIo { bindings.logoutCurrentAccount() }
    }

    fun passwordLogin(uin: String, password: String) {
        launchIo { bindings.passwordLogin(uin, password) }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        launchIo { bindings.captchaLogin(uin, password, ticket, randstr, sid) }
    }

    fun newDeviceLogin(uin: String, password: String, verifiedToken: String? = null) {
        launchIo { bindings.newDeviceLogin(uin, password, verifiedToken) }
    }

    suspend fun getNewDeviceQRCode(): QqNewDeviceQrCodeResult {
        return withContext(ioDispatcher) {
            bindings.getNewDeviceQRCode()
        }
    }

    suspend fun pollNewDeviceQRCode(bytesToken: String): QqNewDeviceQrPollResult {
        return withContext(ioDispatcher) {
            bindings.pollNewDeviceQRCode(bytesToken)
        }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { block() }
        }
    }

    private fun logBuildMarkerIfNeeded(versionMarker: String) {
        if (buildMarkerLogged) return
        buildMarkerLogged = true
        log(versionMarker)
    }

    private suspend fun maybeRequestQrCodeAutomatically() {
        if (!pollingTracker.isScreenVisible("qq-login")) {
            autoQrRefreshIssuedForBlankState = false
            return
        }
        val state = loginState.value
        val needsQrBootstrap = state.bridgeReady && !state.isLogin && state.qrCodeUrl.isBlank()
        if (!needsQrBootstrap) {
            autoQrRefreshIssuedForBlankState = false
            return
        }
        if (autoQrRefreshIssuedForBlankState) {
            return
        }
        autoQrRefreshIssuedForBlankState = true
        bindings.refreshQrCode()
    }

    private fun log(message: String) {
        bindings.log(message)
    }
}

internal enum class QQLoginPollingTransition {
    START,
    KEEP_RUNNING,
    STOP,
    NO_CHANGE,
}

internal class QQLoginPollingTracker {
    private val activeScreens = linkedSetOf<String>()

    fun onScreenVisible(screenTag: String): QQLoginPollingTransition {
        val normalizedTag = screenTag.trim()
        if (normalizedTag.isBlank()) return QQLoginPollingTransition.NO_CHANGE
        if (!activeScreens.add(normalizedTag)) return QQLoginPollingTransition.NO_CHANGE
        return if (activeScreens.size == 1) {
            QQLoginPollingTransition.START
        } else {
            QQLoginPollingTransition.KEEP_RUNNING
        }
    }

    fun onScreenHidden(screenTag: String): QQLoginPollingTransition {
        val normalizedTag = screenTag.trim()
        if (normalizedTag.isBlank()) return QQLoginPollingTransition.NO_CHANGE
        if (!activeScreens.remove(normalizedTag)) return QQLoginPollingTransition.NO_CHANGE
        return if (activeScreens.isEmpty()) {
            QQLoginPollingTransition.STOP
        } else {
            QQLoginPollingTransition.KEEP_RUNNING
        }
    }

    fun activeScreenCount(): Int = activeScreens.size

    fun isScreenVisible(screenTag: String): Boolean = activeScreens.contains(screenTag.trim())

    fun activeScreenSummary(): String = activeScreens.joinToString(separator = ",")
}

internal fun buildQqLoginVersionMarker(versionName: String, versionCode: Long): String {
    return "QQ login diagnostics build: versionName=$versionName versionCode=$versionCode marker=qq-login-diag-v3-poll-singleton"
}
