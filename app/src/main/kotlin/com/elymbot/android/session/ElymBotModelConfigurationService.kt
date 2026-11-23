package com.elymbot.android.session

import androidx.room.withTransaction
import com.elymbot.android.data.CredentialVault
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ModelAssignmentEntity
import com.elymbot.android.data.ModelProbeEntity
import com.elymbot.android.data.ModelServiceEntity
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.ui.ModelChoice
import com.elymbot.android.ui.ModelConfiguration
import com.elymbot.android.ui.ModelProbe
import com.elymbot.android.ui.ModelRole as UiModelRole
import com.elymbot.android.ui.ModelServiceDraft
import com.elymbot.android.ui.ModelServiceItem
import com.elymbot.android.ui.ProbeFailure
import com.elymbot.android.ui.ProbeStatus
import com.elymbot.android.ui.ProbeTarget
import com.elymbot.android.ui.UiIssue
import com.elymbot.android.ui.UiServiceException
import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelProbeFailureKind
import com.elymbot.core.contract.ModelProbeTarget
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequestException
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val CATALOG_SEPARATOR = "\u001f"

internal class ElymBotModelConfigurationService(
    private val repository: ElymBotDataRepository,
    private val connectionProvider: RoomModelServiceConnectionProvider,
    private val modelGateway: ModelGateway,
) {
    val models: Flow<ModelConfiguration> = combine(
        readyFlow { repository.database.modelConfigurationDao().observeServices() },
        readyFlow { repository.database.modelConfigurationDao().observeAssignments() },
        readyFlow { repository.database.credentialDao().observeServiceIds() },
    ) { services, assignments, credentialIds ->
        val items = services.map { service ->
            ModelServiceItem(
                id = service.id,
                provider = service.provider,
                name = service.name,
                baseUrl = service.baseUrl,
                modelId = service.modelId,
                hasKey = service.id in credentialIds,
                catalog = decodeCatalog(service.catalogValue),
            )
        }
        val choices = assignments.associate { it.role to ModelChoice(it.serviceId, it.modelId) }
        ModelConfiguration(
            services = items,
            text = choices[UiModelRole.Text.name],
            vision = choices[UiModelRole.Vision.name],
        )
    }

    suspend fun saveModelService(service: ModelServiceDraft) {
        repository.initialize()
        val baseUrl = service.baseUrl.trim()
        val modelId = service.modelId.trim()
        val providerName = service.provider.trim()
        val name = service.name.trim().ifEmpty { providerName }
        if (baseUrl.isEmpty() || modelId.isEmpty() || providerName.isEmpty() || name.isEmpty()) fail(UiIssue.InvalidInput)
        val id = service.id ?: UUID.randomUUID().toString()
        val newRecord = ModelServiceEntity(
            id = id,
            provider = providerName,
            name = name,
            baseUrl = baseUrl,
            modelId = modelId,
            catalogValue = encodeCatalog(service.catalog),
        )
        repository.database.withTransaction {
            val serviceDao = repository.database.modelConfigurationDao()
            val existing = serviceDao.getService(id)
            val baseUrlChanged = existing != null && existing.baseUrl != baseUrl
            val apiKeyChanged = if (existing != null && service.apiKey.isNotBlank()) {
                val storedKey = try {
                    repository.credentials.resolve(id)
                } catch (_: CredentialVault.CredentialUnavailableException) {
                    null
                }
                storedKey != service.apiKey
            } else {
                false
            }
            serviceDao.upsertService(newRecord)
            if (service.apiKey.isNotBlank() && (existing == null || baseUrlChanged || apiKeyChanged)) {
                repository.credentials.store(id, service.apiKey)
            } else if (baseUrlChanged) {
                repository.credentials.delete(id)
            }
            if (baseUrlChanged || apiKeyChanged) {
                serviceDao.deleteProbes(id)
            }
        }
    }

    suspend fun deleteModelService(serviceId: String) {
        repository.initialize()
        repository.database.withTransaction {
            repository.database.modelConfigurationDao().deleteAssignmentsForService(serviceId)
            repository.database.modelConfigurationDao().deleteService(serviceId)
        }
    }

    suspend fun fetchModels(service: ModelServiceDraft): List<String> = withContext(Dispatchers.IO) {
        connectionProvider.withDraftConnection(service) { temporaryId ->
            modelGateway.listModels(temporaryId).distinct()
        }
    }

    fun probeModel(service: ModelServiceDraft): Flow<ModelProbe> = flow {
        repository.initialize()
        emit(ModelProbe())
        val result = try {
            connectionProvider.withDraftConnection(service) { temporaryId ->
                val report = modelGateway.probe(ModelReference(temporaryId, service.modelId.trim()))
                saveProbeIfPersisted(service, temporaryId, report)
                report.toUiProbe()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            ModelProbe(issue = failure.toUiIssue())
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    suspend fun setModel(role: UiModelRole, model: ModelChoice?) {
        repository.initialize()
        if (model == null) {
            repository.database.modelConfigurationDao().deleteAssignment(role.name)
            return
        }
        val service = repository.database.modelConfigurationDao().getService(model.serviceId)
        if (model.modelId.isBlank() || service == null ||
            (service.modelId != model.modelId && model.modelId !in decodeCatalog(service.catalogValue))
        ) {
            fail(UiIssue.InvalidInput)
        }
        repository.database.modelConfigurationDao().upsertAssignment(
            ModelAssignmentEntity(role.name, model.serviceId, model.modelId),
        )
    }

    private suspend fun saveProbeIfPersisted(
        draft: ModelServiceDraft,
        temporaryServiceId: String,
        report: ModelCapabilityReport,
    ) {
        val serviceId = draft.id ?: return
        connectionProvider.withSavedIdentity(temporaryServiceId, serviceId) { service ->
            if (service.baseUrl != draft.baseUrl.trim()) return@withSavedIdentity
            val modelId = draft.modelId.trim()
            val dao = repository.database.modelConfigurationDao()
            dao.deleteProbesForModel(serviceId, modelId, service.baseUrl)
            ModelProbeTarget.entries.forEach { target ->
                val status = when (target) {
                    ModelProbeTarget.AVAILABILITY -> report.availability
                    ModelProbeTarget.VISION -> report.vision
                    ModelProbeTarget.NATIVE_STREAMING -> report.nativeStreaming
                    ModelProbeTarget.STRUCTURED_TOOL_CALLS -> report.structuredToolCalls
                }
                val failure = report.failuresByTarget[target]
                dao.upsertProbe(
                    ModelProbeEntity(
                        serviceId = serviceId,
                        modelId = modelId,
                        baseUrl = service.baseUrl,
                        target = target.name,
                        status = status.name,
                        failureKind = failure?.kind?.name,
                        httpStatusCode = failure?.httpStatusCode,
                    ),
                )
            }
        }
    }

    private fun ModelCapabilityReport.toUiProbe(): ModelProbe {
        val failures = failuresByTarget.mapNotNull { (target, failure) ->
            val uiTarget = when (target) {
                ModelProbeTarget.AVAILABILITY -> ProbeTarget.Availability
                ModelProbeTarget.STRUCTURED_TOOL_CALLS -> ProbeTarget.StructuredToolCalls
                ModelProbeTarget.VISION -> ProbeTarget.Vision
                ModelProbeTarget.NATIVE_STREAMING -> ProbeTarget.Streaming
            }
            uiTarget to ProbeFailure(failure.kind.toUiIssue(), failure.httpStatusCode)
        }.toMap()
        return ModelProbe(
            availability = availability.toUiStatus(),
            structuredToolCalls = structuredToolCalls.toUiStatus(),
            vision = vision.toUiStatus(),
            streaming = nativeStreaming.toUiStatus(),
            issue = failures.values.firstOrNull()?.issue,
            failuresByTarget = failures,
        )
    }

    private fun CapabilityStatus.toUiStatus(): ProbeStatus = when (this) {
        CapabilityStatus.SUPPORTED -> ProbeStatus.Supported
        CapabilityStatus.UNSUPPORTED -> ProbeStatus.Unsupported
        CapabilityStatus.UNKNOWN -> ProbeStatus.Unknown
    }

    private fun ModelProbeFailureKind.toUiIssue(): UiIssue = when (this) {
        ModelProbeFailureKind.NETWORK -> UiIssue.Connection
        ModelProbeFailureKind.AUTHENTICATION -> UiIssue.Authentication
        ModelProbeFailureKind.HTTP -> UiIssue.ServiceResponse
        ModelProbeFailureKind.API_REJECTED -> UiIssue.ServiceRejected
        ModelProbeFailureKind.PROTOCOL -> UiIssue.ServiceProtocol
        ModelProbeFailureKind.TIMED_OUT -> UiIssue.TimedOut
    }

    private fun <T> readyFlow(source: () -> Flow<T>): Flow<T> = flow {
        repository.initialize()
        emitAll(source())
    }

    private fun decodeCatalog(value: String): List<String> = value.split(CATALOG_SEPARATOR).filter(String::isNotEmpty)

    private fun encodeCatalog(items: List<String>): String = items.distinct().joinToString(CATALOG_SEPARATOR)

    private fun fail(issue: UiIssue): Nothing = throw UiServiceException(issue)
}

internal fun Throwable.toUiIssue(): UiIssue = when (this) {
    is UiServiceException -> issue
    is CredentialVault.CredentialUnavailableException -> UiIssue.Authentication
    is ModelRequestException.Network -> UiIssue.Connection
    is ModelRequestException.HttpStatus -> if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || statusCode == HttpURLConnection.HTTP_FORBIDDEN) UiIssue.Authentication else UiIssue.ServiceResponse
    is ModelRequestException.ApiRejected -> if (
        httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED || httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN
    ) UiIssue.Authentication else UiIssue.ServiceRejected
    is ModelRequestException.Protocol -> UiIssue.ServiceProtocol
    is ModelRequestException.StreamInterrupted -> UiIssue.Connection
    is ModelRequestException.ContextLimit -> UiIssue.InvalidInput
    is ModelRequestException.TimedOut -> UiIssue.TimedOut
    is FileNotFoundException -> UiIssue.InvalidInput
    else -> UiIssue.Failed
}
