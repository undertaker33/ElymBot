package com.astrbot.android.ui.common

import com.astrbot.android.ui.navigation.AppUiTransitionState
import kotlinx.coroutines.delay

suspend internal fun runWithAppUiTransition(
    colorArgb: Int,
    action: suspend () -> Unit,
) {
    AppUiTransitionState.requestTransition(colorArgb)
    delay(50)
    action()
}
