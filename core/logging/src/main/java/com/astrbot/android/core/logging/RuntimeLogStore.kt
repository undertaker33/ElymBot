package com.astrbot.android.core.logging

import kotlinx.coroutines.flow.StateFlow

interface RuntimeLogStore {
    val logs: StateFlow<List<String>>

    fun append(message: String)

    fun flush()

    fun clear()
}

