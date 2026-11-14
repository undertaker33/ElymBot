package com.elymbot.android.ui

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ElymBotUiService {
    val personas: Flow<List<PersonaCard>>
    val activePersonaId: Flow<String?>
    val mainSession: Flow<ConversationSummary?>
    val models: Flow<ModelConfiguration>
    val commandNames: Flow<Map<ChatCommand, String>>

    fun sessions(personaId: String): Flow<List<ConversationSummary>>
    fun conversation(sessionId: String): Flow<Conversation>
    suspend fun setViewedConversation(sessionId: String?)
    suspend fun newSession(personaId: String): String
    suspend fun activatePersona(personaId: String)
    suspend fun savePersona(persona: PersonaCard): String
    suspend fun deletePersona(personaId: String)
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSessions(personaId: String, sessionIds: Set<String>)
    suspend fun send(sessionId: String, text: String, image: Uri?)
    suspend fun stop(sessionId: String)
    suspend fun retry(sessionId: String)
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
)

data class ChatMessage(
    val id: String,
    val text: String = "",
    val assistant: Boolean = false,
    val author: String? = null,
    val image: Uri? = null,
    val status: MessageStatus? = null,
)

enum class MessageStatus { Waiting, Stopped, Interrupted }

enum class UiIssue {
    MainModelMissing, VisionModelMissing, VisionCapabilityUnknown, Authentication, Connection,
    ServiceResponse, ServiceRejected, ServiceProtocol, TimedOut, Busy, OwnedSessions, InvalidInput, Failed,
}

class UiServiceException(val issue: UiIssue) : RuntimeException()

enum class ChatCommand(val defaultName: String) {
    Help("help"), Persona("persona"), Checkpoint("checkpoint"), Reset("reset"), Mark("mark"),
}

enum class ModelRole { Text, Vision }

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

enum class ProbeTarget { Availability, Vision, Streaming }

data class ProbeFailure(val issue: UiIssue, val httpStatusCode: Int? = null)

data class ModelProbe(
    val availability: ProbeStatus = ProbeStatus.Waiting,
    val vision: ProbeStatus = ProbeStatus.Waiting,
    val streaming: ProbeStatus = ProbeStatus.Waiting,
    val issue: UiIssue? = null,
    val failuresByTarget: Map<ProbeTarget, ProbeFailure> = emptyMap(),
)
