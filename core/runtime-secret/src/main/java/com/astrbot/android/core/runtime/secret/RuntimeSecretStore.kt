package com.astrbot.android.core.runtime.secret

interface RuntimeSecretStore {
    fun getOrCreateSecrets(): RuntimeSecrets

    fun ensureSecrets(): RuntimeSecrets = getOrCreateSecrets()

    fun getOrCreateWebUiToken(): String = getOrCreateSecrets().webUiToken

    fun getOrCreateWebUiJwtSecret(): String = getOrCreateSecrets().webUiJwtSecret
}

data class RuntimeSecrets(
    val webUiToken: String,
    val webUiJwtSecret: String,
)
