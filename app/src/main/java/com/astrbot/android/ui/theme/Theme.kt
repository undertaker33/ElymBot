package com.astrbot.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Clay,
    secondary = Moss,
    background = Sand,
    surface = Sand,
    onPrimary = Sand,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    secondary = Moss,
)

@Composable
fun AstrBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AstrBotTypography,
        content = content,
    )
}
