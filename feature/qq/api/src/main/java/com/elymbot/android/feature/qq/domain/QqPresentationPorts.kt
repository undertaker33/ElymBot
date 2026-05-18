package com.elymbot.android.feature.qq.domain

fun interface QqWebUiCredentialPort {
    fun getOrCreateWebUiToken(): String
}

fun interface QqPresentationLogPort {
    fun append(message: String)
}
