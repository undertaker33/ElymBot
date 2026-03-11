package com.astrbot.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Clay,
    secondary = Moss,
    background = Sand,
    surface = Color.White,
    surfaceVariant = Color(0xFFE7E7E4),
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Color(0xFFD4D4D0),
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
