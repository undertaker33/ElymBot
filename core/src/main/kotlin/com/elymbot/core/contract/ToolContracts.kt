package com.elymbot.core.contract

enum class ToolScope {
    MAIN_SESSION,
    PERSONA_SESSION,
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
    val scopes: Set<ToolScope>,
)

/** Installation identity assigned by the trusted host after verifying a plugin package. */
data class ToolInstallationIdentity(
    val pluginId: String,
    val instanceId: String,
) {
    init {
        require(pluginId.isNotBlank())
        require(instanceId.isNotBlank())
    }
}

/** The verified owner of a registered source; plugin code does not provide this value. */
sealed interface ToolRegistrationOwner {
    data object Host : ToolRegistrationOwner

    data class Plugin(val installation: ToolInstallationIdentity) : ToolRegistrationOwner
}

/** Host-created registration that binds a tool implementation to its trusted owner. */
data class ToolRegistration(
    val source: ToolSource,
    val owner: ToolRegistrationOwner = ToolRegistrationOwner.Host,
)

/** Identity and request metadata available to the host authorization policy. */
data class ToolAccessContext(
    val scope: ToolScope,
    val sessionId: String? = null,
    val executionId: String? = null,
    val attemptId: String? = null,
    val turnSequence: Long? = null,
    val historyUpperBoundExclusive: Long? = null,
    val inputMessage: AgentMessage? = null,
    val installationIdentity: ToolInstallationIdentity? = null,
)

enum class ToolAccessDenial {
    HOST_AUTHORITY_REQUIRED,
    INSTALLATION_INACTIVE,
    ROUTE_NOT_AUTHORIZED,
    SESSION_NOT_AUTHORIZED,
}

sealed interface ToolAccessDecision {
    data object Allowed : ToolAccessDecision

    data class Rejected(val reason: ToolAccessDenial) : ToolAccessDecision
}

/**
 * Host policy controls both model-visible tool definitions and execution-time
 * access. Authorization is rechecked immediately before invoking a source.
 */
interface ToolAccessPolicy {
    fun canAdvertise(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): Boolean

    suspend fun authorize(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): ToolAccessDecision
}

/** Compatibility policy for existing host-owned tools; plugin registrations fail closed. */
object BuiltInOnlyToolAccessPolicy : ToolAccessPolicy {
    override fun canAdvertise(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): Boolean = owner == ToolRegistrationOwner.Host

    override suspend fun authorize(
        definition: ToolDefinition,
        owner: ToolRegistrationOwner,
        context: ToolAccessContext,
    ): ToolAccessDecision = if (owner == ToolRegistrationOwner.Host) {
        ToolAccessDecision.Allowed
    } else {
        ToolAccessDecision.Rejected(ToolAccessDenial.HOST_AUTHORITY_REQUIRED)
    }
}

data class ToolExecutionContext(
    val sessionId: String,
    val turnSequence: Long,
    val attemptId: String,
    val historyUpperBoundExclusive: Long,
    val scope: ToolScope,
    val executionId: String = "",
    val inputMessage: AgentMessage? = null,
    /** Set by ToolRegistry from the trusted registration, not from model arguments. */
    val installationIdentity: ToolInstallationIdentity? = null,
    /** Host-assigned tool batch position for stable per-step submissions. */
    val toolStepOrdinal: Int = 0,
    /** Host-assigned call position within the tool batch. */
    val toolCallOrdinal: Int = 0,
)

enum class ToolResultStatus {
    SUCCESS,
    REJECTED,
    FAILED,
}

data class ToolExecutionResult(
    val callId: String,
    val status: ToolResultStatus,
    val content: String,
)

data class ToolProgress(val text: String)

/** One stable tool source. Execution is suspendable and observes caller cancellation. */
interface ToolSource {
    val definition: ToolDefinition

    suspend fun execute(
        call: ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (ToolProgress) -> Unit,
    ): ToolExecutionResult
}
