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

    suspend fun snapshot(serviceId: String, expectedBaseUrl: String): ModelConnectionLease? {
        repository.initialize()
        val snapshot = readSavedSnapshot(serviceId) ?: return null
        val service = snapshot.service
        if (service.baseUrl != expectedBaseUrl) return null
        val connection = TemporaryConnection(service.baseUrl, repository.credentials.resolveSnapshot(snapshot.credential))
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
        val inheritedKey = if (draft.apiKey.isBlank()) {
            saved?.takeIf { it.service.baseUrl == draft.baseUrl }
                ?.let { repository.credentials.resolveSnapshot(it.credential) }
        } else null
        val connection = TemporaryConnection(
            baseUrl = draft.baseUrl,
            apiKey = draft.apiKey.takeIf(String::isNotBlank) ?: inheritedKey,
        )
        val lease = register(connection, temporaryServiceId)
        try {
            return block(lease.serviceId)
        } finally {
            lease.close()
        }
    }

    suspend fun <T> withServiceSnapshot(
        serviceId: String,
        block: suspend (snapshotServiceId: String) -> T,
    ): T? {
        repository.initialize()
        val snapshot = readSavedSnapshot(serviceId) ?: return null
        val connection = TemporaryConnection(
            snapshot.service.baseUrl,
            repository.credentials.resolveSnapshot(snapshot.credential),
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
        val baseUrl: String,
        private var apiKey: String?,
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

class ModelConnectionLease internal constructor(
    val serviceId: String,
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() = release()
}
