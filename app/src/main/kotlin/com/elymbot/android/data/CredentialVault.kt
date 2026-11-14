package com.elymbot.android.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CredentialVault(
    private val dao: CredentialDao,
    private val alias: String = "elymbot.model.credentials.v1",
) {
    suspend fun store(serviceId: String, apiKey: String) {
        val record = withContext(Dispatchers.IO) {
            val plaintext = apiKey.toByteArray(Charsets.UTF_8)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                CredentialEntity(serviceId, cipher.iv, cipher.doFinal(plaintext))
            } finally {
                plaintext.fill(0)
            }
        }
        dao.upsert(record)
    }

    suspend fun resolve(serviceId: String): String? = resolveSnapshot(dao.get(serviceId))

    internal suspend fun resolveSnapshot(record: CredentialEntity?): String? {
        if (record == null) return null
        return withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, record.iv))
            val plaintext = try {
                cipher.doFinal(record.encryptedKey)
            } catch (_: Exception) {
                throw CredentialUnavailableException()
            }
            try {
                plaintext.toString(Charsets.UTF_8)
            } finally {
                plaintext.fill(0)
            }
        }
    }

    suspend fun delete(serviceId: String) = dao.delete(serviceId)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    class CredentialUnavailableException : IllegalStateException("Stored model credential is unavailable")

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
