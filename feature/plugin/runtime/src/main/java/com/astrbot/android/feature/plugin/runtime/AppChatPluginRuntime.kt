package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.feature.plugin.runtime.toolsource.FutureToolSourceRegistry
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceIdentity
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceInvokeRequest

class EngineBackedAppChatPluginRuntime(
    private val pluginProvider: () -> List<PluginRuntimePlugin>,
    private val engine: PluginExecutionEngine,
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val activeRuntimeStore: PluginV2ActiveRuntimeStore,
    private val dispatchEngine: PluginV2DispatchEngine,
    private val logBus: PluginRuntimeLogBus,
    private val lifecycleManager: PluginV2LifecycleManager,
    private val futureToolSourceRegistry: FutureToolSourceRegistry,
) : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    // Test-only convenience constructor; production uses the Hilt-backed constructor below.
    internal constructor(
        pluginProvider: () -> List<PluginRuntimePlugin>,
        engine: PluginExecutionEngine,
        hostCapabilityGateway: PluginHostCapabilityGateway,
    ) : this(
        pluginProvider = pluginProvider,
        engine = engine,
        hostCapabilityGateway = hostCapabilityGateway,
        runtimeServices = newInMemoryAppChatRuntimeServices(),
        futureToolSourceRegistry = FutureToolSourceRegistry.empty(),
    )

    private constructor(
        pluginProvider: () -> List<PluginRuntimePlugin>,
        engine: PluginExecutionEngine,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        runtimeServices: InMemoryAppChatRuntimeServices,
        futureToolSourceRegistry: FutureToolSourceRegistry,
    ) : this(
        pluginProvider = pluginProvider,
        engine = engine,
        hostCapabilityGateway = hostCapabilityGateway,
        activeRuntimeStore = runtimeServices.activeRuntimeStore,
        dispatchEngine = runtimeServices.dispatchEngine,
        logBus = runtimeServices.logBus,
        lifecycleManager = runtimeServices.lifecycleManager,
        futureToolSourceRegistry = futureToolSourceRegistry,
    )

    constructor(
        pluginCatalog: ExternalPluginRuntimeCatalog,
        engine: PluginExecutionEngine,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        dispatchEngine: PluginV2DispatchEngine,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        futureToolSourceRegistry: FutureToolSourceRegistry,
    ) : this(
        pluginProvider = pluginCatalog::plugins,
        engine = engine,
        hostCapabilityGateway = hostCapabilityGateway,
        activeRuntimeStore = activeRuntimeStore,
        dispatchEngine = dispatchEngine,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        futureToolSourceRegistry = futureToolSourceRegistry,
    )

    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return engine.executeBatch(
            trigger = trigger,
            plugins = pluginProvider(),
            contextFactory = contextFactory,
        )
    }

    override suspend fun runLlmPipeline(
        input: PluginV2LlmPipelineInput,
    ): PluginV2LlmPipelineResult {
        val configProfileId = input.configProfileId.orEmpty()
        val toolSourceContext = input.toolSourceContext
            ?: futureToolSourceRegistry.contextForConfig(configProfileId)
        val futureDescriptors = futureToolSourceRegistry.collectToolDescriptors(toolSourceContext)
        val activeFutureKinds = futureDescriptors.map { it.sourceKind }.toSet()
        val snapshot = hostCapabilityGateway.requireRuntimeGateway().registerHostBuiltinTools(
            activeRuntimeStore.snapshot(),
            personaSnapshot = input.personaToolEnablementSnapshot,
            futureSourceDescriptors = futureDescriptors,
            activeFutureSourceKinds = activeFutureKinds,
        )
        return createAppChatPluginRuntimeCoordinator(
            hostCapabilityGateway = hostCapabilityGateway,
            dispatchEngine = dispatchEngine,
            logBus = logBus,
            lifecycleManager = lifecycleManager,
            activeRuntimeStore = activeRuntimeStore,
            futureRegistry = futureToolSourceRegistry,
            toolSourceContext = toolSourceContext,
            toolRegistrySnapshot = snapshot.toolRegistrySnapshot,
        ).runPreSendStages(
            input = input,
            snapshot = snapshot,
        )
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        val configProfileId = request.pipelineInput.configProfileId.orEmpty()
        val toolSourceContext = request.pipelineInput.toolSourceContext
            ?: futureToolSourceRegistry.contextForConfig(configProfileId)
        val futureDescriptors = futureToolSourceRegistry.collectToolDescriptors(toolSourceContext)
        val activeFutureKinds = futureDescriptors.map { it.sourceKind }.toSet()
        val snapshot = request.hostCapabilityGateway.requireRuntimeGateway().registerHostBuiltinTools(
            activeRuntimeStore.snapshot(),
            personaSnapshot = request.pipelineInput.personaToolEnablementSnapshot,
            futureSourceDescriptors = futureDescriptors,
            activeFutureSourceKinds = activeFutureKinds,
        )
        return createAppChatPluginRuntimeCoordinator(
            hostCapabilityGateway = request.hostCapabilityGateway.requireRuntimeGateway(),
            dispatchEngine = dispatchEngine,
            logBus = logBus,
            lifecycleManager = lifecycleManager,
            activeRuntimeStore = activeRuntimeStore,
            futureRegistry = futureToolSourceRegistry,
            toolSourceContext = toolSourceContext,
            toolRegistrySnapshot = snapshot.toolRegistrySnapshot,
        ).deliverLlmPipeline(
            request = request,
            snapshot = snapshot,
        )
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        return createAppChatPluginRuntimeCoordinator(
            hostCapabilityGateway = hostCapabilityGateway,
            dispatchEngine = dispatchEngine,
            logBus = logBus,
            lifecycleManager = lifecycleManager,
            activeRuntimeStore = activeRuntimeStore,
        )
            .dispatchAfterMessageSent(
                event = event,
                afterSentView = afterSentView,
            )
    }
}

