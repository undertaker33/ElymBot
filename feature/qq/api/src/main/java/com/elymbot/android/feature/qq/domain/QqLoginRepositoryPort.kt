package com.elymbot.android.feature.qq.domain

import com.elymbot.android.feature.qq.domain.model.NapCatLoginState
import com.elymbot.android.feature.qq.domain.model.SavedQqAccount
import kotlinx.coroutines.flow.StateFlow

fun interface QqWebUiTokenProvider {
    fun getOrCreateWebUiToken(): String
}

data class QqNewDeviceQrCodeResult(
    val qrUrl: String,
    val bytesToken: String,
)

data class QqNewDeviceQrPollResult(
    val guaranteeStatus: Int,
    val successToken: String,
)

interface QqLoginRepositoryPort {
    val loginState: StateFlow<NapCatLoginState>

    fun refresh(
        webUiTokenProvider: QqWebUiTokenProvider,
        manual: Boolean = false,
    ): NapCatLoginState

    fun refreshQrCode(webUiTokenProvider: QqWebUiTokenProvider)

    fun quickLoginSavedAccount(
        uin: String? = null,
        webUiTokenProvider: QqWebUiTokenProvider,
    )

    fun saveQuickLoginAccount(
        uin: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    )

    suspend fun logoutCurrentAccount(webUiTokenProvider: QqWebUiTokenProvider)

    fun passwordLogin(
        uin: String,
        password: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    )

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    )

    fun newDeviceLogin(
        uin: String,
        password: String,
        verifiedToken: String? = null,
        webUiTokenProvider: QqWebUiTokenProvider,
    )

    fun getNewDeviceQRCode(webUiTokenProvider: QqWebUiTokenProvider): QqNewDeviceQrCodeResult

    fun pollNewDeviceQRCode(
        bytesToken: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    ): QqNewDeviceQrPollResult

    fun restoreSavedLoginState(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    )
}
