package com.elymbot.android.core.logging

fun interface RuntimeLogSink {
    fun append(message: String)
}

