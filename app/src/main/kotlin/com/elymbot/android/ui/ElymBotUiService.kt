package com.elymbot.android.ui

import android.net.Uri
import android.content.Intent
import com.elymbot.core.contract.ScheduledTaskAction
import com.elymbot.core.contract.ScheduledTaskActionResult
import com.elymbot.core.contract.ScheduledTaskSnapshotResult
import com.elymbot.core.contract.ScheduledTaskUiStateResult
import kotlinx.coroutines.flow.Flow

interface ElymBotUiService {
    val personas: Flow<List<PersonaCard>>
    val personaTags: Flow<List<String>>
    val activePersonaId: Flow<String?>
    val mainSession: Flow<ConversationSummary?>
    val models: Flow<ModelConfiguration>
    val appGuide: Flow<AppGuideState>
    val commandNames: Flow<Map<ChatCommand, String>>
    val pluginManagement: Flow<PluginManagementState>
    val appUpdate: Flow<AppUpdateState>

    fun sessions(personaId: String): Flow<List<ConversationSummary>>
    fun conversation(sessionId: String): Flow<Conversation>
    fun checkpoints(sessionId: String): Flow<List<ChatCheckpoint>>
    suspend fun saveCheckpoint(sessionId: String)
    suspend fun restoreCheckpoint(sessionId: String, displayNumber: Int, expectedCheckpointId: String)
    suspend fun setViewedConversation(sessionId: String?)
    suspend fun newSession(personaId: String): String
    suspend fun activatePersona(personaId: String)
    suspend fun savePersona(persona: PersonaCard, makeGlobal: Boolean = false, createMissingTags: Boolean = true): String
    suspend fun createPersonaTag(tag: String)
    suspend fun deletePersonaTag(tag: String)
    suspend fun deletePersona(personaId: String)
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSessions(personaId: String, sessionIds: Set<String>)
    suspend fun send(sessionId: String, text: String, image: Uri?)
    suspend fun stop(sessionId: String)
    suspend fun retry(sessionId: String)
    suspend fun retryDelivery(messageId: String, deliveryId: String)
    suspend fun skipAppGuide()
    suspend fun scheduledTasksSnapshot(
        sessionId: String,
        taskId: String? = null,
    ): ScheduledTaskSnapshotResult
    fun scheduledTaskUiState(
        sessionId: String,
        taskId: String? = null,
    ): Flow<ScheduledTaskUiStateResult>
    fun scheduledTasksAvailable(sessionId: String): Flow<Boolean>
    suspend fun skipScheduledTaskGuide(sessionId: String): ScheduledTaskUiStateResult
    suspend fun requestScheduledTaskAction(
        sessionId: String,
        requestId: String,
        action: ScheduledTaskAction,
    ): ScheduledTaskActionResult
    suspend fun confirmScheduledTaskAction(
        sessionId: String,
        confirmationId: String,
        initiatingExecutionId: String,
        approved: Boolean,
    ): ScheduledTaskActionResult
    suspend fun refreshPluginDirectory()
    suspend fun installPlugin(reference: PluginReleaseReference): PluginManagementActionResult
    suspend fun enablePlugin(pluginId: String): PluginManagementActionResult
    suspend fun disablePlugin(pluginId: String): PluginManagementActionResult
    suspend fun uninstallPlugin(pluginId: String, deleteData: Boolean): PluginManagementActionResult
    suspend fun checkForAppUpdate(forceRefresh: Boolean = false)
    fun canRequestAppUpdateInstall(): Boolean
    fun appUpdatePermissionSettingsIntent(): Intent
    suspend fun prepareAppUpdateInstall(offerId: String): AppUpdateInstallResult
    suspend fun finishAppUpdateInstall(ticketId: String)
    suspend fun saveModelService(service: ModelServiceDraft)
    suspend fun deleteModelService(serviceId: String)
    suspend fun fetchModels(service: ModelServiceDraft): List<String>
    fun probeModel(service: ModelServiceDraft): Flow<ModelProbe>
    suspend fun setModel(role: ModelRole, model: ModelChoice?)
    suspend fun saveCommandNames(names: Map<ChatCommand, String>)
}

