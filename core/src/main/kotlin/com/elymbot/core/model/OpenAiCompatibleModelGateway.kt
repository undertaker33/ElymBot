package com.elymbot.core.model

import com.elymbot.core.contract.CapabilityStatus
import com.elymbot.core.contract.ModelCapabilityReport
import com.elymbot.core.contract.ModelContentPart
import com.elymbot.core.contract.ModelFinishReason
import com.elymbot.core.contract.ModelGateway
import com.elymbot.core.contract.ModelMessage
import com.elymbot.core.contract.ModelProbeFailure
import com.elymbot.core.contract.ModelProbeFailureKind
import com.elymbot.core.contract.ModelProbeTarget
import com.elymbot.core.contract.ModelReference
import com.elymbot.core.contract.ModelRequest
import com.elymbot.core.contract.ModelRequestException
import com.elymbot.core.contract.ModelRole
import com.elymbot.core.contract.ModelStreamEvent
import com.elymbot.core.contract.ModelToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A service connection whose credential is available only to the authentication path. */
class ModelServiceConnection(
    val baseUrl: String,
    apiKey: String?,
) {
    private val credential = apiKey?.trim()?.takeIf(String::isNotEmpty)

    internal fun applyAuthentication(builder: Request.Builder) {
        credential?.let { builder.header("Authorization", "Bearer " + it) }
    }

    internal fun sanitize(message: String): String =
        credential?.let { message.replace(it, "[redacted]") } ?: message

    override fun toString(): String = "ModelServiceConnection(baseUrl=" + baseUrl + ")"
}
/** Resolves a configured service and its credential for the model adapter. */
fun interface ModelServiceConnectionProvider {
    suspend fun resolve(serviceId: String): ModelServiceConnection?
}

