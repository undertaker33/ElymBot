package com.astrbot.android.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.astrbot.android.ui.navigation.AppMotionTokens

@Composable
fun rememberPulsingAlpha(
    initialValue: Float = 0.35f,
    targetValue: Float = 1f,
    durationMillis: Int = AppMotionTokens.recordingPulseMillis,
    label: String,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    val value by infiniteTransition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Value",
    )
    return value
}

@Composable
fun rememberPulsingScale(
    initialValue: Float = 1f,
    targetValue: Float = 1.14f,
    durationMillis: Int = AppMotionTokens.voiceButtonPulseMillis,
    label: String,
): Float {
    return rememberPulsingAlpha(
        initialValue = initialValue,
        targetValue = targetValue,
        durationMillis = durationMillis,
        label = label,
    )
}

suspend fun LazyListState.animateToItemWithAppMotion(index: Int) {
    animateScrollToItem(index)
}
