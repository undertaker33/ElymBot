package com.elymbot.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppGuidePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableSkipped = MutableStateFlow(preferences.getBoolean(SKIPPED_KEY, false))

    val skipped: StateFlow<Boolean> = mutableSkipped.asStateFlow()

    fun skip() {
        check(preferences.edit().putBoolean(SKIPPED_KEY, true).commit())
        mutableSkipped.value = true
    }

    private companion object {
        const val PREFERENCES_NAME = "elymbot_app_guide_preferences"
        const val SKIPPED_KEY = "skipped"
    }
}
