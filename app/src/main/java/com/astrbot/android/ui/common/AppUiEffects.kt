package com.astrbot.android.ui.common

import com.astrbot.android.ui.navigation.AppUiTransitionState
import kotlinx.coroutines.delay

suspend internal fun runWithAppUiTransition(
    transitionState: AppUiTransitionState,
    colorArgb: Int,
    action: suspend () -> Unit,
) {
    transitionState.requestTransition(colorArgb)
    delay(50)
    action()
}
