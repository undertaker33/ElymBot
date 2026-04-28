package com.astrbot.android.core.logging

fun interface RuntimeLogSink {
    fun append(message: String)
}

