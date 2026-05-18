package com.elymbot.android.feature.qq.runtime

import androidx.appcompat.app.AppCompatDelegate
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.llm.LlmProviderProbePort
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.plugin.domain.PluginWorkspacePathPort
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchPort
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2MessageDispatchResult
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqPlatformConfigPort
import com.elymbot.android.feature.qq.domain.QqPluginExecutionPort
import com.elymbot.android.feature.qq.domain.QqScheduledMessageSender
import com.elymbot.android.feature.qq.domain.QqSendResult
import com.elymbot.android.feature.qq.domain.QqStartupPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

typealias OneBotSendResult = QqSendResult

data class QqOneBotRuntimeDependencies(
    val botPort: BotRepositoryPort,
    val configPort: ConfigRepositoryPort,
    val personaPort: PersonaRepositoryPort,
    val providerPort: ProviderRepositoryPort,
    val conversationPort: QqConversationPort,
    val platformConfigPort: QqPlatformConfigPort,
    val orchestrator: RuntimeLlmOrchestratorPort,
    val runtimeContextResolverPort: RuntimeContextResolverPort,
    val appChatPluginRuntime: AppChatLlmPipelineRuntime,
    val pluginCatalog: () -> List<PluginRuntimePlugin> = { emptyList() },
    val pluginV2DispatchEngine: Any? = null,
    val failureStateStore: Any? = null,
    val scopedFailureStateStore: Any? = null,
    val pluginMessageDispatchPort: PluginV2MessageDispatchPort = PluginV2MessageDispatchPort {
        PluginV2MessageDispatchResult()
    },
    val providerInvoker: QqProviderInvoker,
    val gatewayFactory: PluginHostCapabilityGatewayFactory,
    val hostCapabilityGateway: PluginHostCapabilityGateway,
    val hostActionExecutor: Any? = null,
    val pluginExecutionService: QqPluginExecutionPort,
    val llmProviderProbePort: LlmProviderProbePort,
    val runtimeLogger: RuntimeLogger = RuntimeLogger { },
    val logBus: Any? = null,
    val silkAudioEncoder: (File) -> File,
    val pluginWorkspacePathPort: PluginWorkspacePathPort,
    val filesDirProvider: () -> File? = { null },
    val executeLegacyPluginsDuringLlmDispatch: Boolean = false,
)

interface QqBridgeRuntime : QqStartupPort, QqScheduledMessageSender

internal abstract class BaseQqOneBotBridgeRuntime : QqBridgeRuntime {
    private companion object {
        const val PORT = 6199
        const val PATH = "/ws"
        const val AUTH_TOKEN = "elymbot_android_bridge"
        const val MAX_RECENT_MESSAGE_IDS = 512
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val rateLimiter = QqRateLimiter()
    private val stashReplayGuards = ConcurrentHashMap<String, AtomicBoolean>()
    private val recentMessageIds = object : LinkedHashMap<String, Unit>(MAX_RECENT_MESSAGE_IDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > MAX_RECENT_MESSAGE_IDS
        }
    }

    @Volatile
    private var transport: OneBotReverseWebSocketTransport? = null

    protected abstract fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies

    protected abstract fun runtimeGraphFactory(): QqRuntimeGraphFactory

    protected open fun currentAppChatPluginRuntime(): AppChatLlmPipelineRuntime {
        return requireRuntimeDependencies().appChatPluginRuntime
    }

    protected open fun currentReplySenderOverride():
        ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? = null

    override fun initialize() = Unit

    override fun start() {
        if (started.get()) return
        synchronized(this) {
            if (started.get()) return
            if (ensureTransport().start()) {
                started.set(true)
            }
        }
    }

