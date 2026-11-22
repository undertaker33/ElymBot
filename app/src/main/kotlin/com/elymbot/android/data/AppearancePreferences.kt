package com.elymbot.android.data

import android.content.Context

internal enum class AppearanceMode { SYSTEM, LIGHT, DARK }

internal class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): AppearanceMode = when (preferences.getString(APPEARANCE_MODE_KEY, null)) {
        AppearanceMode.LIGHT.name -> AppearanceMode.LIGHT
        AppearanceMode.DARK.name -> AppearanceMode.DARK
        else -> AppearanceMode.SYSTEM
    }

    fun write(mode: AppearanceMode) {
        preferences.edit().putString(APPEARANCE_MODE_KEY, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "elymbot_appearance_preferences"
        const val APPEARANCE_MODE_KEY = "appearance_mode"
    }
}
