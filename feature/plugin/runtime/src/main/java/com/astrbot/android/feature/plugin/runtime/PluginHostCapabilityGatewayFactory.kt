package com.astrbot.android.feature.plugin.runtime

/** Factory that creates contextual [PluginHostCapabilityGateway] instances from Hilt-owned parts. */
class PluginHostCapabilityGatewayFactory(
    private val resolver: PluginExecutionHostResolver,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
) : com.astrbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory {
    override fun create(
        sendMessageHandler: (String) -> Unit,
        sendNotificationHandler: (String, String) -> Unit,
        openHostPageHandler: (String) -> Unit,
    ): PluginHostCapabilityGateway = createWithHostToolHandlers(
        sendMessageHandler = sendMessageHandler,
        sendNotificationHandler = sendNotificationHandler,
        openHostPageHandler = openHostPageHandler,
        hostToolHandlers = PluginExecutionHostToolHandlers(),
    )

    fun create(): PluginHostCapabilityGateway = create(
        sendMessageHandler = {},
        sendNotificationHandler = { _, _ -> },
        openHostPageHandler = {},
    )

    fun createWithHostToolHandlers(
        sendMessageHandler: (String) -> Unit = {},
        sendNotificationHandler: (String, String) -> Unit = { _, _ -> },
        openHostPageHandler: (String) -> Unit = {},
        hostToolHandlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
    ): PluginHostCapabilityGateway = DefaultPluginHostCapabilityGateway(
        resolver = resolver,
        hostActionExecutor = hostActionExecutor,
        hostActionHandlers = ExternalPluginHostActionHandlers(
            sendMessage = sendMessageHandler,
            sendNotification = sendNotificationHandler,
            openHostPage = openHostPageHandler,
        ),
        hostToolHandlers = hostToolHandlers,
    )
}