    override fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment>,
        botId: String,
    ): OneBotSendResult {
        val dependencies = requireRuntimeDependencies()
        return buildRuntimeGraph(dependencies).outboundGateway().sendScheduledMessage(
            conversationId = conversationId,
            text = text,
            attachments = attachments,
            botId = botId,
        )
    }

    internal suspend fun handlePayload(payload: String) {
        val json = runCatching { JSONObject(payload) }
            .getOrElse { error ->
                appendLog(
                    "OneBot payload parse failed: ${error.message ?: error.javaClass.simpleName}",
                )
                return
            }

        if (json.has("retcode") || json.has("status")) {
            val echo = json.opt("echo")?.toString().orEmpty()
            val status = json.optString("status").ifBlank { "unknown" }
            val retcode = json.opt("retcode")?.toString().orEmpty()
            appendLog(
                "OneBot action response: status=$status retcode=${retcode.ifBlank { "-" }} echo=${echo.ifBlank { "-" }}",
            )
            return
        }

        when (val result = buildServerAdapter().handlePayload(payload)) {
            is OneBotServerAdapterResult.Handled -> Unit
            is OneBotServerAdapterResult.Ignored -> appendLog("OneBot payload ignored: ${result.reason}")
            is OneBotServerAdapterResult.Invalid -> appendLog("OneBot payload invalid: ${result.reason}")
        }
    }

    internal fun runtimeGraphForTests(): QqOneBotRuntimeGraph {
        return buildRuntimeGraph(requireRuntimeDependencies())
    }

    private fun buildServerAdapter(): OneBotServerAdapter {
        return buildRuntimeGraph(requireRuntimeDependencies()).adapter()
    }

    private fun buildRuntimeGraph(
        dependencies: QqOneBotRuntimeDependencies,
    ): QqOneBotRuntimeGraph {
        return runtimeGraphFactory().create(
            dependencies = dependencies,
            transport = ensureTransport(),
            appChatPluginRuntime = currentAppChatPluginRuntime(),
            replyOverrideProvider = { currentReplySenderOverride() },
            filesDirProvider = dependencies.filesDirProvider,
            rateLimiter = rateLimiter,
            markMessageId = ::markMessageId,
            scheduleStashReplay = ::scheduleStashReplay,
            currentLanguageTag = ::currentLanguageTag,
            transcribeAudio = { provider, attachment ->
                dependencies.llmProviderProbePort.transcribeAudio(
                    provider = provider.toLlmProviderProfile(),
                    attachment = attachment.toLlmConversationAttachment(),
                )
            },
            resolvePluginPrivateRootPath = ::resolvePluginPrivateRootPath,
            log = ::appendLog,
        )
    }

    private fun ensureTransport(): OneBotReverseWebSocketTransport {
        transport?.let { return it }
        return synchronized(this) {
            transport ?: OneBotReverseWebSocketTransport(
                port = PORT,
                path = PATH,
                authToken = AUTH_TOKEN,
                onPayload = { payload ->
                    scope.launch {
                        handlePayload(payload)
                    }
                },
                log = ::appendLog,
            ).also { transport = it }
        }
    }

    private fun appendLog(message: String) {
        requireRuntimeDependencies().runtimeLogger.append(message)
    }

    private fun scheduleStashReplay(
        bot: BotProfile,
        config: ConfigProfile,
        sourceKey: String,
    ) {
        if (config.rateLimitStrategy != "stash" || config.rateLimitWindowSeconds <= 0) return
        val guard = stashReplayGuards.getOrPut(sourceKey) { AtomicBoolean(false) }
        if (!guard.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    delay(config.rateLimitWindowSeconds * 1000L)
                    val replayEvents = rateLimiter.releaseReady(
                        sourceKey = sourceKey,
                        windowSeconds = config.rateLimitWindowSeconds,
                        maxCount = config.rateLimitMaxCount,
                    )
                    if (replayEvents.isEmpty()) {
                        if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                            return@launch
                        }
                        continue
                    }
                    replayEvents.forEach { payload ->
                        val message = payload as? com.elymbot.android.feature.qq.domain.IncomingQqMessage ?: return@forEach
                        buildRuntimeGraph(requireRuntimeDependencies()).runtimeService().handleIncomingMessage(message)
                    }
                    if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                        return@launch
                    }
                }
            } finally {
                guard.set(false)
                if (rateLimiter.drainReady(sourceKey).isNotEmpty()) {
                    scheduleStashReplay(
                        bot = bot,
                        config = requireRuntimeDependencies().configPort.resolve(bot.configProfileId),
                        sourceKey = sourceKey,
                    )
                }
            }
        }
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        return runCatching {
            requireRuntimeDependencies().pluginWorkspacePathPort.privateRootPath(pluginId)
        }.getOrDefault("")
    }

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
    }

    private fun markMessageId(messageId: String): Boolean {
        synchronized(recentMessageIds) {
            val existed = recentMessageIds.containsKey(messageId)
            recentMessageIds[messageId] = Unit
            return !existed
        }
    }
}

@Singleton
internal class HiltQqOneBotBridgeRuntime @Inject constructor(
    private val runtimeDependencies: QqOneBotRuntimeDependencies,
    private val qqRuntimeGraphFactory: QqRuntimeGraphFactory,
) : BaseQqOneBotBridgeRuntime() {
    override fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies = runtimeDependencies

    override fun runtimeGraphFactory(): QqRuntimeGraphFactory = qqRuntimeGraphFactory
}