data class ImagePosition(val x: Float = 0.5f, val y: Float = 0.5f)

data class PersonaCard(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val prompt: String = "",
    val favorite: Boolean = false,
    val cover: Uri? = null,
    val coverPosition: ImagePosition = ImagePosition(),
    val avatarPosition: ImagePosition = ImagePosition(),
)

data class ConversationSummary(
    val id: String,
    val personaId: String,
    val title: String,
    val preview: String? = null,
    val running: Boolean = false,
    val unread: Boolean = false,
)

data class Conversation(
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val issue: UiIssue? = null,
    val canRetry: Boolean = false,
    val toolProgressActive: Boolean = false,
    val detachedDeliveryReceipts: List<ChatDetachedDeliveryReceipt> = emptyList(),
    val pendingDeliveries: List<ChatPendingDelivery> = emptyList(),
)

data class ChatMessage(
    val id: String,
    val text: String = "",
    val assistant: Boolean = false,
    val author: String? = null,
    val image: Uri? = null,
    val status: MessageStatus? = null,
    val localReceipt: Boolean = false,
    val deliveryTargets: List<ChatDeliveryTarget> = emptyList(),
)

data class ChatDeliveryTarget(
    val id: String,
    val kind: String,
    val channelId: String?,
    val conversationId: String?,
    val accountId: String?,
    val status: ChatDeliveryStatus,
)

data class ChatDetachedDeliveryReceipt(
    val id: String,
    val turnSequence: Long,
    val messageId: String,
    val targetKey: String,
    val channelId: String,
    val conversationId: String,
    val accountId: String?,
    val pluginId: String,
    val status: ChatDeliveryStatus,
    val remoteMessageId: String?,
)

data class ChatPendingDelivery(
    val id: String,
    val messageId: String,
    val targetKey: String,
    val kind: String,
    val channelId: String?,
    val conversationId: String?,
    val accountId: String?,
    val status: ChatDeliveryStatus,
    val availableAtEpochMillis: Long,
)

enum class ChatDeliveryStatus { Pending, Sending, Delivered, Failed, Unknown, Cancelled, Stale }

data class ChatCheckpoint(
    val id: String,
    val displayNumber: Int,
    val current: Boolean,
    val retainedMessageCount: Int,
)

enum class MessageStatus { Waiting, Stopped, Interrupted }

enum class UiIssue {
    MainModelMissing, VisionModelMissing, VisionCapabilityUnknown, Authentication, Connection,
    ServiceResponse, ServiceRejected, ServiceProtocol, TimedOut, Busy, OwnedSessions, InvalidInput,
    NoCheckpointBoundary, Failed,
}

class UiServiceException(val issue: UiIssue) : RuntimeException()

enum class ChatCommand(val defaultName: String) {
    Help("help"), Persona("persona"), Checkpoint("checkpoint"), Reset("reset"), Mark("mark"),
}

enum class ModelRole { Text, Vision }

enum class AppGuideStatus { Unknown, NeedsSetup, Skipped, Ready }

data class AppGuideState(
    val status: AppGuideStatus,
    val structuredToolCalls: ProbeStatus,
)

data class PluginReleaseReference(
    val pluginId: String,
    val publisherId: String,
    val version: String,
)

data class PluginReleaseOption(
    val reference: PluginReleaseReference,
    val compatible: Boolean,
)

enum class PluginTrustConfigurationStatus { NotConfigured, Ready, Invalid }

enum class PluginTrustConfigurationFailure { EmptyKey, KeyTooLarge, InvalidRsaKey, UnsupportedAbi, ReadFailed }

enum class PluginDistributionStatus { NotConfigured, Loading, Ready, DirectoryMissing, NetworkError, InvalidDirectory }

enum class PluginDistributionFailure { Transfer, InvalidMetadata, PrivateStorage, Internal }

