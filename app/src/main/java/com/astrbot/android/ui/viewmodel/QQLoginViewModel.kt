package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.NapCatLoginService
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QQLoginViewModel : ViewModel() {
    val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState
    private val pollingTracker = QQLoginPollingTracker()
    private var pollingJob: Job? = null
    private var buildMarkerLogged = false

    fun onScreenVisible(screenTag: String, versionMarker: String) {
        when (pollingTracker.onScreenVisible(screenTag)) {
            QQLoginPollingTransition.START -> {
                logBuildMarkerIfNeeded(versionMarker)
                RuntimeLogRepository.append(
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
                RuntimeLogRepository.append("QQ login polling stopped: no visible QQ login screens remain")
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
                    withContext(Dispatchers.IO) {
                        NapCatLoginRepository.refresh()
                    }
                }
                delay(if (loginState.value.isLogin) 5000 else 3000)
            }
        }
    }

    fun refreshNow() {
        launchIo { NapCatLoginRepository.refresh(manual = true) }
    }

    fun refreshQrCode() {
        launchIo { NapCatLoginRepository.refreshQrCode() }
    }

    fun quickLoginSavedAccount(uin: String? = null) {
        launchIo { NapCatLoginRepository.quickLoginSavedAccount(uin) }
    }

    fun saveQuickLoginAccount(uin: String) {
        launchIo { NapCatLoginRepository.saveQuickLoginAccount(uin) }
    }

    fun logoutCurrentAccount() {
        launchIo { NapCatLoginRepository.logoutCurrentAccount() }
    }

    fun passwordLogin(uin: String, password: String) {
        launchIo { NapCatLoginRepository.passwordLogin(uin, password) }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        launchIo { NapCatLoginRepository.captchaLogin(uin, password, ticket, randstr, sid) }
    }

    fun newDeviceLogin(uin: String, password: String, verifiedToken: String? = null) {
        launchIo { NapCatLoginRepository.newDeviceLogin(uin, password, verifiedToken) }
    }

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return withContext(Dispatchers.IO) {
            NapCatLoginRepository.getNewDeviceQRCode()
        }
    }

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return withContext(Dispatchers.IO) {
            NapCatLoginRepository.pollNewDeviceQRCode(bytesToken)
        }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }
        }
    }

    private fun logBuildMarkerIfNeeded(versionMarker: String) {
        if (buildMarkerLogged) return
        buildMarkerLogged = true
        RuntimeLogRepository.append(versionMarker)
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

    fun activeScreenSummary(): String = activeScreens.joinToString(separator = ",")
}

internal fun buildQqLoginVersionMarker(versionName: String, versionCode: Long): String {
    return "QQ login diagnostics build: versionName=$versionName versionCode=$versionCode marker=qq-login-diag-v3-poll-singleton"
}