/** OpenAI Chat Completions compatible transport used by the Core model port. */
class OpenAiCompatibleModelGateway(
    private val connectionProvider: ModelServiceConnectionProvider,
    client: OkHttpClient = OkHttpClient(),
    private val probeTimeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
) : ModelGateway {
    private val streamClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val probeClient = streamClient.newBuilder()
        .callTimeout(probeTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    init {
        require(probeTimeoutMillis > 0) { "Probe timeout must be positive." }
    }

    override suspend fun listModels(serviceId: String): List<String> {
        val connection = resolveConnection(serviceId)
        val requestBuilder = Request.Builder()
            .url(endpoint(connection, "/models"))
            .method("GET", null)
        connection.applyAuthentication(requestBuilder)
        val request = requestBuilder.build()

        return execute(probeClient, request) { response ->
            if (!response.isSuccessful) throw OpenAiCompatibleProtocol.responseFailure(response, connection)
            OpenAiCompatibleProtocol.parseModels(response.body.string())
        }
    }

    override suspend fun probe(model: ModelReference): ModelCapabilityReport {
        validateModelReference(model)
        val failures = LinkedHashMap<ModelProbeTarget, ModelProbeFailure>()
        val connection = try {
            resolveConnection(model.serviceId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ModelRequestException) {
            failures[ModelProbeTarget.AVAILABILITY] = failure.toProbeFailure()
            return ModelCapabilityReport(
                model = model,
                availability = CapabilityStatus.UNKNOWN,
                vision = CapabilityStatus.UNKNOWN,
                nativeStreaming = CapabilityStatus.UNKNOWN,
                structuredToolCalls = CapabilityStatus.UNKNOWN,
                failuresByTarget = failures,
            )
        }

        val availability = runProbe(failures, ModelProbeTarget.AVAILABILITY) {
            val result = requestCompletion(
                connection = connection,
                model = model,
                messages = listOf(userText("Reply with OK.")),
            )
            if (result.text.isNotBlank()) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN
        }
        if (availability == CapabilityStatus.UNSUPPORTED || failures[ModelProbeTarget.AVAILABILITY]?.let(::shouldSkipFollowupProbes) == true) {
            return ModelCapabilityReport(
                model = model,
                availability = availability,
                vision = CapabilityStatus.UNKNOWN,
                nativeStreaming = CapabilityStatus.UNKNOWN,
                structuredToolCalls = CapabilityStatus.UNKNOWN,
                failuresByTarget = failures,
            )
        }

        val vision = runProbe(failures, ModelProbeTarget.VISION) {
            probeVision(connection, model)
        }
        val nativeStreaming = runProbe(failures, ModelProbeTarget.NATIVE_STREAMING) {
            probeStreaming(model)
        }
        val structuredTools = runProbe(failures, ModelProbeTarget.STRUCTURED_TOOL_CALLS) {
            probeToolCalls(connection, model)
        }
        return ModelCapabilityReport(
            model = model,
            availability = availability,
            vision = vision,
            nativeStreaming = nativeStreaming,
            structuredToolCalls = structuredTools,
            failuresByTarget = failures,
        )
    }

    override fun stream(request: ModelRequest): Flow<ModelStreamEvent> = callbackFlow {
        val connection = try {
            validateModelReference(request.model)
            resolveConnection(request.model.serviceId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ModelRequestException) {
            close(failure)
            return@callbackFlow
        }
        val body = try {
            OpenAiCompatibleProtocol.buildRequest(request, request.useNativeStreaming)
        } catch (failure: ModelRequestException) {
            close(failure)
            return@callbackFlow
        }
        val httpRequest = postRequest(connection, body, request.useNativeStreaming)
        val call = streamClient.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, failure: IOException) {
                close(failure.toModelFailure(duringStream = false))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw OpenAiCompatibleProtocol.responseFailure(it, connection)
                        if (request.useNativeStreaming) {
                            OpenAiCompatibleProtocol.streamResponse(it, connection) { event -> sendEvent(event) }
                        } else {
                            val completion = OpenAiCompatibleProtocol.parseCompletion(it.body.string(), connection)
                            if (completion.text.isNotEmpty()) sendEvent(ModelStreamEvent.TextDelta(completion.text))
                            sendEvent(ModelStreamEvent.Completed(completion.finishReason, completion.toolCalls))
                        }
                    }
                    close()
                } catch (cancellation: CancellationException) {
                    close(cancellation)
                } catch (failure: Exception) {
                    close(failure.toModelFailure(duringStream = request.useNativeStreaming))
                }
            }

            private fun sendEvent(event: ModelStreamEvent) {
                if (trySendBlocking(event).isFailure) {
                    throw CancellationException("The model stream collector has closed.")
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private suspend fun probeVision(
        connection: ModelServiceConnection,
        model: ModelReference,
    ): CapabilityStatus {
        val result = requestCompletion(
            connection = connection,
            model = model,
            messages = listOf(
                ModelMessage(
                    role = ModelRole.USER,
                    content = listOf(
                        ModelContentPart.Text(VISION_PROMPT),
                        ModelContentPart.Image(OpenAiCompatibleProtocol.visionProbeImageBytes(), "image/png"),
                    ),
                ),
            ),
        )
        val colors = COLOR_WORD_REGEX.findAll(result.text.lowercase(Locale.ROOT))
            .map { it.value }
            .toList()
        return if (colors == VISION_EXPECTED_COLORS) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN
    }

    private suspend fun probeStreaming(model: ModelReference): CapabilityStatus {
        val result = withTimeoutOrNull(probeTimeoutMillis) {
            val responseText = StringBuilder()
            var completed: ModelFinishReason? = null
            stream(
                ModelRequest(
                    model = model,
                    messages = listOf(userText("Reply with OK.")),
                    useNativeStreaming = true,
                ),
            ).collect { event ->
                when (event) {
                    is ModelStreamEvent.TextDelta -> responseText.append(event.text)
                    is ModelStreamEvent.Completed -> completed = event.finishReason
                }
            }
            responseText.isNotBlank() && completed != null
        }
        if (result == null) throw ModelRequestException.TimedOut("The native streaming probe timed out.")
        return if (result) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN
    }

    private suspend fun probeToolCalls(
        connection: ModelServiceConnection,
        model: ModelReference,
    ): CapabilityStatus {
        val tool = ModelToolDefinition(
            name = PROBE_TOOL_NAME,
            description = "Call this tool once to confirm structured tool support.",
            parametersJsonSchema = PROBE_TOOL_SCHEMA,
        )
        val result = requestCompletion(
            connection = connection,
            model = model,
            messages = listOf(userText("Call capability_probe once with probe set to elymbot. Do not answer in text.")),
            tools = listOf(tool),
            forcedToolChoice = "required",
        )
        val call = result.toolCalls.singleOrNull() ?: return CapabilityStatus.UNKNOWN
        if (result.finishReason != ModelFinishReason.TOOL_CALLS || call.name != PROBE_TOOL_NAME) {
            return CapabilityStatus.UNKNOWN
        }
        val arguments = OpenAiCompatibleProtocol.parseObject(call.argumentsJson, "tool probe arguments")
        return if (arguments.stringValue("probe") == PROBE_TOOL_VALUE) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN
    }

    private suspend fun requestCompletion(
        connection: ModelServiceConnection,
        model: ModelReference,
        messages: List<ModelMessage>,
        tools: List<ModelToolDefinition> = emptyList(),
        forcedToolChoice: String? = null,
    ): ParsedCompletion {
        val request = ModelRequest(
            model = model,
            messages = messages,
            tools = tools,
            useNativeStreaming = false,
        )
        val requestBody = OpenAiCompatibleProtocol.buildRequest(
            request = request,
            streaming = false,
            forcedToolChoice = forcedToolChoice,
        )
        val httpRequest = postRequest(connection, requestBody, streaming = false)
        return execute(probeClient, httpRequest) { response ->
            if (!response.isSuccessful) throw OpenAiCompatibleProtocol.responseFailure(response, connection)
            OpenAiCompatibleProtocol.parseCompletion(response.body.string(), connection)
        }
    }

    private suspend fun runProbe(
        failures: MutableMap<ModelProbeTarget, ModelProbeFailure>,
        target: ModelProbeTarget,
        block: suspend () -> CapabilityStatus,
    ): CapabilityStatus {
        return try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ModelRequestException) {
            failures[target] = failure.toProbeFailure()
            if (failure.isExplicitUnsupported(target)) CapabilityStatus.UNSUPPORTED else CapabilityStatus.UNKNOWN
        } catch (failure: Exception) {
            failures[target] = ModelRequestException.Protocol("The capability probe could not be completed.", failure)
                .toProbeFailure()
            CapabilityStatus.UNKNOWN
        }
    }

    private suspend fun <T> execute(
        client: OkHttpClient,
        request: Request,
        parseResponse: (Response) -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, failure: IOException) {
                if (continuation.isActive) continuation.resumeWithException(failure.toModelFailure(duringStream = false))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use(parseResponse)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure.toModelFailure(duringStream = false))
                    return
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private suspend fun resolveConnection(serviceId: String): ModelServiceConnection {
        if (serviceId.isBlank()) throw ModelRequestException.Protocol("The model service ID is empty.")
        return try {
            connectionProvider.resolve(serviceId)
                ?: throw ModelRequestException.ApiRejected("service_not_configured", "The model service is not configured.")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ModelRequestException) {
            throw failure
        } catch (failure: Exception) {
            throw ModelRequestException.Protocol("The model service configuration could not be loaded.", failure)
        }
    }

    private fun postRequest(connection: ModelServiceConnection, body: String, streaming: Boolean): Request {
        val builder = Request.Builder()
            .url(endpoint(connection, "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Accept", if (streaming) "text/event-stream, application/json" else "application/json")
            .post(body.toRequestBody(OpenAiCompatibleProtocol.JSON_MEDIA_TYPE))
        connection.applyAuthentication(builder)
        return builder.build()
    }

    private fun endpoint(connection: ModelServiceConnection, path: String): HttpUrl {
        val url = (connection.baseUrl.trim().trimEnd('/') + path).toHttpUrlOrNull()
            ?: throw ModelRequestException.Protocol("The model service URL is invalid.")
        if (url.scheme != "http" && url.scheme != "https") {
            throw ModelRequestException.Protocol("The model service URL must use HTTP or HTTPS.")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
            throw ModelRequestException.Protocol("The model service URL contains unsupported components.")
        }
        return url
    }

    private fun shouldSkipFollowupProbes(failure: ModelProbeFailure): Boolean =
        failure.kind == ModelProbeFailureKind.NETWORK ||
            failure.kind == ModelProbeFailureKind.AUTHENTICATION ||
            failure.kind == ModelProbeFailureKind.TIMED_OUT ||
            failure.httpStatusCode == 402 ||
            failure.httpStatusCode == 429

    private fun ModelRequestException.isExplicitUnsupported(target: ModelProbeTarget): Boolean {
        if (this !is ModelRequestException.ApiRejected && this !is ModelRequestException.HttpStatus) return false
        val code = (this as? ModelRequestException.ApiRejected)?.code.orEmpty()
        val details = (code + " " + message.orEmpty()).lowercase(Locale.ROOT)
        val explicitUnsupported = listOf("unsupported", "not_supported", "does not support", "not available", "not found", "not_found")
            .any(details::contains)
        if (!explicitUnsupported) return false
        return when (target) {
            ModelProbeTarget.AVAILABILITY -> details.contains("model")
            ModelProbeTarget.VISION -> listOf("image", "vision", "visual").any(details::contains)
            ModelProbeTarget.NATIVE_STREAMING -> listOf("stream", "sse").any(details::contains)
            ModelProbeTarget.STRUCTURED_TOOL_CALLS -> listOf("tool", "function").any(details::contains)
        }
    }

    private fun ModelRequestException.toProbeFailure(): ModelProbeFailure {
        val status = when (this) {
            is ModelRequestException.HttpStatus -> statusCode
            is ModelRequestException.ApiRejected -> httpStatusCode
            is ModelRequestException.ContextLimit -> httpStatusCode
            else -> null
        }
        val kind = when (this) {
            is ModelRequestException.Network -> ModelProbeFailureKind.NETWORK
            is ModelRequestException.HttpStatus -> if (status == 401 || status == 403) {
                ModelProbeFailureKind.AUTHENTICATION
            } else {
                ModelProbeFailureKind.HTTP
            }
            is ModelRequestException.ApiRejected -> {
                val code = this.code.orEmpty().lowercase(Locale.ROOT)
                if (status == 401 || status == 403 ||
                    listOf("authentication_error", "invalid_api_key", "permission_denied").any(code::contains)
                ) {
                    ModelProbeFailureKind.AUTHENTICATION
                } else if (status != null) {
                    ModelProbeFailureKind.HTTP
                } else {
                    ModelProbeFailureKind.API_REJECTED
                }
            }
            is ModelRequestException.Protocol, is ModelRequestException.StreamInterrupted -> ModelProbeFailureKind.PROTOCOL
            is ModelRequestException.ContextLimit -> ModelProbeFailureKind.API_REJECTED
            is ModelRequestException.TimedOut -> ModelProbeFailureKind.TIMED_OUT
        }
        val retryable = when (this) {
            is ModelRequestException.Network, is ModelRequestException.StreamInterrupted, is ModelRequestException.TimedOut -> true
            is ModelRequestException.HttpStatus -> status == 408 || status == 429 || (status != null && status >= 500)
            is ModelRequestException.ApiRejected -> status == 408 || status == 429 || (status != null && status >= 500) ||
                listOf("rate_limit", "overloaded", "temporarily_unavailable")
                    .any { code.orEmpty().lowercase(Locale.ROOT).contains(it) }
            else -> false
        }
        return ModelProbeFailure(kind = kind, httpStatusCode = status, retryable = retryable)
    }

    private fun validateModelReference(model: ModelReference) {
        if (model.serviceId.isBlank() || model.modelId.isBlank()) {
            throw ModelRequestException.Protocol("The model reference is incomplete.")
        }
    }

    private fun userText(value: String) = ModelMessage(
        role = ModelRole.USER,
        content = listOf(ModelContentPart.Text(value)),
    )

    private companion object {
        const val DEFAULT_PROBE_TIMEOUT_MILLIS = 15_000L
        const val PROBE_TOOL_NAME = "capability_probe"
        const val PROBE_TOOL_VALUE = "elymbot"
        const val PROBE_TOOL_SCHEMA = """{"type":"object","properties":{"probe":{"type":"string"}},"required":["probe"],"additionalProperties":false}"""
        const val VISION_PROMPT = "Name the four block colors in order: top left, top right, bottom left, bottom right. Reply only with four English color names separated by spaces."
        val VISION_EXPECTED_COLORS = listOf("red", "blue", "green", "yellow")
        val COLOR_WORD_REGEX = Regex("[a-z]+")
    }
}

private fun Exception.toModelFailure(duringStream: Boolean): ModelRequestException = when (this) {
    is ModelRequestException -> this
    is CancellationException -> throw this
    is SocketTimeoutException -> ModelRequestException.TimedOut("The model request timed out.", this)
    is IOException -> if (duringStream) {
        ModelRequestException.StreamInterrupted("The model stream was interrupted.", this)
    } else {
        ModelRequestException.Network("The model service could not be reached.", this)
    }
    else -> ModelRequestException.Protocol("The model request could not be completed.", this)
}
