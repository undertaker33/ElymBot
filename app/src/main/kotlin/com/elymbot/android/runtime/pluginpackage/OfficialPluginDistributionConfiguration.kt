package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

internal sealed interface OfficialPluginDistributionConfigurationLoad {
    data object NotConfigured : OfficialPluginDistributionConfigurationLoad

    data class Ready(val configuration: PluginDistributionConfiguration) : OfficialPluginDistributionConfigurationLoad

    data class Invalid(val failure: Failure) : OfficialPluginDistributionConfigurationLoad

    enum class Failure {
        EMPTY_KEY,
        KEY_TOO_LARGE,
        INVALID_RSA_KEY,
        UNSUPPORTED_ABI,
        READ_FAILED,
    }
}

/** Loads release-publisher trust from a packaged public key; no private signing material belongs in the App. */
internal object OfficialPluginDistributionConfiguration {
    const val TRUSTED_DIRECTORY_ROOT_ASSET = "plugin-distribution/directory-root.der"
    const val CURRENT_HOST_API = "elymbot-plugin-api-v1"

    fun load(context: Context): OfficialPluginDistributionConfigurationLoad {
        val keyBytes = try {
            context.applicationContext.assets.open(TRUSTED_DIRECTORY_ROOT_ASSET).use(::readBoundedKey)
        } catch (_: FileNotFoundException) {
            return OfficialPluginDistributionConfigurationLoad.NotConfigured
        } catch (_: IllegalArgumentException) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.EMPTY_KEY,
            )
        } catch (_: IOException) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.READ_FAILED,
            )
        }

        if (keyBytes.isEmpty()) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.EMPTY_KEY,
            )
        }
        if (keyBytes.size > MAX_KEY_BYTES) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.KEY_TOO_LARGE,
            )
        }
        val publicKey = try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        } catch (_: Exception) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.INVALID_RSA_KEY,
            )
        }
        if (publicKey !is RSAPublicKey ||
            publicKey.modulus.bitLength() !in PluginDistributionConfiguration.MIN_RSA_BITS..PluginDistributionConfiguration.MAX_RSA_BITS
        ) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.INVALID_RSA_KEY,
            )
        }
        val abis = Build.SUPPORTED_ABIS.toSet()
        if (abis.isEmpty()) {
            return OfficialPluginDistributionConfigurationLoad.Invalid(
                OfficialPluginDistributionConfigurationLoad.Failure.UNSUPPORTED_ABI,
            )
        }
        return OfficialPluginDistributionConfigurationLoad.Ready(
            PluginDistributionConfiguration(
                trustedDirectoryKey = publicKey,
                hostApi = CURRENT_HOST_API,
                supportedAbis = abis,
            ),
        )
    }

    private fun readBoundedKey(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_KEY_BYTES) {
                return ByteArray(MAX_KEY_BYTES + 1)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private const val MAX_KEY_BYTES = 1_024
    private const val COPY_BUFFER_BYTES = 256
}
