package com.elymbot.android.core.common.logging

fun interface RuntimeLogger {
    fun append(message: String)

    companion object {
        fun noop(): RuntimeLogger = RuntimeLogger { }
    }
}