private fun createAppChatPluginRuntimeCoordinator(
    hostCapabilityGateway: PluginHostCapabilityGateway,
    dispatchEngine: PluginV2DispatchEngine,
    logBus: PluginRuntimeLogBus,
    lifecycleManager: PluginV2LifecycleManager,
    activeRuntimeStore: PluginV2ActiveRuntimeStore,
    futureRegistry: FutureToolSourceRegistry? = null,
    toolSourceContext: com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceContext? = null,
    toolRegistrySnapshot: PluginV2ToolRegistrySnapshot? = null,
): PluginV2LlmPipelineCoordinator {
    return PluginV2LlmPipelineCoordinator(
        dispatchEngine = dispatchEngine,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        snapshotProvider = activeRuntimeStore::snapshot,
        toolExecutor = PluginV2ToolExecutor { args ->
            // 1. Try host builtin tools first
            val hostResult = hostCapabilityGateway.requireRuntimeGateway().executeHostBuiltinTool(args)
            if (hostResult != null) return@PluginV2ToolExecutor hostResult

            // 2. Try future source tools (ACTIVE_CAPABILITY, MCP, etc.)
            if (futureRegistry != null) {
                val entry = toolRegistrySnapshot?.activeEntriesByToolId?.get(args.toolId)
                if (entry != null) {
                    val invokeResult = futureRegistry.invoke(
                        ToolSourceInvokeRequest(
                            identity = ToolSourceIdentity(
                                sourceKind = entry.sourceKind,
                                ownerId = entry.pluginId,
                                sourceRef = entry.name,
                                displayName = entry.name,
                            ),
                            args = args,
                            timeoutMs = 60_000L,
                            configProfileId = extractConfigProfileId(args),
                            toolSourceContext = toolSourceContext,
                        ),
                    )
                    if (invokeResult != null) return@PluginV2ToolExecutor invokeResult.result
                }
            }

            // 3. Fallback error
            PluginToolResult(
                toolCallId = args.toolCallId,
                requestId = args.requestId,
                toolId = args.toolId,
                status = PluginToolResultStatus.ERROR,
                errorCode = "tool_executor_unavailable",
                text = "Tool executor is not wired yet.",
            )
        },
    )
}

private fun extractConfigProfileId(args: PluginToolArgs): String? {
    val host = args.metadata?.get("__host") as? Map<*, *> ?: return null
    val eventExtras = host["eventExtras"] as? Map<*, *> ?: return null
    return (eventExtras["configProfileId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
}

private data class InMemoryAppChatRuntimeServices(
    val activeRuntimeStore: PluginV2ActiveRuntimeStore,
    val dispatchEngine: PluginV2DispatchEngine,
    val logBus: PluginRuntimeLogBus,
    val lifecycleManager: PluginV2LifecycleManager,
)

private fun newInMemoryAppChatRuntimeServices(): InMemoryAppChatRuntimeServices {
    val logBus = InMemoryPluginRuntimeLogBus()
    val activeRuntimeStore = PluginV2ActiveRuntimeStore(logBus = logBus)
    val lifecycleManager = PluginV2LifecycleManager(
        logBus = logBus,
        store = activeRuntimeStore,
    )
    return InMemoryAppChatRuntimeServices(
        activeRuntimeStore = activeRuntimeStore,
        dispatchEngine = PluginV2DispatchEngine(
            logBus = logBus,
            store = activeRuntimeStore,
            lifecycleManager = lifecycleManager,
        ),
        logBus = logBus,
        lifecycleManager = lifecycleManager,
    )
}
