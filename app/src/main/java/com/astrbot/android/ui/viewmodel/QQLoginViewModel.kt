package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.model.NapCatLoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QQLoginViewModel : ViewModel() {
    val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    init {
        viewModelScope.launch {
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

    fun quickLoginSavedAccount() {
        launchIo { NapCatLoginRepository.quickLoginSavedAccount() }
    }

    fun saveQuickLoginAccount(uin: String) {
        launchIo { NapCatLoginRepository.saveQuickLoginAccount(uin) }
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

    fun newDeviceLogin(uin: String, password: String) {
        launchIo { NapCatLoginRepository.newDeviceLogin(uin, password) }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }
        }
    }
}
