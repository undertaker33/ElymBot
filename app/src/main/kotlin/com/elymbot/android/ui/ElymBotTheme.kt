package com.elymbot.android.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ElymBotColors(
    val paper: Color,
    val paperTop: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val line: Color,
    val danger: Color,
    val onDanger: Color,
    val success: Color,
    val successBackground: Color,
    val scrim: Color,
    val floatingBorder: Color,
    val portraitBase: Color,
    val onPortrait: Color,
)

private val LightColors = ElymBotColors(
    paper = Color(0xFFF7F0E6),
    paperTop = Color(0xFFFCF7EF),
    surface = Color(0xFFFFFBF5),
    ink = Color(0xFF35251D),
    muted = Color(0xFF735D50),
    accent = Color(0xFF7B4733),
    onAccent = Color(0xFFFFF8F2),
    soft = Color(0xFFF1E0D1),
    line = Color(0xFFE5D7C8),
    danger = Color(0xFFA52B32),
    onDanger = Color(0xFFFFF8F2),
    success = Color(0xFF356445),
    successBackground = Color(0xFFE9EFE2),
    scrim = Color(0x6B261A18),
    floatingBorder = Color(0xFFFFFDF8),
    portraitBase = Color(0xFF7B4733),
    onPortrait = Color(0xFFFFF8F2),
)

private val DarkColors = ElymBotColors(
    paper = Color(0xFF201915),
    paperTop = Color(0xFF2B211B),
    surface = Color(0xFF2F241E),
    ink = Color(0xFFF7E9DA),
    muted = Color(0xFFCCB8A9),
    accent = Color(0xFFEFBA9D),
    onAccent = Color(0xFF41291E),
    soft = Color(0xFF4B342B),
    line = Color(0xFF574139),
    danger = Color(0xFFFF9A9A),
    onDanger = Color(0xFF40191B),
    success = Color(0xFFB0D8AF),
    successBackground = Color(0xFF263B2C),
    scrim = Color(0xA6000000),
    floatingBorder = Color(0x40F5D9C2),
    portraitBase = Color(0xFF724C3B),
    onPortrait = Color(0xFFF7E9DA),
)

private val LightScheme = with(LightColors) {
    lightColorScheme(primary = accent, onPrimary = onAccent, surface = surface, onSurface = ink, background = paper, error = danger)
}

private val DarkScheme = with(DarkColors) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = soft,
        onPrimaryContainer = accent,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = soft,
        onSurfaceVariant = muted,
        surfaceContainer = surface,
        surfaceContainerHigh = Color(0xFF392C24),
        outline = muted,
        outlineVariant = line,
        error = danger,
        onError = onDanger,
    )
}

private val LocalElymBotColors = staticCompositionLocalOf { LightColors }

internal val ElymColors: ElymBotColors
    @Composable @ReadOnlyComposable get() = LocalElymBotColors.current

internal val Paper: Color @Composable @ReadOnlyComposable get() = ElymColors.paper
internal val PaperTop: Color @Composable @ReadOnlyComposable get() = ElymColors.paperTop
internal val SurfaceColor: Color @Composable @ReadOnlyComposable get() = ElymColors.surface
internal val Ink: Color @Composable @ReadOnlyComposable get() = ElymColors.ink
internal val Muted: Color @Composable @ReadOnlyComposable get() = ElymColors.muted
internal val Accent: Color @Composable @ReadOnlyComposable get() = ElymColors.accent
internal val AccentInk: Color @Composable @ReadOnlyComposable get() = ElymColors.onAccent
internal val Soft: Color @Composable @ReadOnlyComposable get() = ElymColors.soft
internal val Line: Color @Composable @ReadOnlyComposable get() = ElymColors.line
internal val Danger: Color @Composable @ReadOnlyComposable get() = ElymColors.danger

@Composable
internal fun ElymBotTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val contentColor = if (dark) colors.ink else LocalContentColor.current
    CompositionLocalProvider(LocalElymBotColors provides colors, LocalContentColor provides contentColor) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
