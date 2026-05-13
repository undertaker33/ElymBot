package com.astrbot.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
class AppUiTransitionState {
    private val _transitionEvents = MutableStateFlow(0L)
    val transitionEvents = _transitionEvents.asStateFlow()

    private var activeColor: Int? = null

    fun requestTransition(colorArgb: Int) {
        activeColor = colorArgb
        _transitionEvents.value += 1
    }

    fun currentColor(): Int? = activeColor

    fun clearColor(colorArgb: Int?) {
        if (colorArgb == null) return
        if (activeColor == colorArgb) {
            activeColor = null
        }
    }
}

@Composable
fun rememberAppUiTransitionState(): AppUiTransitionState {
    return remember { AppUiTransitionState() }
}

val LocalAppUiTransitionState = compositionLocalOf { AppUiTransitionState() }
