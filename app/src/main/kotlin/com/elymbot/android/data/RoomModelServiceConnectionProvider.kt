package com.elymbot.android.data

import androidx.room.withTransaction
import com.elymbot.android.ui.ModelServiceDraft
import com.elymbot.core.model.ModelServiceConnection
import com.elymbot.core.model.ModelServiceConnectionProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RoomModelServiceConnectionProvider(
    private val repository: ElymBotDataRepository,
) : ModelServiceConnectionProvider {
    private val temporaryConnections = ConcurrentHashMap<String, TemporaryConnection>()

    suspend fun snapshot(
        serviceId: String,
        expectedBaseUrl: String,
        expectedCredential: CredentialEntity?,
    ): ModelConnectionLease? {
        repository.initialize()
        val snapshot = readSavedSnapshot(serviceId) ?: return null
        val service = snapshot.service
        if (service.baseUrl != expectedBaseUrl || !snapshot.credential.sameEncryptedValueAs(expectedCredential)) return null
        val connection = TemporaryConnection(
            persistedServiceId = serviceId,
            baseUrl = service.baseUrl,
            apiKey = repository.credentials.resolveSnapshot(snapshot.credential),
            savedCredential = snapshot.credential,
            canPersistSavedIdentity = true,
        )
        return lease(connection)
    }

    override suspend fun resolve(serviceId: String): ModelServiceConnection? {
        repository.initialize()
        temporaryConnections[serviceId]?.let { return it.toConnection() }
        val snapshot = readSavedSnapshot(serviceId) ?: return null
        return ModelServiceConnection(snapshot.service.baseUrl, repository.credentials.resolveSnapshot(snapshot.credential))
    }

    suspend fun <T> withDraftConnection(
        draft: ModelServiceDraft,
        block: suspend (temporaryServiceId: String) -> T,
    ): T {
        repository.initialize()
        val temporaryServiceId = "draft-${UUID.randomUUID()}"
        val saved = draft.id?.let { readSavedSnapshot(it) }
        val baseUrl = draft.baseUrl.trim()
        val draftKey = draft.apiKey.takeIf(String::isNotBlank)
        val savedKey = saved?.takeIf { it.service.baseUrl == baseUrl }?.let { snapshot ->
            if (draftKey == null) {
                repository.credentials.resolveSnapshot(snapshot.credential)
            } else {
                try {
                    repository.credentials.resolveSnapshot(snapshot.credential)
                } catch (_: CredentialVault.CredentialUnavailableException) {
                    null
                }
            }
        }
        val effectiveKey = draftKey ?: savedKey
        val matchesSavedIdentity = saved != null && saved.service.baseUrl == baseUrl && effectiveKey == savedKey
        val connection = TemporaryConnection(
            persistedServiceId = draft.id,
            baseUrl = baseUrl,
            apiKey = effectiveKey,
            savedCredential = saved?.credential.takeIf { matchesSavedIdentity },
            canPersistSavedIdentity = matchesSavedIdentity,
        )
        val lease = register(connection, temporaryServiceId)
        try {
            return block(lease.serviceId)
        } finally {
            lease.close()
        }
    }

    suspend fun <T> withSavedIdentity(
        temporaryServiceId: String,
        persistedServiceId: String,
        block: suspend (ModelServiceEntity) -> T,
    ): T? {
        val connection = temporaryConnections[temporaryServiceId] ?: return null
        if (connection.persistedServiceId != persistedServiceId || !connection.canPersistSavedIdentity) return null
        repository.initialize()
        return repository.database.withTransaction {
            val service = repository.database.modelConfigurationDao().getService(persistedServiceId)
                ?: return@withTransaction null
            if (service.baseUrl != connection.baseUrl) return@withTransaction null
            val savedCredential = repository.database.credentialDao().get(persistedServiceId)
            if (!savedCredential.sameEncryptedValueAs(connection.savedCredential)) return@withTransaction null
            block(service)
        }
    }

    suspend fun <T> withServiceSnapshot(
        serviceId: String,
        block: suspend (snapshotServiceId: String) -> T,
    ): T? {
        repository.initialize()
        val snapshot = readSavedSnapshot(serviceId) ?: return null
        val connection = TemporaryConnection(
            persistedServiceId = serviceId,
            baseUrl = snapshot.service.baseUrl,
            apiKey = repository.credentials.resolveSnapshot(snapshot.credential),
            savedCredential = snapshot.credential,
            canPersistSavedIdentity = true,
        )
        val lease = lease(connection)
        try {
            return block(lease.serviceId)
        } finally {
            lease.close()
        }
    }

    private fun lease(connection: TemporaryConnection): ModelConnectionLease =
        register(connection, "snapshot-${UUID.randomUUID()}")

    private suspend fun readSavedSnapshot(serviceId: String): SavedConnectionSnapshot? =
        repository.database.withTransaction {
            val service = repository.database.modelConfigurationDao().getService(serviceId)
                ?: return@withTransaction null
            SavedConnectionSnapshot(service, repository.database.credentialDao().get(serviceId))
        }

    private fun register(connection: TemporaryConnection, serviceId: String): ModelConnectionLease {
        temporaryConnections[serviceId] = connection
        return ModelConnectionLease(serviceId) {
            temporaryConnections.remove(serviceId)
            connection.clear()
        }
    }

    private data class TemporaryConnection(
        val persistedServiceId: String?,
        val baseUrl: String,
        private var apiKey: String?,
        val savedCredential: CredentialEntity?,
        val canPersistSavedIdentity: Boolean,
    ) {
        fun toConnection() = ModelServiceConnection(baseUrl, apiKey)

        fun clear() {
            apiKey = null
        }

        override fun toString(): String = "TemporaryConnection(baseUrl=$baseUrl)"
    }

    private data class SavedConnectionSnapshot(
        val service: ModelServiceEntity,
        val credential: CredentialEntity?,
    )
}

private fun CredentialEntity?.sameEncryptedValueAs(other: CredentialEntity?): Boolean = when {
    this == null || other == null -> this == null && other == null
    serviceId != other.serviceId -> false
    else -> iv.contentEquals(other.iv) && encryptedKey.contentEquals(other.encryptedKey)
}

class ModelConnectionLease internal constructor(
    val serviceId: String,
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() = release()
}
