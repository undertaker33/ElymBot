package com.elymbot.android.core.logging

import android.util.Log

class AndroidRuntimeLogSink(
    private val tag: String = "ElymBotRuntime",
) : RuntimeLogSink {
    override fun append(message: String) {
        runCatching { Log.i(tag, message) }
    }
}

