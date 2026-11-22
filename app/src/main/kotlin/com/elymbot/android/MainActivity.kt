package com.elymbot.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.elymbot.android.data.AppearanceMode
import com.elymbot.android.data.AppearancePreferences
import com.elymbot.android.ui.ElymBotApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splash.remove()
            } else {
                val icon = splash.iconView
                val overlay = splash.view
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = SplashExitDurationMillis
                    interpolator = PathInterpolator(0.20f, 0.54f, 0.30f, 1f)
                    addUpdateListener {
                        val progress = it.animatedFraction
                        val iconScale = 1f + 0.08f * progress
                        icon.scaleX = iconScale
                        icon.scaleY = iconScale
                        overlay.alpha = 1f - progress
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            splash.remove()
                        }
                    })
                    start()
                }
            }
        }
        val preferences = AppearancePreferences(this)
        var appearanceMode by mutableStateOf(preferences.read())
        val detectDarkMode: (Resources) -> Boolean = { resources ->
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            appearanceMode.useDarkTheme(nightMode == Configuration.UI_MODE_NIGHT_YES)
        }
        val statusStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDarkMode)
        val navigationStyle = SystemBarStyle.auto(DefaultLightNavigationScrim, DefaultDarkNavigationScrim, detectDarkMode)
        enableEdgeToEdge(statusStyle, navigationStyle)
        setContent {
            ElymBotApp(
                service = (application as ElymBotApplication).uiService,
                appearanceMode = appearanceMode,
                darkTheme = appearanceMode.useDarkTheme(isSystemInDarkTheme()),
                onAppearanceModeChange = { mode ->
                    if (appearanceMode != mode) {
                        preferences.write(mode)
                        appearanceMode = mode
                        enableEdgeToEdge(statusStyle, navigationStyle)
                    }
                },
            )
        }
    }
}

private const val SplashExitDurationMillis = 440L

private fun AppearanceMode.useDarkTheme(systemDark: Boolean): Boolean = when (this) {
    AppearanceMode.SYSTEM -> systemDark
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}

// AndroidX's default compatibility scrims for pre-Android 10 navigation bars.
private val DefaultLightNavigationScrim = Color.argb(230, 255, 255, 255)
private val DefaultDarkNavigationScrim = Color.argb(128, 27, 27, 27)
