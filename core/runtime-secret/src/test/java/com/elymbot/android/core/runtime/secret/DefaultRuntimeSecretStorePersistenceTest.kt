package com.elymbot.android.core.runtime.secret

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRuntimeSecretStorePersistenceTest {

    @Test
    fun load_or_create_secrets_persists_same_values_across_reads() {
        val rootDir = createTempDirectory("runtime-secrets").toFile()
        val first = DefaultRuntimeSecretStore.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (index + 1).toByte() } },
        )

        val second = DefaultRuntimeSecretStore.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (index + 99).toByte() } },
        )

        assertEquals(first, second)
        assertValidSecret(first.webUiToken)
        assertValidSecret(first.webUiJwtSecret)
    }

    @Test
    fun load_or_create_secrets_writes_shell_consumable_env_file() {
        val rootDir = createTempDirectory("runtime-secrets-env").toFile()

        val secrets = DefaultRuntimeSecretStore.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (size - index).toByte() } },
        )

        val envFile = File(rootDir, "config/runtime-secrets.env")
        val envText = envFile.readText()

        assertTrue(envText.contains("NAPCAT_WEBUI_SECRET_KEY=${secrets.webUiToken}"))
        assertTrue(envText.contains("NAPCAT_WEBUI_JWT_SECRET_KEY=${secrets.webUiJwtSecret}"))
        assertTrue(envText.contains("ELYMBOT_RUNTIME_SECRET_VERSION=1"))
    }

    private fun assertValidSecret(value: String) {
        assertEquals(64, value.length)
        assertTrue(value.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
