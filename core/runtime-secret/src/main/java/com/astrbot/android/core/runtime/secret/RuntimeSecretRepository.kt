package com.astrbot.android.core.runtime.secret

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import java.io.File

/**
 * Compatibility facade for legacy container and tests. New production code should
 * inject [RuntimeSecretStore] directly.
 */
object RuntimeSecretRepository {
    data class RuntimeSecrets(
        val webUiToken: String,
        val webUiJwtSecret: String,
    )

    @Volatile
    private var secretsOverrideForTests: RuntimeSecrets? = null

    fun initialize(context: Context) {
        ensureSecrets(context.applicationContext.filesDir)
    }

    fun ensureSecrets(filesDir: File): com.astrbot.android.core.runtime.secret.RuntimeSecrets {
        secretsOverrideForTests?.let { return it.toRuntimeSecrets() }
        return DefaultRuntimeSecretStore(
            filesDir = filesDir,
            logger = RuntimeLogger {},
        ).ensureSecrets()
    }

    fun getOrCreateWebUiToken(): String {
        return getWebUiTokenOverrideForTests()
            ?: error("RuntimeSecretRepository compat facade is not initialized with a filesDir.")
    }

    fun getOrCreateWebUiJwtSecret(): String {
        return secretsOverrideForTests?.webUiJwtSecret
            ?: error("RuntimeSecretRepository compat facade is not initialized with a filesDir.")
    }

    fun setSecretsOverrideForTests(secrets: RuntimeSecrets?) {
        secretsOverrideForTests = secrets
    }

    internal fun getWebUiTokenOverrideForTests(): String? {
        return secretsOverrideForTests?.webUiToken
    }

    fun loadOrCreateSecretsForTests(
        rootDir: File,
        randomBytes: (Int) -> ByteArray,
    ): RuntimeSecrets {
        return DefaultRuntimeSecretStore.loadOrCreateSecretsForTests(rootDir, randomBytes).toCompatSecrets()
    }

    private fun RuntimeSecrets.toRuntimeSecrets(): com.astrbot.android.core.runtime.secret.RuntimeSecrets {
        return com.astrbot.android.core.runtime.secret.RuntimeSecrets(
            webUiToken = webUiToken,
            webUiJwtSecret = webUiJwtSecret,
        )
    }

    private fun com.astrbot.android.core.runtime.secret.RuntimeSecrets.toCompatSecrets(): RuntimeSecrets {
        return RuntimeSecrets(
            webUiToken = webUiToken,
            webUiJwtSecret = webUiJwtSecret,
        )
    }
}