enum class PluginRuntimeAvailability { Restoring, Active, Unavailable, Disabled }

data class InstalledPluginSummary(
    val pluginId: String,
    val publisherId: String,
    val version: String,
    val enabled: Boolean,
    val availability: PluginRuntimeAvailability,
    val cleanupPending: Boolean,
)

data class PluginManagementState(
    val trustConfigurationStatus: PluginTrustConfigurationStatus,
    val trustConfigurationFailure: PluginTrustConfigurationFailure? = null,
    val restorationComplete: Boolean = false,
    val directoryStatus: PluginDistributionStatus,
    val directoryFailure: PluginDistributionFailure? = null,
    val usingLastGoodDirectory: Boolean = false,
    val releases: List<PluginReleaseOption> = emptyList(),
    val installed: List<InstalledPluginSummary> = emptyList(),
)

enum class PluginManagementFailure {
    TrustNotConfigured,
    DirectoryUnavailable,
    ReleaseNotVerified,
    IncompatibleRelease,
    PluginNotInstalled,
    RuntimeUnavailable,
    InstallFailed,
    UninstallCleanupPending,
}

sealed interface PluginManagementActionResult {
    data object Completed : PluginManagementActionResult

    data class Rejected(val reason: PluginManagementFailure) : PluginManagementActionResult
}

enum class AppUpdateUiStatus {
    Checking,
    UpToDate,
    UpdateAvailable,
    MetadataMissing,
    NetworkError,
    InvalidRelease,
    IncompatiblePackage,
    IncompatibleInstallation,
}

enum class AppUpdateUiFailure { Network, ReleaseMetadata, ReleaseAssetDigest, InstalledPackage }

data class AppUpdateCandidate(
    val id: String,
    val versionCode: Long,
    val versionName: String,
)

data class AppUpdateState(
    val status: AppUpdateUiStatus = AppUpdateUiStatus.Checking,
    val installedVersionCode: Long? = null,
    val installedVersionName: String? = null,
    val availableUpdate: AppUpdateCandidate? = null,
    val failure: AppUpdateUiFailure? = null,
)

data class AppUpdateInstallTicket(
    val id: String,
    val intent: Intent,
    val versionCode: Long,
    val versionName: String,
)

enum class AppUpdateInstallFailure { OfferExpired, InstallerUnavailable, DownloadFailed }

sealed interface AppUpdateInstallResult {
    data class Ready(val ticket: AppUpdateInstallTicket) : AppUpdateInstallResult

    data object InstallerPermissionRequired : AppUpdateInstallResult

    data class Rejected(val reason: AppUpdateInstallFailure) : AppUpdateInstallResult
}

data class ModelChoice(val serviceId: String, val modelId: String)

data class ModelConfiguration(
    val services: List<ModelServiceItem> = emptyList(),
    val text: ModelChoice? = null,
    val vision: ModelChoice? = null,
)

data class ModelServiceItem(
    val id: String,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val hasKey: Boolean,
    val catalog: List<String> = emptyList(),
)

data class ModelServiceDraft(
    val id: String? = null,
    val provider: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val catalog: List<String> = emptyList(),
) {
    override fun toString(): String = "ModelServiceDraft(id=$id, provider=$provider, modelId=$modelId)"
}

enum class ProbeStatus { Waiting, Supported, Unsupported, Unknown }

enum class ProbeTarget { Availability, StructuredToolCalls, Vision, Streaming }

data class ProbeFailure(val issue: UiIssue, val httpStatusCode: Int? = null)

data class ModelProbe(
    val availability: ProbeStatus = ProbeStatus.Waiting,
    val structuredToolCalls: ProbeStatus = ProbeStatus.Waiting,
    val vision: ProbeStatus = ProbeStatus.Waiting,
    val streaming: ProbeStatus = ProbeStatus.Waiting,
    val issue: UiIssue? = null,
    val failuresByTarget: Map<ProbeTarget, ProbeFailure> = emptyMap(),
)
