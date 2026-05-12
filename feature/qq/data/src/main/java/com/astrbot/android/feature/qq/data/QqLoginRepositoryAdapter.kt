package com.astrbot.android.feature.qq.data

import com.astrbot.android.core.runtime.container.ContainerRuntimeController
import com.astrbot.android.core.runtime.container.RuntimeBridgeController
import com.astrbot.android.feature.qq.domain.QqLoginRepositoryPort
import com.astrbot.android.feature.qq.domain.QqNewDeviceQrCodeResult
import com.astrbot.android.feature.qq.domain.QqNewDeviceQrPollResult
import com.astrbot.android.feature.qq.domain.QqWebUiTokenProvider
import com.astrbot.android.feature.qq.domain.model.NapCatBridgeConfig
import com.astrbot.android.feature.qq.domain.model.NapCatLoginState
import com.astrbot.android.feature.qq.domain.model.NapCatRuntimeState
import com.astrbot.android.feature.qq.domain.model.SavedQqAccount
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class QqLoginRepositoryAdapter @Inject constructor(
    private val loginRepository: NapCatLoginRepository,
    private val containerRuntimeControllerProvider: Provider<ContainerRuntimeController>,
    private val runtimeBridgeControllerProvider: Provider<RuntimeBridgeController>,
) : QqLoginRepositoryPort {
    override val loginState: StateFlow<NapCatLoginState> = loginRepository.loginState

    override fun refresh(
        webUiTokenProvider: QqWebUiTokenProvider,
        manual: Boolean,
    ): NapCatLoginState {
        return loginRepository.refresh(
            webUiTokenProvider = webUiTokenProvider,
            manual = manual,
        )
    }

    override fun refreshQrCode(webUiTokenProvider: QqWebUiTokenProvider) {
        loginRepository.refreshQrCode(webUiTokenProvider)
    }

    override fun quickLoginSavedAccount(
        uin: String?,
        webUiTokenProvider: QqWebUiTokenProvider,
    ) {
        loginRepository.quickLoginSavedAccount(
            uin = uin,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override fun saveQuickLoginAccount(
        uin: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    ) {
        loginRepository.saveQuickLoginAccount(
            uin = uin,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override suspend fun logoutCurrentAccount(webUiTokenProvider: QqWebUiTokenProvider) {
        loginRepository.logoutCurrentAccount(
            containerRuntimeController = containerRuntimeControllerProvider.get(),
            runtimeBridgeController = runtimeBridgeControllerProvider.get(),
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override fun passwordLogin(
        uin: String,
        password: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    ) {
        loginRepository.passwordLogin(
            uin = uin,
            password = password,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
        webUiTokenProvider: QqWebUiTokenProvider,
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

    override fun newDeviceLogin(
        uin: String,
        password: String,
        verifiedToken: String?,
        webUiTokenProvider: QqWebUiTokenProvider,
    ) {
        loginRepository.newDeviceLogin(
            uin = uin,
            password = password,
            verifiedToken = verifiedToken,
            webUiTokenProvider = webUiTokenProvider,
        )
    }

    override fun getNewDeviceQRCode(webUiTokenProvider: QqWebUiTokenProvider): QqNewDeviceQrCodeResult {
        val result = loginRepository.getNewDeviceQRCode(webUiTokenProvider)
        return QqNewDeviceQrCodeResult(
            qrUrl = result.qrUrl,
            bytesToken = result.bytesToken,
        )
    }

    override fun pollNewDeviceQRCode(
        bytesToken: String,
        webUiTokenProvider: QqWebUiTokenProvider,
    ): QqNewDeviceQrPollResult {
        val result = loginRepository.pollNewDeviceQRCode(
            bytesToken = bytesToken,
            webUiTokenProvider = webUiTokenProvider,
        )
        return QqNewDeviceQrPollResult(
            guaranteeStatus = result.guaranteeStatus,
            successToken = result.successToken,
        )
    }

    override fun restoreSavedLoginState(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    ) {
        loginRepository.restoreSavedLoginState(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }

    internal fun installBridgeStateAccessors(
        configSnapshot: () -> NapCatBridgeConfig,
        runtimeStateSnapshot: () -> NapCatRuntimeState,
    ) {
        loginRepository.installBridgeStateAccessors(
            configSnapshot = configSnapshot,
            runtimeStateSnapshot = runtimeStateSnapshot,
        )
    }

    internal fun bootstrapFromLocalStore(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    ) {
        loginRepository.bootstrapFromLocalStore(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }

}
