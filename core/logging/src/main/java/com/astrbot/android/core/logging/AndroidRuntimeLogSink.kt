package com.astrbot.android.core.logging

import android.util.Log

class AndroidRuntimeLogSink(
    private val tag: String = "AstrBotRuntime",
) : RuntimeLogSink {
    override fun append(message: String) {
        runCatching { Log.i(tag, message) }
    }
}

