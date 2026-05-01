package com.astrbot.android.core.runtime.secret

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger

/**
 * Compatibility facade for legacy call sites that cannot yet receive Hilt injection.
 * New production code should inject [RuntimeSecretStore] directly.
 */
object AppSecretStore {
    @Volatile
    private var compatStore: RuntimeSecretStore? = null

    fun initialize(context: Context) {
        val store = DefaultRuntimeSecretStore(
            filesDir = context.applicationContext.filesDir,
            logger = RuntimeLogger {},
        )
        store.ensureSecrets()
        compatStore = store
    }

    fun getOrCreateWebUiToken(): String {
        RuntimeSecretRepository.getWebUiTokenOverrideForTests()?.let { return it }
        return requireNotNull(compatStore) {
            "AppSecretStore compat facade is not initialized."
        }.getOrCreateWebUiToken()
    }
}
